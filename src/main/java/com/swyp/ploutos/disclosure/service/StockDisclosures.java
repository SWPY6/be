package com.swyp.ploutos.disclosure.service;

import java.time.OffsetDateTime;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureWindow;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;
import com.swyp.ploutos.disclosure.StockDisclosureFeed;

/**
 * 종목 공시 조회 결과. 공급자를 조회했는지(시장 지원·법인 매핑 여부)를 타입으로 구분한다.
 */
public sealed interface StockDisclosures {

    Long stockId();

    /** 종목 시장의 국가 */
    Country country();

    DisclosureWindow window();

    /**
     * 공급자를 조회한 결과.
     *
     * @param source         종목 시장의 공시 공급자
     * @param filedDateRange 공급자에 실제로 요청한 접수일 범위(시장 현지 날짜). 요청 기간을 포함한다.
     * @param fetchedAt      공급자에서 목록을 받은 시각(종목 시장 현지 시각). 캐시 결과면 과거 시각이다.
     */
    record Fetched(
            Long stockId,
            Country country,
            DisclosureSource source,
            DisclosureWindow window,
            FiledDateRange filedDateRange,
            OffsetDateTime fetchedAt,
            StockDisclosureFeed feed
    ) implements StockDisclosures {
    }

    /** 종목을 공급자 법인 코드에 연결하지 못해 공급자를 조회하지 않았다. 공시 0건과 다르다. */
    record Unmapped(
            Long stockId,
            Country country,
            DisclosureSource source,
            DisclosureWindow window
    ) implements StockDisclosures {
    }

    /** 종목 시장에 아직 공시 공급자가 없다(미국). 외부를 호출하지 않았다. */
    record Unsupported(
            Long stockId,
            Country country,
            DisclosureWindow window
    ) implements StockDisclosures {
    }
}
