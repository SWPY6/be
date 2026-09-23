package com.swyp.ploutos.external.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ploutos.kis")
public record KisProperties(
        String baseUrl,
        String appKey,
        String appSecret
) {

    public KisProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("ploutos.kis.base-url 이 비어 있습니다. KIS_BASE_URL 환경변수를 확인하세요.");
        }
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("ploutos.kis.app-key 가 비어 있습니다. KIS_APP_KEY 환경변수를 확인하세요.");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("ploutos.kis.app-secret 이 비어 있습니다. KIS_APP_SECRET 환경변수를 확인하세요.");
        }
    }
}
