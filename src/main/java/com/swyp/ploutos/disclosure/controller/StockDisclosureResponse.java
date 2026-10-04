package com.swyp.ploutos.disclosure.controller;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.disclosure.Disclosure;
import com.swyp.ploutos.disclosure.Disclosure.DatePrecision;
import com.swyp.ploutos.disclosure.Disclosure.LinkKind;
import com.swyp.ploutos.disclosure.Disclosure.TimeBasis;
import com.swyp.ploutos.disclosure.DisclosureSource;
import com.swyp.ploutos.disclosure.DisclosureSource.WindowPrecision;
import com.swyp.ploutos.disclosure.DisclosureWindow;
import com.swyp.ploutos.disclosure.StockDisclosureFeed.Coverage;
import com.swyp.ploutos.disclosure.service.StockDisclosures;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "종목 공시. 함께 확인된 맥락이며 가격 변동의 원인으로 단정하지 않는다")
record StockDisclosureResponse(
        @Schema(description = "종목 ID", example = "1") Long stockId,
        @Schema(description = "종목 시장의 국가(KR/US)", example = "KR") Country country,
        @Schema(description = "공시 공급자. KR=DART, US=SEC", example = "DART") DisclosureSource source,
        @Schema(description = "요청 기간. from 제외, to 포함") Window window,
        @Schema(description = "DATE_EXPANDED(DART): 접수 날짜만 있어 기간 양 끝 날짜 전체를 포함했다. "
                + "EXACT(SEC): 접수 시각으로 기간을 걸렀다(시각이 없는 건만 날짜로 포함). 공급자를 조회하지 않았으면 null",
                example = "DATE_EXPANDED", nullable = true)
        WindowPrecision windowPrecision,
        @Schema(description = "공급자에 요청한 접수일 범위(시장 현지 날짜, 양 끝 포함). 기간이 오늘에서 끝나면 오늘 기준 "
                + "최근 90일로 고정하므로 요청 기간보다 넓을 수 있다. 공급자를 조회하지 않았으면 null", nullable = true)
        FiledDateRange filedDateRange,
        @Schema(description = "공급자에서 목록을 받은 시각(시장 현지 시각). 최대 10분 캐시라 현재보다 과거일 수 있다. "
                + "공급자를 조회하지 않았으면 null", example = "2026-10-02T13:55:12+09:00", nullable = true)
        OffsetDateTime fetchedAt,
        @Schema(description = "COMPLETE: 요청 기간의 공시를 다 받음(0건이면 정말 없음), "
                + "PARTIAL: 최신 100건만 받았거나 과거 이력을 다 보지 못해 요청 기간에 더 있을 수 있음, "
                + "UNMAPPED: 종목을 공급자 법인에 연결하지 못함(items가 비어 있어도 공시 0건이 아니다)", example = "COMPLETE")
        Coverage coverage,
        @Schema(description = "items 건수(기간 필터·중복 제거 후). 공급자 전체 건수가 아니다", example = "1") int total,
        @Schema(description = "접수일 → 접수 시각 → 원문 ID 내림차순. 시각이 없는 같은 날짜 안의 순서는 실제 접수 순서를 보장하지 않는다")
        List<Item> items
) {

    static StockDisclosureResponse from(StockDisclosures disclosures) {
        return switch (disclosures) {
            case StockDisclosures.Fetched fetched -> fetched(fetched);
            case StockDisclosures.Unmapped unmapped -> unmapped(unmapped);
        };
    }

    private static StockDisclosureResponse fetched(StockDisclosures.Fetched disclosures) {
        ZoneId zone = disclosures.country().zoneId();
        return new StockDisclosureResponse(
                disclosures.stockId(),
                disclosures.country(),
                disclosures.source(),
                Window.from(disclosures.window()),
                disclosures.source().windowPrecision(),
                FiledDateRange.from(disclosures.filedDateRange()),
                disclosures.fetchedAt(),
                disclosures.feed().coverage(),
                disclosures.feed().total(),
                disclosures.feed().items().stream().map(disclosure -> Item.from(disclosure, zone)).toList()
        );
    }

    /** 공급자를 조회하지 않았으므로 조회 정보는 null이고, 빈 목록이지만 공시 0건과 구분한다. */
    private static StockDisclosureResponse unmapped(StockDisclosures.Unmapped disclosures) {
        return new StockDisclosureResponse(
                disclosures.stockId(),
                disclosures.country(),
                disclosures.source(),
                Window.from(disclosures.window()),
                null,
                null,
                null,
                Coverage.UNMAPPED,
                0,
                List.of()
        );
    }

    @Schema(description = "요청 기간")
    record Window(
            @Schema(example = "2026-09-02T14:00:00+09:00") OffsetDateTime from,
            @Schema(example = "2026-10-02T14:00:00+09:00") OffsetDateTime to
    ) {

        static Window from(DisclosureWindow window) {
            return new Window(window.from(), window.to());
        }
    }

    @Schema(description = "접수일 조회 범위(양 끝 포함)")
    record FiledDateRange(
            @Schema(example = "2026-09-02") LocalDate from,
            @Schema(example = "2026-10-02") LocalDate to
    ) {

        static FiledDateRange from(DisclosureWindow.FiledDateRange range) {
            return new FiledDateRange(range.from(), range.to());
        }
    }

    @Schema(description = "공시")
    record Item(
            @Schema(description = "공급자", example = "DART") DisclosureSource provider,
            @Schema(description = "공급자 원문 ID(DART 접수번호, SEC accession number). 같은 값이면 같은 공시",
                    example = "20260930000123")
            String providerDocumentId,
            @Schema(description = "자료 종류. 항상 DISCLOSURE", example = "DISCLOSURE") String type,
            @Schema(description = "보고서명. DART는 [기재정정] 같은 정정 표기를 그대로 둔다. SEC는 영문 원문",
                    example = "[기재정정]주요사항보고서(자기주식취득결정)")
            String title,
            @Schema(description = "SEC Form 원문(/A 포함). DART는 null", example = "10-K", nullable = true) String formType,
            @Schema(description = "SEC Form 한글 라벨(고정 사전). 사전에 없거나 DART면 null", example = "연간보고서",
                    nullable = true)
            String formLabel,
            @Schema(description = "공시 대상 법인명", example = "삼성전자", nullable = true) String issuerName,
            @Schema(description = "제출인(DART). 법인 자신이 아닐 수 있다(예: 임원·주요주주). SEC는 null",
                    example = "삼성전자", nullable = true)
            String filerName,
            @Schema(description = "DART 비고(rm) 원문. 없거나 SEC면 null", example = "유", nullable = true) String remark,
            @Schema(description = "접수일", example = "2026-09-30") LocalDate filedDate,
            @Schema(description = "SEC 접수 시각(시장 현지 오프셋). DART이거나 시각을 확인하지 못하면 null",
                    example = "2026-09-29T18:44:50-04:00", nullable = true)
            OffsetDateTime publishedAt,
            @Schema(description = "SECOND: publishedAt이 있음, DATE: 접수일만 있음", example = "DATE")
            DatePrecision datePrecision,
            @Schema(description = "ACCEPTANCE_TIME: SEC 접수 시각, RECEIPT_DATE: 접수일. 최초 공개 시각이라고 단정하지 않는다",
                    example = "RECEIPT_DATE")
            TimeBasis timeBasis,
            @Schema(description = "요약. 1차는 제공하지 않아 항상 null", nullable = true) String summary,
            @Schema(description = "요약 제공 여부. 1차는 항상 UNAVAILABLE", example = "UNAVAILABLE") String summaryStatus,
            @Schema(description = "원문 링크", example = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260930000123")
            String url,
            @Schema(description = "DART_VIEWER: DART 공시 뷰어, SEC_DOCUMENT: SEC 제출 문서 본문, "
                    + "SEC_FILING_INDEX: 본문 경로가 없어 대신 준 SEC 제출 문서 목록", example = "DART_VIEWER")
            LinkKind linkKind
    ) {

        static Item from(Disclosure disclosure, ZoneId zone) {
            return new Item(
                    disclosure.source(),
                    disclosure.documentId(),
                    "DISCLOSURE",
                    disclosure.title(),
                    disclosure.formType(),
                    disclosure.formLabel(),
                    disclosure.issuerName(),
                    disclosure.filerName(),
                    disclosure.remark(),
                    disclosure.filedDate(),
                    disclosure.acceptedAtIn(zone),
                    disclosure.datePrecision(),
                    disclosure.timeBasis(),
                    null,
                    "UNAVAILABLE",
                    disclosure.url(),
                    disclosure.linkKind()
            );
        }
    }
}
