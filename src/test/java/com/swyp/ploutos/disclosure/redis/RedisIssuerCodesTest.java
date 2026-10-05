package com.swyp.ploutos.disclosure.redis;

import static com.swyp.ploutos.disclosure.redis.RedisTemplates.CLOSED_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.REDIS_PORT;
import static com.swyp.ploutos.disclosure.redis.RedisTemplates.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;

@Testcontainers
class RedisIssuerCodesTest {

    private static final Instant NOW = Instant.parse("2026-10-02T05:00:00Z");
    private static final Map<String, String> CODES = Map.of("KRX:005930", "00126380", "KRX:000660", "00164779");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    private StringRedisTemplate redisTemplate;
    private FakeProvider provider;
    private RedisIssuerCodes issuerCodes;

    @BeforeEach
    void setUp() {
        redisTemplate = template(redis.getHost(), redis.getMappedPort(REDIS_PORT));
        for (DisclosureSource source : DisclosureSource.values()) {
            String key = RedisIssuerCodes.codesKey(source);
            redisTemplate.delete(List.of(key, key + ":refreshed", key + ":lock"));
        }
        provider = new FakeProvider(DisclosureSource.DART);
        issuerCodes = issuerCodesOn(redisTemplate, provider);
    }

    @Test
    void 매핑이_없으면_받아서_저장하고_거래소와_티커로_찾는다() {
        // given
        provider.willReturn(CODES);

        // when
        Optional<String> corpCode = issuerCodes.issuerIdOf(Exchange.KRX, "005930");

        // then
        assertThat(corpCode).contains("00126380");
        assertThat(redisTemplate.opsForHash().entries(RedisIssuerCodes.codesKey(DisclosureSource.DART))).hasSize(2);
        assertThat(redisTemplate.hasKey(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":lock")).isFalse();
    }

    @Test
    void 티커는_대문자와_SEC_클래스_표기로_맞춰_찾는다() {
        // given
        FakeProvider sec = new FakeProvider(DisclosureSource.SEC);
        sec.willReturn(Map.of("NYSE:BRK-B", "0001067983"));
        RedisIssuerCodes secCodes = issuerCodesOn(redisTemplate, sec);

        // when & then
        assertThat(secCodes.issuerIdOf(Exchange.NYSE, "brk.b")).contains("0001067983");
        assertThat(secCodes.issuerIdOf(Exchange.NASDAQ, "BRK-B")).isEmpty();
    }

    @Test
    void 공급자마다_다른_키에_저장한다() {
        // given
        provider.willReturn(CODES);
        FakeProvider sec = new FakeProvider(DisclosureSource.SEC);
        sec.willReturn(Map.of("NASDAQ:AAPL", "0000320193"));

        // when
        issuerCodes.issuerIdOf(Exchange.KRX, "005930");
        issuerCodesOn(redisTemplate, sec).issuerIdOf(Exchange.NASDAQ, "AAPL");

        // then
        assertThat(redisTemplate.opsForHash().entries("disclosure:dart:issuer-codes:v2")).hasSize(2);
        assertThat(redisTemplate.opsForHash().entries("disclosure:sec:issuer-codes:v2")).hasSize(1);
    }

    @Test
    void 매핑에_없는_종목은_비어_있다() {
        // given
        provider.willReturn(CODES);

        // when
        Optional<String> corpCode = issuerCodes.issuerIdOf(Exchange.KRX, "069500");

        // then
        assertThat(corpCode).isEmpty();
    }

    @Test
    void 갱신_주기_안에는_다시_받지_않는다() {
        // given
        provider.willReturn(CODES);
        issuerCodes.issuerIdOf(Exchange.KRX, "005930");

        // when
        issuerCodes.issuerIdOf(Exchange.KRX, "000660");

        // then
        assertThat(provider.calls).isEqualTo(1);
        Long ttl = redisTemplate.getExpire(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":refreshed", TimeUnit.HOURS);
        assertThat(ttl).isBetween(23L, 24L);
    }

    @Test
    void 갱신_주기가_지나면_새_매핑으로_통째로_바꾼다() {
        // given
        provider.willReturn(CODES);
        issuerCodes.issuerIdOf(Exchange.KRX, "005930");
        redisTemplate.delete(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":refreshed");
        provider.willReturn(Map.of("KRX:005930", "00126380"));

        // when
        Optional<String> removed = issuerCodes.issuerIdOf(Exchange.KRX, "000660");

        // then
        assertThat(removed).isEmpty();
        assertThat(provider.calls).isEqualTo(2);
        assertThat(redisTemplate.hasKey(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":staging")).isFalse();
    }

    @Test
    void 갱신에_실패해도_기존_매핑이_있으면_그대로_쓰고_잠금이_풀릴_때까지_다시_받지_않는다() {
        // given
        provider.willReturn(CODES);
        issuerCodes.issuerIdOf(Exchange.KRX, "005930");
        redisTemplate.delete(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":refreshed");
        provider.willThrow(ErrorCode.DISCLOSURE_UNAVAILABLE);

        // when
        Optional<String> first = issuerCodes.issuerIdOf(Exchange.KRX, "005930");
        Optional<String> second = issuerCodes.issuerIdOf(Exchange.KRX, "000660");

        // then
        assertThat(first).contains("00126380");
        assertThat(second).contains("00164779");
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void 처음_받다가_실패하면_공급자_오류를_그대로_던진다() {
        // given
        provider.willThrow(ErrorCode.DISCLOSURE_UNAVAILABLE);

        // when & then
        assertError(() -> issuerCodes.issuerIdOf(Exchange.KRX, "005930"), ErrorCode.DISCLOSURE_UNAVAILABLE);
    }

    @Test
    void 매핑이_없는데_다른_요청이_받는_중이면_일시_제한이다() {
        // given
        redisTemplate.opsForValue().set(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":lock", "other");
        provider.willReturn(CODES);

        // when & then
        assertError(() -> issuerCodes.issuerIdOf(Exchange.KRX, "005930"), ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        assertThat(provider.calls).isZero();
    }

    @Test
    void 다른_요청이_받는_중이어도_기존_매핑이_있으면_그것을_쓴다() {
        // given
        provider.willReturn(CODES);
        issuerCodes.issuerIdOf(Exchange.KRX, "005930");
        redisTemplate.delete(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":refreshed");
        redisTemplate.opsForValue().set(RedisIssuerCodes.codesKey(DisclosureSource.DART) + ":lock", "other");

        // when
        Optional<String> corpCode = issuerCodes.issuerIdOf(Exchange.KRX, "005930");

        // then
        assertThat(corpCode).contains("00126380");
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void 받는_사이_잠금이_만료돼_다른_인스턴스가_잡았으면_그_잠금과_임시_키를_건드리지_않는다() {
        // given 파일을 받는 동안 잠금이 만료되고 다른 인스턴스가 잠금을 잡고 자기 임시 키에 쓰는 중이다
        String codesKey = RedisIssuerCodes.codesKey(DisclosureSource.DART);
        String otherStaging = codesKey + ":staging:other";
        provider.response = () -> {
            redisTemplate.opsForValue().set(codesKey + ":lock", "other");
            redisTemplate.opsForHash().put(otherStaging, "KRX:999999", "09999999");
            return CODES;
        };

        // when
        Optional<String> corpCode = issuerCodes.issuerIdOf(Exchange.KRX, "005930");

        // then
        assertThat(corpCode).contains("00126380");
        assertThat(redisTemplate.opsForValue().get(codesKey + ":lock")).isEqualTo("other");
        assertThat(redisTemplate.opsForHash().entries(otherStaging)).containsOnlyKeys("KRX:999999");
        assertThat(redisTemplate.opsForHash().entries(codesKey)).isEqualTo(CODES);
        redisTemplate.delete(otherStaging);
    }

    @Test
    void Redis에_접근하지_못하면_일시_제한이고_공급자를_부르지_않는다() {
        // given
        RedisIssuerCodes broken = issuerCodesOn(template("localhost", CLOSED_PORT), provider);
        provider.willReturn(CODES);

        // when & then
        assertError(() -> broken.issuerIdOf(Exchange.KRX, "005930"), ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        assertThat(provider.calls).isZero();
    }

    private static RedisIssuerCodes issuerCodesOn(StringRedisTemplate template, IssuerCodeProvider provider) {
        return new RedisIssuerCodes(
                template,
                provider,
                new DisclosureRedisProperties(600, 16_000, 15_000, 5, 24),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(expected);
    }

    private static final class FakeProvider implements IssuerCodeProvider {

        private final DisclosureSource source;
        private Supplier<Map<String, String>> response;
        int calls;

        FakeProvider(DisclosureSource source) {
            this.source = source;
        }

        void willReturn(Map<String, String> codes) {
            response = () -> codes;
        }

        void willThrow(ErrorCode errorCode) {
            response = () -> {
                throw new BusinessException(errorCode);
            };
        }

        @Override
        public DisclosureSource source() {
            return source;
        }

        @Override
        public Map<String, String> fetchAll() {
            calls++;
            return response.get();
        }
    }
}
