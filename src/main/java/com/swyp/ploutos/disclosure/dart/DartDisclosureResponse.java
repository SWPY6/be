package com.swyp.ploutos.disclosure.dart;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DART 공시검색(list.json) 응답. 쓰지 않는 필드(page_no, page_count, total_page)는 받지 않는다.
 *
 * @param totalCount 조회 조건에 맞는 전체 건수. 1페이지만 받으므로 받은 건수보다 크면 일부만 받은 것이다.
 */
record DartDisclosureResponse(
        String status,
        @JsonProperty("total_count") Long totalCount,
        List<Item> list
) {

    /** 날짜(rcept_dt)는 yyyyMMdd다. 시각은 주지 않는다. */
    record Item(
            @JsonProperty("rcept_no") String receiptNo,
            @JsonProperty("report_nm") String reportName,
            @JsonProperty("corp_name") String corpName,
            @JsonProperty("flr_nm") String filerName,
            @JsonProperty("rcept_dt") String receiptDate,
            String rm
    ) {
    }
}
