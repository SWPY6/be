package com.swyp.ploutos.market;

/**
 * 지표가 어떤 성격의 시세인지. KIS 어댑터가 이 값으로 호출할 API를 고른다.
 * TR ID와 시장구분 코드는 어댑터가 갖고, 여기는 도메인 의미만 둔다.
 */
public enum IndicatorKind {

    DOMESTIC_INDEX,
    OVERSEAS_INDEX,
    EXCHANGE_RATE

}
