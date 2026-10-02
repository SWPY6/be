package com.swyp.ploutos.disclosure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.disclosure.StockDisclosureFeed.Coverage;

class StockDisclosureFeedTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final DisclosureWindow KR_WINDOW = new DisclosureWindow(
            OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9)),
            OffsetDateTime.of(2026, 10, 2, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    );
    private static final DisclosureWindow US_WINDOW = new DisclosureWindow(
            OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.ofHours(-4)),
            OffsetDateTime.of(2026, 10, 1, 20, 0, 0, 0, ZoneOffset.ofHours(-4))
    );

    @Test
    void 접수일_내림차순으로_정렬하고_같은_날짜는_접수번호_내림차순이다() {
        // given
        Disclosure older = dart("20260929000001", "20260929");
        Disclosure sameDayFirst = dart("20260930000100", "20260930");
        Disclosure sameDaySecond = dart("20260930000200", "20260930");

        // when
        StockDisclosureFeed feed = StockDisclosureFeed.of(List.of(older, sameDayFirst, sameDaySecond), true, KR_WINDOW, SEOUL);

        // then
        assertThat(feed.items()).containsExactly(sameDaySecond, sameDayFirst, older);
    }

    @Test
    void 같은_날짜는_접수_시각_내림차순이고_시각이_없으면_뒤로_간다() {
        // given
        Disclosure morning = sec("0000320193-26-000001", "2026-09-30", "2026-09-30T13:00:00.000Z");
        Disclosure evening = sec("0000320193-26-000002", "2026-09-30", "2026-09-30T22:00:00.000Z");
        Disclosure untimed = sec("0000320193-26-000003", "2026-09-30", null);

        // when
        StockDisclosureFeed feed = StockDisclosureFeed.of(List.of(untimed, morning, evening), true, US_WINDOW, NEW_YORK);

        // then
        assertThat(feed.items()).containsExactly(evening, morning, untimed);
    }

    @Test
    void 시각이_있는_공시는_기간_밖이면_뺀다() {
        // given 기간 시작(2026-09-29 12:00 EDT = 16:00 UTC) 이전
        Disclosure before = sec("0000320193-26-000001", "2026-09-29", "2026-09-29T15:59:59.000Z");
        Disclosure inside = sec("0000320193-26-000002", "2026-09-29", "2026-09-29T16:00:01.000Z");

        // when
        StockDisclosureFeed feed = StockDisclosureFeed.of(List.of(before, inside), true, US_WINDOW, NEW_YORK);

        // then
        assertThat(feed.items()).containsExactly(inside);
    }

    @Test
    void 원문_ID가_같으면_한_건만_남긴다() {
        // given
        Disclosure first = dart("20260930000100", "20260930");
        Disclosure duplicate = dart("20260930000100", "20260930");

        // when
        StockDisclosureFeed feed = StockDisclosureFeed.of(List.of(first, duplicate), true, KR_WINDOW, SEOUL);

        // then
        assertThat(feed.total()).isEqualTo(1);
    }

    @Test
    void 끝까지_받았으면_COMPLETE_남아_있으면_PARTIAL이다() {
        // given
        List<Disclosure> candidates = List.of(dart("20260930000100", "20260930"));

        // when & then
        assertThat(StockDisclosureFeed.of(candidates, true, KR_WINDOW, SEOUL).coverage()).isEqualTo(Coverage.COMPLETE);
        assertThat(StockDisclosureFeed.of(candidates, false, KR_WINDOW, SEOUL).coverage()).isEqualTo(Coverage.PARTIAL);
    }

    @Test
    void 공시가_없어도_끝까지_받았으면_정상_0건이다() {
        // when
        StockDisclosureFeed feed = StockDisclosureFeed.of(List.of(), true, KR_WINDOW, SEOUL);

        // then
        assertThat(feed.items()).isEmpty();
        assertThat(feed.coverage()).isEqualTo(Coverage.COMPLETE);
        assertThat(feed.fetched()).isTrue();
    }

    @Test
    void 미매핑은_빈_목록이지만_조회한_결과가_아니다() {
        // when
        StockDisclosureFeed unmapped = StockDisclosureFeed.unmapped();

        // then
        assertThat(unmapped.coverage()).isEqualTo(Coverage.UNMAPPED);
        assertThat(unmapped.fetched()).isFalse();
    }

    @Test
    void 최신_N건은_화면_정렬_기준으로_고른다() {
        // given
        Disclosure oldest = dart("20260901000001", "20260901");
        Disclosure newest = dart("20260930000001", "20260930");
        Disclosure middle = dart("20260915000001", "20260915");

        // when
        List<Disclosure> latest = StockDisclosureFeed.latest(List.of(oldest, newest, middle), 2);

        // then
        assertThat(latest).containsExactly(newest, middle);
    }

    private static Disclosure dart(String receiptNo, String filedDate) {
        return Disclosure.dart(receiptNo, "분기보고서", "삼성전자", "삼성전자", null, filedDate).orElseThrow();
    }

    private static Disclosure sec(String accession, String filingDate, String acceptance) {
        return Disclosure.sec("0000320193", accession, "8-K", "Current report", "Apple Inc.", filingDate, acceptance,
                "a.htm").orElseThrow();
    }
}
