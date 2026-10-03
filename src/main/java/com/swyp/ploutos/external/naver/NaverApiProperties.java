package com.swyp.ploutos.external.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NAVER API HUB 접속 설정. 주소와 인증 헤더는 API HUB 전용이며 다른 네이버 API 제품의 값과 섞지 않는다.
 */
@ConfigurationProperties(prefix = "ploutos.naver")
public record NaverApiProperties(
        String baseUrl,
        String clientId,
        String clientSecret
) {

    public NaverApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ploutos.naver.base-url 이 비어 있습니다. NAVER_API_HUB_BASE_URL 환경변수를 확인하세요.");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("ploutos.naver.client-id 가 비어 있습니다. NAVER_API_HUB_CLIENT_ID 환경변수를 확인하세요.");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("ploutos.naver.client-secret 이 비어 있습니다. NAVER_API_HUB_CLIENT_SECRET 환경변수를 확인하세요.");
        }
    }

    /** 설정이 로그에 찍혀도 인증값이 드러나지 않게 가린다. */
    @Override
    public String toString() {
        return "NaverApiProperties[baseUrl=" + baseUrl + ", clientId=****, clientSecret=****]";
    }
}
