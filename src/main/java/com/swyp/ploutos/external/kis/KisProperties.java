package com.swyp.ploutos.external.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ploutos.kis")
public record KisProperties(
        String baseUrl,
        String appKey,
        String appSecret,
        /**
         * 스스로 지키는 초당 호출 수. 실전 한도는 20인데 사용자 요청 몫을 남겨 둔다.
         * 설정을 빠뜨려도 뜨도록 기본값을 둔다 — 앱키와 달리 없다고 배포를 막을 값이 아니다.
         */
        @DefaultValue("15") int maxCallsPerSecond
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
        if (maxCallsPerSecond < 1) {
            throw new IllegalArgumentException("ploutos.kis.max-calls-per-second 는 1 이상이어야 합니다.");
        }
    }
}
