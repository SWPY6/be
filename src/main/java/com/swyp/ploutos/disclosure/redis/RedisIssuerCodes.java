package com.swyp.ploutos.disclosure.redis;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;
import com.swyp.ploutos.disclosure.service.IssuerCodes;

/**
 * 거래소·티커 → 공급자 법인 ID 매핑을 공급자별 Redis 해시에 두고 주기적으로 다시 받는다.
 * 갱신 표시가 만료되면 조회 요청이 갱신하며, 인스턴스 간 잠금(토큰으로 소유자 확인)으로 한 곳만 받는다.
 * 새 매핑은 임시 키에 다 쓴 뒤 바꿔 끼우므로 읽는 쪽은 반쯤 쓴 매핑을 보지 않는다.
 * 갱신에 실패하면 기존 매핑을 그대로 쓰고, 잠금이 풀릴 때까지 다시 시도하지 않는다.
 * 공급자마다 하나씩 {@link DisclosureRedisConfig}에서 만든다.
 */
class RedisIssuerCodes implements IssuerCodes {

    private static final Logger log = LoggerFactory.getLogger(RedisIssuerCodes.class);

    // 파일 수신(응답 타임아웃 10초, 재시도 1회)보다 길게 잡는다. 실패 후 재시도 간격도 이 값이다.
    static final Duration LOCK_TTL = Duration.ofSeconds(60);

    private static final RedisScript<Long> RELEASE_LOCK = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final IssuerCodeProvider provider;
    private final DisclosureRedisProperties properties;
    private final Clock clock;
    private final String codesKey;

    RedisIssuerCodes(
            StringRedisTemplate redisTemplate, IssuerCodeProvider provider,
            DisclosureRedisProperties properties, Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.provider = provider;
        this.properties = properties;
        this.clock = clock;
        this.codesKey = codesKey(provider.source());
    }

    static String codesKey(DisclosureSource source) {
        return "disclosure:" + source.name().toLowerCase(Locale.ROOT) + ":issuer-codes:v2";
    }

    @Override
    public DisclosureSource source() {
        return provider.source();
    }

    @Override
    public Optional<String> issuerIdOf(Exchange exchange, String ticker) {
        try {
            refreshIfStale();
            HashOperations<String, String, String> codes = redisTemplate.opsForHash();
            return Optional.ofNullable(codes.get(codesKey, IssuerCodes.key(exchange, ticker)));
        } catch (DataAccessException e) {
            log.error("공시 법인 매핑 저장소에 접근하지 못했습니다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
    }

    private void refreshIfStale() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(codesKey + ":refreshed"))) {
            return;
        }
        boolean hasCodes = Boolean.TRUE.equals(redisTemplate.hasKey(codesKey));
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(codesKey + ":lock", token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            if (hasCodes) {
                return;
            }
            log.warn("{} 법인 매핑이 아직 없고 다른 요청이 받는 중이거나 직전에 실패했다.", source());
            throw new BusinessException(ErrorCode.DISCLOSURE_QUOTA_EXCEEDED);
        }
        refresh(hasCodes, token);
    }

    /**
     * 실패하면 잠금을 남겨 둬 잠금이 만료될 때까지 다른 요청이 같은 실패를 반복하지 않게 한다.
     * 받는 사이 잠금이 만료돼 다른 인스턴스가 잡을 수 있으므로, 임시 키는 잠금 토큰별로 따로 쓰고
     * 잠금은 내 토큰일 때만 푼다.
     */
    private void refresh(boolean hasCodes, String token) {
        Map<String, String> codes;
        try {
            codes = provider.fetchAll();
        } catch (BusinessException e) {
            if (!hasCodes) {
                throw e;
            }
            log.warn("{} 법인 매핑 갱신에 실패해 기존 매핑을 그대로 쓴다. code={}", source(), e.errorCode().code());
            return;
        }
        String stagingKey = codesKey + ":staging:" + token;
        redisTemplate.opsForHash().putAll(stagingKey, codes);
        redisTemplate.rename(stagingKey, codesKey);
        redisTemplate.opsForValue().set(
                codesKey + ":refreshed", clock.instant().toString(), properties.issuerCodeRefreshInterval()
        );
        redisTemplate.execute(RELEASE_LOCK, List.of(codesKey + ":lock"), token);
        log.info("{} 법인 매핑을 갱신했다. count={}", source(), codes.size());
    }
}
