package com.swyp.ploutos.external.naver;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(NaverApiProperties.class)
@RequiredArgsConstructor
class NaverClientConfig {

    static final String CLIENT_ID_HEADER = "X-NCP-APIGW-API-KEY-ID";
    static final String CLIENT_SECRET_HEADER = "X-NCP-APIGW-API-KEY";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final NaverApiProperties properties;

    @Bean
    RestClient naverRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        );
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(CLIENT_ID_HEADER, properties.clientId())
                .defaultHeader(CLIENT_SECRET_HEADER, properties.clientSecret())
                .requestFactory(requestFactory)
                .build();
    }
}
