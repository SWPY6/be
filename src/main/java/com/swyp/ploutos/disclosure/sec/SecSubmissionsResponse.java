package com.swyp.ploutos.disclosure.sec;

import java.util.List;

/**
 * SEC submissions 응답. 쓰지 않는 필드는 받지 않는다. {@code recent}는 열 배열 구조라
 * 같은 인덱스가 한 제출 건이다. {@code files}는 recent에 없는 과거 이력 파일 목록이다(읽지 않는다).
 */
record SecSubmissionsResponse(String name, Filings filings) {

    record Filings(Recent recent, List<Object> files) {
    }

    /** filingDate는 yyyy-MM-dd, acceptanceDateTime은 UTC ISO-8601(예: 2026-09-29T22:44:50.000Z)이다. */
    record Recent(
            List<String> accessionNumber,
            List<String> filingDate,
            List<String> acceptanceDateTime,
            List<String> form,
            List<String> primaryDocument,
            List<String> primaryDocDescription
    ) {
    }
}
