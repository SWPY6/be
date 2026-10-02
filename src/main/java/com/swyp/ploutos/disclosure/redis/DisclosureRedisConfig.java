package com.swyp.ploutos.disclosure.redis;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.swyp.ploutos.disclosure.service.IssuerCodeProvider;
import com.swyp.ploutos.disclosure.service.IssuerCodes;

/**
 * {@link DisclosureRedisProperties}는 스캔 대상이 아니므로 여기서 등록한다.
 * 법인 매핑은 공급자마다 하나씩 만든다. 매개변수 이름이 공급자 빈 이름과 같아야 한다.
 */
@Configuration
@EnableConfigurationProperties(DisclosureRedisProperties.class)
class DisclosureRedisConfig {

    @Bean
    IssuerCodes dartIssuerCodes(
            StringRedisTemplate redisTemplate, IssuerCodeProvider dartCorpCodeProvider,
            DisclosureRedisProperties properties, Clock clock
    ) {
        return new RedisIssuerCodes(redisTemplate, dartCorpCodeProvider, properties, clock);
    }

    @Bean
    IssuerCodes secIssuerCodes(
            StringRedisTemplate redisTemplate, IssuerCodeProvider secCikProvider,
            DisclosureRedisProperties properties, Clock clock
    ) {
        return new RedisIssuerCodes(redisTemplate, secCikProvider, properties, clock);
    }
}
