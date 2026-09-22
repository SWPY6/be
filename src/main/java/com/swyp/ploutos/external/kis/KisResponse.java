package com.swyp.ploutos.external.kis;

/**
 * KIS API 응답의 공통 봉투. 각 API 응답 record가 이를 구현하면
 * {@link KisApiClient}가 본문을 한 번만 역직렬화하고도 성공 여부를 판정할 수 있다.
 */
public interface KisResponse {

    String TOKEN_EXPIRED_CODE = "EGW00123";

    String rtCd();

    String msgCd();

    String msg1();

    default boolean isSuccess() {
        return "0".equals(rtCd());
    }

    default boolean isTokenExpired() {
        return TOKEN_EXPIRED_CODE.equals(msgCd());
    }
}
