package com.swyp.ploutos.disclosure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.disclosure.Disclosure.LinkKind;

class DisclosureTest {

    private static final String RECEIPT_NO = "20260930000123";
    private static final String CIK = "0000320193";
    // 제출 대행사 CIK(0001140361)가 앞자리에 붙은 accession number
    private static final String ACCESSION = "0001140361-26-038028";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void DART_정상_레코드면_제목_공백을_지우고_접수일만_날짜로_읽는다() {
        // when
        Optional<Disclosure> disclosure = Disclosure.dart(
                RECEIPT_NO, "  [기재정정]주요사항보고서(자기주식취득결정)  ", "삼성전자", "삼성전자", "유", "20260930"
        );

        // then
        assertThat(disclosure).contains(new Disclosure(
                DisclosureSource.DART, RECEIPT_NO, "[기재정정]주요사항보고서(자기주식취득결정)", null, null,
                "삼성전자", "삼성전자", "유", LocalDate.of(2026, 9, 30), null,
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + RECEIPT_NO, LinkKind.DART_VIEWER
        ));
    }

    @Test
    void DART_비고와_이름이_비어_있으면_null로_둔다() {
        // when
        Disclosure disclosure = Disclosure.dart(RECEIPT_NO, "분기보고서", " ", null, "", "20260930").orElseThrow();

        // then
        assertThat(disclosure.issuerName()).isNull();
        assertThat(disclosure.filerName()).isNull();
        assertThat(disclosure.remark()).isNull();
    }

    @Test
    void DART_접수번호가_14자리_숫자가_아니면_뺀다() {
        // when & then
        assertThat(Disclosure.dart(null, "분기보고서", "삼성전자", "삼성전자", null, "20260930")).isEmpty();
        assertThat(Disclosure.dart("2026093000012", "분기보고서", "삼성전자", "삼성전자", null, "20260930")).isEmpty();
        assertThat(Disclosure.dart("2026093000012A", "분기보고서", "삼성전자", "삼성전자", null, "20260930")).isEmpty();
    }

    @Test
    void DART_제목이나_접수일이_없으면_뺀다() {
        // when & then
        assertThat(Disclosure.dart(RECEIPT_NO, " ", "삼성전자", "삼성전자", null, "20260930")).isEmpty();
        assertThat(Disclosure.dart(RECEIPT_NO, "분기보고서", "삼성전자", "삼성전자", null, "2026-09-30")).isEmpty();
        assertThat(Disclosure.dart(RECEIPT_NO, "분기보고서", "삼성전자", "삼성전자", null, "20260931")).isEmpty();
    }

    @Test
    void SEC_접수_시각을_UTC로_읽고_원문_경로는_접수번호_앞자리가_아닌_법인_CIK로_만든다() {
        // when
        Disclosure disclosure = Disclosure.sec(
                CIK, ACCESSION, "4", "FORM 4", "Apple Inc.", "2026-09-29", "2026-09-29T22:44:50.000Z",
                "xslF345X06/form4.xml"
        ).orElseThrow();

        // then
        assertThat(disclosure.acceptedAt()).isEqualTo(Instant.parse("2026-09-29T22:44:50Z"));
        assertThat(disclosure.filedDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(disclosure.url()).isEqualTo(
                "https://www.sec.gov/Archives/edgar/data/320193/000114036126038028/xslF345X06/form4.xml"
        );
        assertThat(disclosure.linkKind()).isEqualTo(LinkKind.SEC_DOCUMENT);
        assertThat(disclosure.formType()).isEqualTo("4");
        assertThat(disclosure.formLabel()).isEqualTo("내부자 지분 변동");
        assertThat(disclosure.filerName()).isNull();
    }

    @Test
    void SEC_설명이_Form과_같거나_비었으면_법인명과_Form으로_제목을_만든다() {
        // when
        Disclosure formOnly = Disclosure.sec(CIK, ACCESSION, "4", "FORM 4", "Apple Inc.", "2026-09-29", null, "a.xml")
                .orElseThrow();
        Disclosure blank = Disclosure.sec(CIK, ACCESSION, "10-K", "", "Apple Inc.", "2026-09-29", null, "a.htm")
                .orElseThrow();
        Disclosure described = Disclosure.sec(CIK, ACCESSION, "8-K", "Current report", "Apple Inc.", "2026-09-29", null,
                "a.htm").orElseThrow();

        // then
        assertThat(formOnly.title()).isEqualTo("Apple Inc. 4");
        assertThat(blank.title()).isEqualTo("Apple Inc. 10-K");
        assertThat(described.title()).isEqualTo("Current report");
    }

    @Test
    void SEC_설명이_기본값_PRIMARY_DOCUMENT면_법인명과_Form으로_제목을_만든다() {
        // when 2026-10-02 실제 버크셔 해서웨이 Form 4 응답에서 확인한 값
        Disclosure upper = Disclosure.sec("0001067983", ACCESSION, "4", "PRIMARY DOCUMENT", "BERKSHIRE HATHAWAY INC",
                "2026-09-29", null, "a.xml").orElseThrow();
        Disclosure lower = Disclosure.sec("0001067983", ACCESSION, "4", " primary document ", "BERKSHIRE HATHAWAY INC",
                "2026-09-29", null, "a.xml").orElseThrow();

        // then
        assertThat(upper.title()).isEqualTo("BERKSHIRE HATHAWAY INC 4");
        assertThat(lower.title()).isEqualTo("BERKSHIRE HATHAWAY INC 4");
    }

    @Test
    void SEC_본문_경로가_없으면_제출_문서_목록_링크를_준다() {
        // when
        Disclosure disclosure = Disclosure.sec(CIK, ACCESSION, "8-K", null, "Apple Inc.", "2026-09-29", null, " ")
                .orElseThrow();

        // then
        assertThat(disclosure.url()).isEqualTo(
                "https://www.sec.gov/Archives/edgar/data/320193/000114036126038028/0001140361-26-038028-index.htm"
        );
        assertThat(disclosure.linkKind()).isEqualTo(LinkKind.SEC_FILING_INDEX);
    }

    @Test
    void SEC_접수_시각을_읽을_수_없으면_시각_없이_접수일만_둔다() {
        // when
        Disclosure disclosure = Disclosure.sec(CIK, ACCESSION, "10-K", "10-K", "Apple Inc.", "2026-09-29",
                "not-a-time", "a.htm").orElseThrow();

        // then
        assertThat(disclosure.acceptedAt()).isNull();
    }

    @Test
    void SEC_accession_형식이_틀리거나_Form이나_접수일이_없으면_뺀다() {
        // when & then
        assertThat(Disclosure.sec(CIK, "0001140361-26-38028", "4", null, "Apple", "2026-09-29", null, "a")).isEmpty();
        assertThat(Disclosure.sec(CIK, ACCESSION, " ", null, "Apple", "2026-09-29", null, "a")).isEmpty();
        assertThat(Disclosure.sec(CIK, ACCESSION, "4", null, "Apple", "20260929", null, "a")).isEmpty();
    }

    @Test
    void 시각이_있으면_기간_경계를_시각으로_판단한다() {
        // given 기간 2026-09-29 18:44:50 EDT 초과 ~ 이하
        OffsetDateTime boundary = OffsetDateTime.of(2026, 9, 29, 18, 44, 50, 0, ZoneOffset.ofHours(-4));
        DisclosureWindow window = new DisclosureWindow(boundary, boundary.plusDays(1));
        Disclosure atBoundary = Disclosure.sec(CIK, ACCESSION, "4", null, "Apple", "2026-09-29",
                "2026-09-29T22:44:50.000Z", "a").orElseThrow();
        Disclosure after = Disclosure.sec(CIK, ACCESSION, "4", null, "Apple", "2026-09-29",
                "2026-09-29T22:44:51.000Z", "a").orElseThrow();

        // when & then
        assertThat(atBoundary.filedIn(window, NEW_YORK)).isFalse();
        assertThat(after.filedIn(window, NEW_YORK)).isTrue();
    }

    @Test
    void 시각이_없으면_기간_양_끝_날짜_안에_접수됐는지로_판단한다() {
        // given 전일 15:30 ~ 오늘 14:00 KST
        DisclosureWindow window = new DisclosureWindow(
                OffsetDateTime.of(2026, 10, 1, 15, 30, 0, 0, ZoneOffset.ofHours(9)),
                OffsetDateTime.of(2026, 10, 2, 14, 0, 0, 0, ZoneOffset.ofHours(9))
        );

        // when & then 전일 장 마감 전 접수일 수도 있지만 날짜로만 판단한다
        assertThat(Disclosure.dart(RECEIPT_NO, "보고서", null, null, null, "20261001").orElseThrow()
                .filedIn(window, SEOUL)).isTrue();
        assertThat(Disclosure.dart(RECEIPT_NO, "보고서", null, null, null, "20260930").orElseThrow()
                .filedIn(window, SEOUL)).isFalse();
    }

    @Test
    void 조회_범위용_날짜는_시각이_있으면_시장_현지_날짜다() {
        // given 2026-10-01 02:00 UTC = 2026-09-30 22:00 EDT
        Disclosure disclosure = Disclosure.sec(CIK, ACCESSION, "4", null, "Apple", "2026-10-01",
                "2026-10-01T02:00:00.000Z", "a").orElseThrow();

        // when & then
        assertThat(disclosure.localDateIn(NEW_YORK)).isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
