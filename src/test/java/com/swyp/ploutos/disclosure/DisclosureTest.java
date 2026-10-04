package com.swyp.ploutos.disclosure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.disclosure.Disclosure.DatePrecision;
import com.swyp.ploutos.disclosure.Disclosure.LinkKind;
import com.swyp.ploutos.disclosure.Disclosure.TimeBasis;
import com.swyp.ploutos.disclosure.DisclosureSource.WindowPrecision;

class DisclosureTest {

    private static final String RECEIPT_NO = "20260930000123";
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
    void 시각이_있으면_기간_경계를_시각으로_판단한다() {
        // given 기간 2026-09-29 18:44:50 EDT 초과 ~ 이하
        OffsetDateTime boundary = OffsetDateTime.of(2026, 9, 29, 18, 44, 50, 0, ZoneOffset.ofHours(-4));
        DisclosureWindow window = new DisclosureWindow(boundary, boundary.plusDays(1));
        Disclosure atBoundary = timed("2026-09-29T22:44:50Z");
        Disclosure after = timed("2026-09-29T22:44:51Z");

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
        Disclosure disclosure = timed("2026-10-01T02:00:00Z");

        // when & then
        assertThat(disclosure.localDateIn(NEW_YORK)).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void 접수_시각이_있으면_초_단위_접수_시각_기준이고_시장_현지_오프셋으로_준다() {
        // given
        Disclosure disclosure = timed("2026-09-29T22:44:50Z");

        // when & then
        assertThat(disclosure.datePrecision()).isEqualTo(DatePrecision.SECOND);
        assertThat(disclosure.timeBasis()).isEqualTo(TimeBasis.ACCEPTANCE_TIME);
        assertThat(disclosure.acceptedAtIn(NEW_YORK)).isEqualTo(OffsetDateTime.parse("2026-09-29T18:44:50-04:00"));
    }

    @Test
    void 접수_시각이_없으면_날짜_단위_접수일_기준이고_시각은_null이다() {
        // given
        Disclosure disclosure = Disclosure.dart(RECEIPT_NO, "분기보고서", "삼성전자", "삼성전자", null, "20260930")
                .orElseThrow();

        // when & then
        assertThat(disclosure.datePrecision()).isEqualTo(DatePrecision.DATE);
        assertThat(disclosure.timeBasis()).isEqualTo(TimeBasis.RECEIPT_DATE);
        assertThat(disclosure.acceptedAtIn(SEOUL)).isNull();
    }

    @Test
    void 국내_시장은_DART이고_공급자가_없는_시장은_비어_있다() {
        // when & then
        assertThat(DisclosureSource.of(Country.KR)).contains(DisclosureSource.DART);
        assertThat(DisclosureSource.of(Country.US)).isEmpty();
        assertThat(DisclosureSource.DART.windowPrecision()).isEqualTo(WindowPrecision.DATE_EXPANDED);
    }

    /** 접수 시각이 있는 공시. 시각을 주는 공급자가 아직 없어 직접 만든다. */
    private static Disclosure timed(String acceptedAt) {
        Instant instant = Instant.parse(acceptedAt);
        return new Disclosure(
                DisclosureSource.DART, RECEIPT_NO, "보고서", null, null, null, null, null,
                instant.atZone(NEW_YORK).toLocalDate(), instant,
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + RECEIPT_NO, LinkKind.DART_VIEWER
        );
    }
}
