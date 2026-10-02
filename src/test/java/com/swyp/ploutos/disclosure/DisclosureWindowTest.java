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

    private static void assertInvalid(OffsetDateTime from, OffsetDateTime to) {
        assertThatThrownBy(() -> DisclosureWindow.of(from, to, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
