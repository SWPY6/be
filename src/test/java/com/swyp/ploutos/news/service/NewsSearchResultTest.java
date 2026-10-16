package com.swyp.ploutos.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.news.NewsWindow;

class NewsSearchResultTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 14, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final NewsWindow WINDOW = NewsWindow.of(NOW.minusDays(7), NOW, NOW);

    @Test
    void 결과가_더_남았고_가장_오래된_응답_기사가_기간_시작보다_늦으면_기간을_다_훑지_못한_것이다() {
        // given
        NewsSearchResult result = new NewsSearchResult(List.of(), false, NOW.minusDays(1));

        // when
        boolean covered = result.covers(WINDOW);

        // then
        assertThat(covered).isFalse();
    }

    @Test
    void 결과가_더_남았어도_가장_오래된_응답_기사가_기간_시작에_닿았으면_기간을_다_훑은_것이다() {
        // given
        NewsSearchResult result = new NewsSearchResult(List.of(), false, WINDOW.from());

        // when
        boolean covered = result.covers(WINDOW);

        // then
        assertThat(covered).isTrue();
    }

    @Test
    void 검색_결과를_끝까지_받았으면_기간을_다_훑은_것이다() {
        // given
        NewsSearchResult result = new NewsSearchResult(List.of(), true, NOW.minusDays(1));

        // when
        boolean covered = result.covers(WINDOW);

        // then
        assertThat(covered).isTrue();
    }
}
