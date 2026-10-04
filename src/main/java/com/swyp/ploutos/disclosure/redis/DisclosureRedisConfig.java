package com.swyp.ploutos.disclosure.redis;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.swyp.ploutos.disclosure.service.DisclosureCallBudget;
import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;
import com.swyp.ploutos.disclosure.service.IssuerCodes;
import com.swyp.ploutos.external.redis.RedisCounter;

/**
 * {@link DisclosureRedisProperties}는 스캔 대상이 아니므로 여기서 등록한다.
 * DART 호출 예산은 상한마다, 법인 매핑은 공급자마다 하나씩 만든다. 매개변수 이름이 공급자 빈 이름과 같아야 한다.
 */
@Configuration
@EnableConfigurationProperties(DisclosureRedisProperties.class)
class DisclosureRedisConfig {

    /** 고유번호 파일이 쓰는 DART 전체 상한. */
    @Bean
    DisclosureCallBudget dartCallBudget(RedisCounter redisCounter, DisclosureRedisProperties properties, Clock clock) {
        return new RedisDartCallBudget(redisCounter, properties.dailyCallLimit(), clock);
    }

    /** 공시검색 상한. 전체 상한과 같은 카운터를 쓰되 더 낮은 값에서 멈춘다. */
    @Bean
    DisclosureCallBudget dartSearchCallBudget(
            RedisCounter redisCounter, DisclosureRedisProperties properties, Clock clock
    ) {
        return new RedisDartCallBudget(redisCounter, properties.dailySearchCallLimit(), clock);
    }

    @Bean
    IssuerCodes dartIssuerCodes(
            StringRedisTemplate redisTemplate, IssuerCodeProvider dartCorpCodeProvider,
            DisclosureRedisProperties properties, Clock clock
    ) {
        return new RedisIssuerCodes(redisTemplate, dartCorpCodeProvider, properties, clock);
    }
}
