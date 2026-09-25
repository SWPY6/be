package com.swyp.ploutos.external.kis.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.external.kis.KisProperties;

import lombok.RequiredArgsConstructor;

/**
 * KIS 접근토큰을 발급받아 메모리에 보관한다.
 * KIS는 토큰 발급을 1분에 1회로 제한하므로 만료 전에는 반드시 재사용해야 한다.
 */
@Component
@RequiredArgsConstructor
class CachedKisAccessTokenProvider implements KisAccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CachedKisAccessTokenProvider.class);

    static final String TOKEN_PATH = "/oauth2/tokenP";
    static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final KisProperties properties;
    private final Clock clock;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    // 여러 스레드가 동시에 만료를 감지해도 발급은 한 번만 일어나도록 메서드 전체를 잠근다.
    @Override
    public synchronized String accessToken() {
        if (isValid()) {
            return token;
        }
        issue();
        return token;
    }

    @Override
    public synchronized void invalidate() {
        expiresAt = Instant.EPOCH;
    }

    private boolean isValid() {
        return token != null && clock.instant().isBefore(expiresAt);
    }

    private void issue() {
        KisTokenResponse response = requestToken();
        if (response == null || response.accessToken() == null) {
            log.error("KIS 토큰 응답에 access_token 이 없습니다.");
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
        token = response.accessToken();
        expiresAt = clock.instant()
                .plusSeconds(response.expiresIn())
                .minus(REFRESH_MARGIN);
    }

    private KisTokenResponse requestToken() {
        try {
            return restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "grant_type", "client_credentials",
                            "appkey", properties.appKey(),
                            "appsecret", properties.appSecret()
                    ))
                    .retrieve()
                    .body(KisTokenResponse.class);
        } catch (RestClientException e) {
            log.error("KIS 토큰 발급에 실패했습니다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
    }
}
