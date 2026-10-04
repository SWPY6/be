package com.swyp.ploutos.external.redis;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 상한과 만료가 있는 Redis 카운터. 확인·증가·만료 설정을 스크립트 하나로 묶어, 중간에 실패해도 만료 없는 키가 남지 않는다.
 * 이미 만료 없이 남은 키도 다음 호출 때 만료를 건다. 상한에 걸린 호출은 세지 않으므로,
 * 같은 키를 다른 상한으로 나눠 쓰면 낮은 상한에서 거절된 호출이 높은 상한의 남은 몫을 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RedisCounter {

    // 올렸으면 올린 값을, 상한에 걸렸으면 0을 돌려준다.
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            local allowed = count < tonumber(ARGV[1])
            if allowed then
                count = redis.call('INCR', KEYS[1])
            end
            if redis.call('TTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if allowed then
                return count
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 현재 값이 {@code limit} 미만이면 1 올리고 올린 값을 돌려준다. 상한에 걸리면 올리지 않고 비어 있다.
     * 저장소에 접근하지 못하면 {@code DataAccessException}을 던진다.
     */
    public OptionalLong increment(String key, long limit, Duration ttl) {
        Long count = redisTemplate.execute(
                INCREMENT, List.of(key), String.valueOf(limit), String.valueOf(ttl.toSeconds())
        );
        if (count == null || count == 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(count);
    }
}
