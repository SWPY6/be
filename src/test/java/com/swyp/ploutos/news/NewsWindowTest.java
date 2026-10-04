package com.swyp.ploutos.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;

class NewsWindowTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 14, 0, 0, 0, ZoneOffset.ofHours(9));

    @Test
    void 기간을_생략하면_현재_기준_최근_7일이다() {
        // given
        OffsetDateTime from = null;
        OffsetDateTime to = null;

        // when
        NewsWindow window = NewsWindow.of(from, to, NOW);

        // then
        assertThat(window.from()).isEqualTo(NOW.minusDays(7));
        assertThat(window.to()).isEqualTo(NOW);
    }

    @Test
    void 기간을_지정하면_그대로_쓴다() {
        // given
        OffsetDateTime from = NOW.minusDays(2);
        OffsetDateTime to = NOW.minusDays(1);

        // when
        NewsWindow window = NewsWindow.of(from, to, NOW);

        // then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
    }

    @Test
    void 정확히_7일이면_허용한다() {
        // given
        OffsetDateTime from = NOW.minusDays(7);

        // when
        NewsWindow window = NewsWindow.of(from, NOW, NOW);

        // then
        assertThat(window.from()).isEqualTo(from);
    }

    @Test
    void 한쪽만_지정하면_잘못된_입력이다() {
        // given
        OffsetDateTime from = NOW.minusDays(1);

        // when & then
        assertInvalid(() -> NewsWindow.of(from, null, NOW));
        assertInvalid(() -> NewsWindow.of(null, NOW, NOW));
    }

    @Test
    void from이_to보다_늦거나_같으면_잘못된_입력이다() {
        // given
        OffsetDateTime to = NOW.minusDays(1);

        // when & then
        assertInvalid(() -> NewsWindow.of(to, to, NOW));
        assertInvalid(() -> NewsWindow.of(to.plusHours(1), to, NOW));
    }

    @Test
    void to가_미래면_잘못된_입력이다() {
        // given
        OffsetDateTime to = NOW.plusSeconds(1);

        // when & then
        assertInvalid(() -> NewsWindow.of(NOW.minusDays(1), to, NOW));
    }

    @Test
    void 기간이_7일을_넘으면_잘못된_입력이다() {
        // given
        OffsetDateTime from = NOW.minusDays(7).minusSeconds(1);

        // when & then
        assertInvalid(() -> NewsWindow.of(from, NOW, NOW));
    }

    @Test
    void from_시각은_제외하고_to_시각은_포함한다() {
        // given
        NewsWindow window = NewsWindow.of(NOW.minusDays(1), NOW, NOW);

        // when & then
        assertThat(window.contains(NOW.minusDays(1))).isFalse();
        assertThat(window.contains(NOW.minusDays(1).plusSeconds(1))).isTrue();
        assertThat(window.contains(NOW)).isTrue();
        assertThat(window.contains(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    void 오프셋이_달라도_같은_순간이면_경계를_같게_판단한다() {
        // given
        NewsWindow window = NewsWindow.of(NOW.minusDays(1), NOW, NOW);
        OffsetDateTime toInUtc = NOW.withOffsetSameInstant(ZoneOffset.UTC);

        // when
        boolean contains = window.contains(toInUtc);

        // then
        assertThat(contains).isTrue();
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
