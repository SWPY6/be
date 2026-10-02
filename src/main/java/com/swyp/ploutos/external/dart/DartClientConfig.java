package com.swyp.ploutos.external.dart;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(DartApiProperties.class)
@RequiredArgsConstructor
class DartClientConfig {

    static final String API_KEY_PARAM = "crtfc_key";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // 법인 코드 파일(수 MB ZIP)도 같은 클라이언트로 받으므로 목록 조회보다 넉넉히 둔다.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final DartApiProperties properties;

    @Bean
    RestClient dartRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        );
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(apiKeyInterceptor(properties.apiKey()))
                .requestFactory(requestFactory)
                .build();
    }

    /** 호출하는 쪽이 인증키를 몰라도 되게 모든 요청에 crtfc_key를 붙인다. */
    private static ClientHttpRequestInterceptor apiKeyInterceptor(String apiKey) {
        return (request, body, execution) -> {
            URI withKey = UriComponentsBuilder.fromUri(request.getURI())
                    .queryParam(API_KEY_PARAM, apiKey)
                    .build(true)
                    .toUri();
            HttpRequest keyed = new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    return withKey;
                }
            };
            return execution.execute(keyed, body);
        };
    }
}
