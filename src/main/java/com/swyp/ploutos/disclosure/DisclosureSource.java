package com.swyp.ploutos.disclosure;

import com.swyp.ploutos.common.enums.Country;

/** 공시 공급자. 종목 시장마다 하나이며 다른 시장 공급자로 대체하지 않는다. */
public enum DisclosureSource {

    DART,
    SEC;

    public static DisclosureSource of(Country country) {
        if (country == Country.KR) {
            return DART;
        }
        return SEC;
    }
}
