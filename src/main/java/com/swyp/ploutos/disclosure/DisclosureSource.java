package com.swyp.ploutos.disclosure;

import java.util.Optional;

import com.swyp.ploutos.common.enums.Country;

/** 공시 공급자. 종목 시장마다 하나이며 다른 시장 공급자로 대체하지 않는다. */
public enum DisclosureSource {

    /** 접수 날짜만 주므로 기간 양 끝 날짜 전체를 포함한다. */
    DART(WindowPrecision.DATE_EXPANDED);

    private final WindowPrecision windowPrecision;

    DisclosureSource(WindowPrecision windowPrecision) {
        this.windowPrecision = windowPrecision;
    }

    /**
     * 종목 시장의 공급자. 아직 공급자가 없는 시장(미국)이면 비어 있다.
     * 나라가 늘면 이 switch가 컴파일되지 않으므로 공급자를 정하지 않은 시장이 조용히 다른 공급자로 가지 않는다.
     */
    public static Optional<DisclosureSource> of(Country country) {
        return switch (country) {
            case KR -> Optional.of(DART);
            case US -> Optional.empty();
        };
    }

    public WindowPrecision windowPrecision() {
        return windowPrecision;
    }

    /** 기간을 공시에 적용한 정밀도. */
    public enum WindowPrecision {
        DATE_EXPANDED
    }
}
