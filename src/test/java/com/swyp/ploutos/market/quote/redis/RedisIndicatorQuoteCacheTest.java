package com.swyp.ploutos.market.quote.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisIndicatorQuoteCacheTest {

    private static final int REDIS_PORT = 6379;
    private static final int CLOSED_PORT = 6390;
    private static final long TTL_SECONDS = 1;
    // 서울 2026-09-30 10:15:03
    private static final OffsetDateTime VALUE_AT = OffsetDateTime.parse("2026-09-30T10:15:03+09:00");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private StringRedisTemplate redisTemplate;
    private RedisIndicatorQuoteCache cache;

    @BeforeEach
    void setUp() {
        redisTemplate = template(redis.getHost(), redis.getMappedPort(REDIS_PORT));
        // 테스트가 남긴 값·락이 다음 테스트로 넘어가면 실행 순서에 따라 결과가 달라진다
        redisTemplate.delete(Arrays.stream(MarketIndicator.values())
                .flatMap(indicator -> Stream.of(
                        "market-quote:" + indicator.name(),
                        "market-quote:lock:" + indicator.name()))
                .toList());
        cache = cacheOf(redisTemplate);
    }

    @Test
    void 저장한_시세를_같은_값과_오프셋으로_읽는다() {
        // given 환율은 소수 넷째 자리까지 오고, 기준 시각은 지표 타임존 오프셋을 가진다
        IndicatorQuote quote = exchangeRate();

        // when
        cache.put(MarketIndicator.USD_KRW, quote);
        Optional<IndicatorQuote> found = cache.find(MarketIndicator.USD_KRW);

        // then
        assertThat(found).contains(quote);
        assertThat(found.orElseThrow().valueAt()).isEqualTo(VALUE_AT);
        assertThat(found.orElseThrow().value()).isEqualTo(new BigDecimal("1354.0000"));
    }

    @Test
    void 지표마다_다른_키에_저장된다() {
        // given
        cache.put(MarketIndicator.KOSPI, quote(MarketIndicator.KOSPI, "6870.81"));

        // when
        cache.put(MarketIndicator.KOSDAQ, quote(MarketIndicator.KOSDAQ, "849.80"));

        // then
        assertThat(cache.find(MarketIndicator.KOSPI).orElseThrow().value()).isEqualByComparingTo("6870.81");
        assertThat(cache.find(MarketIndicator.KOSDAQ).orElseThrow().value()).isEqualByComparingTo("849.80");
    }

    @Test
    void TTL이_지나면_값이_사라진다() throws InterruptedException {
        // given
        cache.put(MarketIndicator.NASDAQ, quote(MarketIndicator.NASDAQ, "26817.30"));

        // when
        Thread.sleep(TTL_SECONDS * 1_000 + 200);

        // then
        assertThat(cache.find(MarketIndicator.NASDAQ)).isEmpty();
    }

    @Test
    void 같은_지표의_락은_한_요청만_잡는다() {
        // given
        boolean first = cache.tryLock(MarketIndicator.SP500);

        // when
        boolean second = cache.tryLock(MarketIndicator.SP500);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        // 해제를 놓쳐도 3초 뒤에는 풀린다
        assertThat(redisTemplate.getExpire("market-quote:lock:SP500", TimeUnit.SECONDS)).isBetween(1L, 3L);
    }

    @Test
    void 락을_해제하면_다시_잡을_수_있다() {
        // given
        cache.tryLock(MarketIndicator.KOSPI);

        // when
        cache.unlock(MarketIndicator.KOSPI);

        // then
        assertThat(cache.tryLock(MarketIndicator.KOSPI)).isTrue();
    }

    @Test
    void Redis에_접근할_수_없으면_예외를_던진다() {
        // given 열려 있지 않은 포트를 가리키는 캐시
        RedisIndicatorQuoteCache broken = cacheOf(template("localhost", CLOSED_PORT));

        // when & then 캐시 없이 KIS를 부르지 않도록 시세 조회 실패로 알린다
        assertThatThrownBy(() -> broken.find(MarketIndicator.KOSPI))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThatThrownBy(() -> broken.put(MarketIndicator.KOSPI, quote(MarketIndicator.KOSPI, "1")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> broken.tryLock(MarketIndicator.KOSPI)).isInstanceOf(BusinessException.class);
    }

    @Test
    void Redis에_접근할_수_없어도_락_해제는_예외를_던지지_않는다() {
        // given
        RedisIndicatorQuoteCache broken = cacheOf(template("localhost", CLOSED_PORT));

        // when & then 락은 TTL로 풀리므로 호출자의 결과를 바꾸지 않는다
        assertThatCode(() -> broken.unlock(MarketIndicator.KOSPI)).doesNotThrowAnyException();
    }

    @Test
    void 저장된_값을_읽을_수_없으면_캐시_없음으로_동작한다() {
        // given 배포로 형식이 바뀌면 옛 값이 남아 있을 수 있다
        redisTemplate.opsForValue().set("market-quote:KOSDAQ", "{\"value\":");

        // when
        Optional<IndicatorQuote> found = cache.find(MarketIndicator.KOSDAQ);

        // then
        assertThat(found).isEmpty();
    }

    private static RedisIndicatorQuoteCache cacheOf(StringRedisTemplate template) {
        return new RedisIndicatorQuoteCache(
                template,
                JsonMapper.builder().build(),
                new IndicatorQuoteCacheProperties(TTL_SECONDS)
        );
    }

    private static StringRedisTemplate template(String host, int port) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(1))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private static IndicatorQuote exchangeRate() {
        return new IndicatorQuote(
                MarketIndicator.USD_KRW,
                new BigDecimal("1354.0000"),
                new BigDecimal("1359.9000"),
                new BigDecimal("1357.0000"),
                new BigDecimal("1361.7000"),
                new BigDecimal("1353.1000"),
                VALUE_AT
        );
    }

    private static IndicatorQuote quote(MarketIndicator indicator, String value) {
        BigDecimal amount = new BigDecimal(value);
        return new IndicatorQuote(indicator, amount, amount, amount, amount, amount, VALUE_AT);
    }
}
