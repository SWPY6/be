package com.swyp.ploutos.stock.quote.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisQuoteCacheTest {

    private static final int REDIS_PORT = 6379;
    private static final int CLOSED_PORT = 6390;
    private static final long TTL_SECONDS = 1;
    private static final long ACTIVE_WINDOW_SECONDS = 60;
    private static final Instant NOW = Instant.parse("2026-08-12T05:31:05Z");
    private static final OffsetDateTime PRICE_AT =
            OffsetDateTime.of(2026, 8, 12, 14, 31, 5, 0, ZoneOffset.ofHours(9));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private StringRedisTemplate redisTemplate;
    private RedisQuoteCache cache;

    @BeforeEach
    void setUp() {
        redisTemplate = template(redis.getHost(), redis.getMappedPort(REDIS_PORT));
        redisTemplate.delete(List.of("quote:active", "quote:refresh:lock"));
        cache = cacheAt(redisTemplate, NOW);
    }

    @Test
    void 저장한_시세를_같은_값으로_읽는다() {
        // given
        Quote quote = quote();

        // when
        cache.put(1L, quote);

        // then
        assertThat(cache.find(1L)).contains(quote);
    }

    @Test
    void TTL이_지나면_값이_사라진다() throws InterruptedException {
        // given
        cache.put(2L, quote());

        // when
        Thread.sleep(TTL_SECONDS * 1_000 + 200);

        // then
        assertThat(cache.find(2L)).isEmpty();
    }

    @Test
    void 같은_종목의_락은_한_요청만_잡는다() {
        // given
        boolean first = cache.tryLock(3L);

        // when
        boolean second = cache.tryLock(3L);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void 락은_3초_뒤_자동_해제된다() {
        // given
        cache.tryLock(4L);

        // when
        Long remaining = redisTemplate.getExpire("quote:lock:4", TimeUnit.SECONDS);

        // then 해제를 놓쳐도 3초 뒤에는 풀린다
        assertThat(remaining).isBetween(1L, 3L);
    }

    @Test
    void 락을_해제하면_다시_잡을_수_있다() {
        // given
        cache.tryLock(5L);

        // when
        cache.unlock(5L);

        // then
        assertThat(cache.tryLock(5L)).isTrue();
    }

    @Test
    void Redis에_접근할_수_없으면_예외를_던진다() {
        // given 열려 있지 않은 포트를 가리키는 캐시
        RedisQuoteCache broken = cacheAt(template("localhost", CLOSED_PORT), NOW);

        // when & then 캐시 없이 KIS를 부르지 않도록 시세 조회 실패로 알린다
        assertThatThrownBy(() -> broken.find(6L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThatThrownBy(() -> broken.tryLock(6L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> broken.put(6L, quote())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> broken.markActive(6L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(broken::activeStockIds).isInstanceOf(BusinessException.class);
        assertThatThrownBy(broken::tryRefreshLeadership).isInstanceOf(BusinessException.class);
    }

    @Test
    void Redis에_접근할_수_없어도_락_해제는_예외를_던지지_않는다() {
        // given
        RedisQuoteCache broken = cacheAt(template("localhost", CLOSED_PORT), NOW);

        // when & then 락은 TTL로 풀리므로 호출자의 결과를 바꾸지 않는다
        assertThatCode(() -> broken.unlock(6L)).doesNotThrowAnyException();
    }

    @Test
    void 조회한_종목은_활성_목록에_담긴다() {
        // given
        cache.markActive(8L);
        cache.markActive(9L);

        // when
        List<Long> active = cache.activeStockIds();

        // then
        assertThat(active).containsExactlyInAnyOrder(8L, 9L);
    }

    @Test
    void 조회_창이_지난_종목은_활성_목록에서_빠진다() {
        // given 8번은 창을 막 벗어났고 9번은 창 안에 있다
        cacheAt(redisTemplate, NOW.minusSeconds(ACTIVE_WINDOW_SECONDS + 1)).markActive(8L);
        cacheAt(redisTemplate, NOW.minusSeconds(ACTIVE_WINDOW_SECONDS - 1)).markActive(9L);

        // when
        List<Long> active = cache.activeStockIds();

        // then
        assertThat(active).containsExactly(9L);
        assertThat(redisTemplate.opsForZSet().score("quote:active", "8")).isNull();
    }

    @Test
    void 갱신_리더_락은_한_번만_잡힌다() {
        // given
        boolean first = cache.tryRefreshLeadership();

        // when
        boolean second = cache.tryRefreshLeadership();

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(redisTemplate.getExpire("quote:refresh:lock", TimeUnit.SECONDS)).isBetween(1L, 9L);
    }

    @Test
    void 저장된_값을_읽을_수_없으면_캐시_없음으로_동작한다() {
        // given 배포로 Quote 형식이 바뀌면 옛 값이 남아 있을 수 있다
        redisTemplate.opsForValue().set("quote:7", "{\"price\":");

        // when
        Optional<Quote> found = cache.find(7L);

        // then
        assertThat(found).isEmpty();
    }

    private static RedisQuoteCache cacheAt(StringRedisTemplate template, Instant now) {
        return new RedisQuoteCache(
                template,
                JsonMapper.builder().build(),
                new QuoteCacheProperties(TTL_SECONDS, ACTIVE_WINDOW_SECONDS),
                Clock.fixed(now, ZoneOffset.UTC)
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

    private static Quote quote() {
        return new Quote(
                new BigDecimal("248000"),
                new BigDecimal("240217"),
                new BigDecimal("244280"),
                new BigDecimal("251224"),
                new BigDecimal("241056"),
                245_000,
                new BigDecimal("60800000000"),
                new BigDecimal("86600000000000"),
                Currency.KRW,
                PRICE_AT,
                PriceTiming.REALTIME
        );
    }
}
