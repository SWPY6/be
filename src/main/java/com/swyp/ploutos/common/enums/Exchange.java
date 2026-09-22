package com.swyp.ploutos.common.enums;

/**
 * 종목이 실제로 거래되는 거래소. 시장·지수 소속({@link MarketCode})과는 다른 정보다 —
 * S&P500 구성 종목은 NASDAQ 또는 NYSE에 상장돼 있다.
 */
public enum Exchange {

    KRX,
    NASDAQ,
    NYSE;

    public boolean isDomestic() {
        return this == KRX;
    }

}
