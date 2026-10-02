package com.swyp.ploutos.external.sec;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

/** 호스트가 둘(data·www)이라 기본 주소 없이 만들고, 호출하는 쪽이 {@link SecApiProperties}로 절대 주소를 만든다. */
@Configuration
@EnableConfigurationProperties(SecApiProperties.class)
@RequiredArgsConstructor
class SecClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // 티커 파일(수백 KB)과 큰 법인의 submissions(수백 KB)를 받는다.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final SecApiProperties properties;

    @Bean
    RestClient secRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        );
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .requestFactory(requestFactory)
                .build();
    }
}
