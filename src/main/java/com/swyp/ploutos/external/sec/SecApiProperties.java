package com.swyp.ploutos.external.sec;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SEC EDGAR 접속 설정. API 키는 없고, SEC가 요구하는 대로 모든 요청에 서비스명과 연락처가 든 User-Agent를 보낸다.
 * 목록은 data.sec.gov, 티커 파일·원문은 www.sec.gov에 있다.
 */
@ConfigurationProperties(prefix = "ploutos.sec")
public record SecApiProperties(
        String userAgent,
        String dataBaseUrl,
        String wwwBaseUrl
) {

    public SecApiProperties {
        if (userAgent == null || userAgent.isBlank() || !userAgent.contains("@")) {
            throw new IllegalArgumentException(
                    "ploutos.sec.user-agent 에 서비스명과 연락 이메일이 필요합니다. SEC_USER_AGENT 환경변수를 확인하세요.");
        }
        if (dataBaseUrl == null || dataBaseUrl.isBlank()) {
            throw new IllegalArgumentException("ploutos.sec.data-base-url 이 비어 있습니다.");
        }
        if (wwwBaseUrl == null || wwwBaseUrl.isBlank()) {
            throw new IllegalArgumentException("ploutos.sec.www-base-url 이 비어 있습니다.");
        }
    }
}
