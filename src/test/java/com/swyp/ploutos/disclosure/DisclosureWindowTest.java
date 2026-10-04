package com.swyp.ploutos.disclosure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.disclosure.DisclosureWindow.FiledDateRange;

class DisclosureWindowTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 2, 14, 0, 0, 0, KST);

    @Test
    void 기간을_생략하면_최근_30일이다() {
        // when
        DisclosureWindow window = DisclosureWindow.of(null, null, NOW);

        // then
        assertThat(window).isEqualTo(new DisclosureWindow(NOW.minusDays(30), NOW));
    }

    @Test
    void 기간을_주면_그대로_쓴다() {
        // given
        OffsetDateTime from = NOW.minusDays(90);

        // when
        DisclosureWindow window = DisclosureWindow.of(from, NOW, NOW);

        // then
        assertThat(window).isEqualTo(new DisclosureWindow(from, NOW));
    }

    @Test
    void 한쪽만_주면_잘못된_입력이다() {
        // when & then
        assertInvalid(NOW.minusDays(1), null);
        assertInvalid(null, NOW);
    }

    @Test
    void 시작이_끝보다_같거나_늦으면_잘못된_입력이다() {
        // when & then
        assertInvalid(NOW, NOW);
        assertInvalid(NOW, NOW.minusDays(1));
    }

    @Test
    void 끝이_미래면_잘못된_입력이다() {
        // when & then
        assertInvalid(NOW.minusDays(1), NOW.plusSeconds(1));
    }

    @Test
    void 기간이_90일을_넘으면_잘못된_입력이다() {
        // when & then
        assertInvalid(NOW.minusDays(90).minusSeconds(1), NOW);
    }

    @Test
    void 접수일_범위는_양_끝_시각이_걸친_한국_날짜다() {
        // given 전일 15:30 KST(장 마감) ~ 오늘 14:00 KST
        DisclosureWindow window = new DisclosureWindow(
                OffsetDateTime.of(2026, 10, 1, 15, 30, 0, 0, KST), NOW
        );

        // when
        FiledDateRange range = window.filedDatesIn(SEOUL);

        // then 전일 장 마감 이전에 접수된 공시도 포함되는 날짜 확장 범위다
        assertThat(range).isEqualTo(new FiledDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)));
    }

    @Test
    void 다른_오프셋으로_준_시각도_한국_날짜로_바꾼다() {
        // given 2026-10-01 16:00 UTC = 2026-10-02 01:00 KST
        DisclosureWindow window = new DisclosureWindow(
                OffsetDateTime.of(2026, 10, 1, 16, 0, 0, 0, ZoneOffset.UTC), NOW
        );

        // when
        FiledDateRange range = window.filedDatesIn(SEOUL);

        // then
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void 오늘에서_끝나는_기간의_공급자_범위는_기간과_관계없이_오늘_기준_최근_90일이다() {
        // given
        DisclosureWindow oneDay = new DisclosureWindow(NOW.minusDays(1), NOW);
        DisclosureWindow longest = new DisclosureWindow(NOW.minusDays(90), NOW);

        // when
        FiledDateRange oneDayRange = oneDay.searchRangeIn(SEOUL);
        FiledDateRange longestRange = longest.searchRangeIn(SEOUL);

        // then
        FiledDateRange expected = new FiledDateRange(LocalDate.of(2026, 7, 4), LocalDate.of(2026, 10, 2));
        assertThat(oneDayRange).isEqualTo(expected);
        assertThat(longestRange).isEqualTo(expected);
    }

    @Test
    void 과거에서_끝나는_기간의_공급자_범위는_시작과_관계없이_끝_날짜_기준_최근_90일이다() {
        // given
        DisclosureWindow tenDays = new DisclosureWindow(NOW.minusDays(10), NOW.minusDays(1));
        DisclosureWindow twoDays = new DisclosureWindow(NOW.minusDays(2), NOW.minusDays(1).plusHours(5));

        // when
        FiledDateRange tenDaysRange = tenDays.searchRangeIn(SEOUL);
        FiledDateRange twoDaysRange = twoDays.searchRangeIn(SEOUL);

        // then
        FiledDateRange expected = new FiledDateRange(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 10, 1));
        assertThat(tenDaysRange).isEqualTo(expected);
        assertThat(twoDaysRange).isEqualTo(expected);
    }

    @Test
    void 공급자_범위의_끝_날짜는_시장_날짜로_정한다() {
        // given 2026-10-01 16:00 UTC = 2026-10-02 01:00 KST
        DisclosureWindow window = new DisclosureWindow(
                NOW.minusDays(5), OffsetDateTime.of(2026, 10, 1, 16, 0, 0, 0, ZoneOffset.UTC)
        );

        // when
        FiledDateRange range = window.searchRangeIn(SEOUL);

        // then
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    private static void assertInvalid(OffsetDateTime from, OffsetDateTime to) {
        assertThatThrownBy(() -> DisclosureWindow.of(from, to, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
