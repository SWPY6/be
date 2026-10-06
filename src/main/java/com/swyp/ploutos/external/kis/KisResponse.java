package com.swyp.ploutos.external.kis;

/**
 * KIS API 응답의 공통 봉투. 각 API 응답 record가 이를 구현하면
 * {@link KisApiClient}가 본문을 한 번만 역직렬화하고도 성공 여부를 판정할 수 있다.
 */
public interface KisResponse {

    String TOKEN_EXPIRED_CODE = "EGW00123";
    String RATE_LIMITED_CODE = "EGW00201";

    String rtCd();

    String msgCd();

    String msg1();

    default boolean isSuccess() {
        return "0".equals(rtCd());
    }

    default boolean isTokenExpired() {
        return TOKEN_EXPIRED_CODE.equals(msgCd());
    }

    /** 초당 호출 한도를 넘었다. 잠시 뒤 다시 부르면 통과하는 일시 오류다. */
    default boolean isRateLimited() {
        return RATE_LIMITED_CODE.equals(msgCd());
    }
}
