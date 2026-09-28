package com.swyp.ploutos.stock.quote;

import com.swyp.ploutos.common.enums.Country;

/** 시세가 실시간인지 지연인지. */
public enum PriceTiming {

    REALTIME,
    DELAYED;

    /**
     * 시장이 속한 나라로 실시간 여부를 정한다. KIS는 국내와 미국을 0분 지연으로 주므로 둘 다 실시간이다.
     * 15분 지연 시장(홍콩·중국·일본·베트남)을 지원하면 여기에 DELAYED를 더한다.
     */
    public static PriceTiming of(Country country) {
        return switch (country) {
            case KR, US -> REALTIME;
        };
    }
}
