package com.swyp.ploutos.external.dart;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Open DART 접속 설정. 인증키는 요청 URL 쿼리(crtfc_key)에 실리므로 로그·응답에 남기지 않는다.
 */
@ConfigurationProperties(prefix = "ploutos.dart")
public record DartApiProperties(
        String baseUrl,
        String apiKey
) {

    public DartApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ploutos.dart.base-url 이 비어 있습니다. DART_BASE_URL 환경변수를 확인하세요.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ploutos.dart.api-key 가 비어 있습니다. DART_API_KEY 환경변수를 확인하세요.");
        }
    }

    /** 설정이 로그에 찍혀도 인증키가 드러나지 않게 가린다. */
    @Override
    public String toString() {
        return "DartApiProperties[baseUrl=" + baseUrl + ", apiKey=****]";
    }
}
