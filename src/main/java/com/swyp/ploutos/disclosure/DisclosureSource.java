package com.swyp.ploutos.disclosure;

import com.swyp.ploutos.common.enums.Country;

/** 공시 공급자. 종목 시장마다 하나이며 다른 시장 공급자로 대체하지 않는다. */
public enum DisclosureSource {

    /** 접수 날짜만 주므로 기간 양 끝 날짜 전체를 포함한다. */
    DART(WindowPrecision.DATE_EXPANDED),
    /** 접수 시각으로 기간을 거른다(시각이 없는 건만 날짜로 포함). */
    SEC(WindowPrecision.EXACT);

    private final WindowPrecision windowPrecision;

    DisclosureSource(WindowPrecision windowPrecision) {
        this.windowPrecision = windowPrecision;
    }

    /** 나라가 늘면 이 switch가 컴파일되지 않으므로 공급자를 정하지 않은 시장이 조용히 다른 공급자로 가지 않는다. */
    public static DisclosureSource of(Country country) {
        return switch (country) {
            case KR -> DART;
            case US -> SEC;
        };
    }

    public WindowPrecision windowPrecision() {
        return windowPrecision;
    }

    /** 기간을 공시에 적용한 정밀도. */
    public enum WindowPrecision {
        DATE_EXPANDED,
        EXACT
    }
}
