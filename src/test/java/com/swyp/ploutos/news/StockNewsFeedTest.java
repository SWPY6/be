package com.swyp.ploutos.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class StockNewsFeedTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 30, 14, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final NewsWindow WINDOW = NewsWindow.of(NOW.minusDays(7), NOW, NOW);
    private static final String STOCK = "삼성전자";

    @Test
    void 제목에_종목명이_있는_기사만_남긴다() {
        // given
        NewsArticle inTitle = article("삼성전자 실적 발표", "요약", "https://a.com/1", NOW.minusHours(1));
        NewsArticle unrelated = article("SK하이닉스 실적", "요약", "https://a.com/3", NOW.minusHours(3));

        // when
        StockNewsFeed feed = StockNewsFeed.of(STOCK, List.of(inTitle, unrelated), true, WINDOW);

        // then
        assertThat(feed.items()).containsExactly(inTitle);
    }

    @Test
    void 요약에만_종목명이_있으면_뺀다() {
        // given
        NewsArticle homonym = article("불가리아 앞 흑해서 선박 침몰", "러시아가 기아를 무기화하고", "https://a.com/1",
                NOW.minusHours(1));
        NewsArticle listed = article("코스피 5.5% 급락", "삼성바이오로직스(0.4%), 기아(0.2%) 등은 상승했다", "https://a.com/2",
                NOW.minusHours(2));

        // when
        StockNewsFeed feed = StockNewsFeed.of("기아", List.of(homonym, listed), true, WINDOW);

        // then
        assertThat(feed.items()).isEmpty();
    }

    @Test
    void 영문_종목명은_대소문자를_가리지_않는다() {
        // given
        NewsArticle article = article("APPLE unveils new iPhone", "요약", "https://a.com/1", NOW.minusHours(1));

        // when
        StockNewsFeed feed = StockNewsFeed.of("Apple", List.of(article), true, WINDOW);

        // then
        assertThat(feed.items()).containsExactly(article);
    }

    @Test
    void 기간_밖의_기사는_뺀다() {
        // given
        NewsArticle atFrom = article("삼성전자 A", "요약", "https://a.com/1", WINDOW.from());
        NewsArticle atTo = article("삼성전자 B", "요약", "https://a.com/2", WINDOW.to());

        // when
        StockNewsFeed feed = StockNewsFeed.of(STOCK, List.of(atFrom, atTo), true, WINDOW);

        // then
        assertThat(feed.items()).containsExactly(atTo);
    }

    @Test
    void 같은_문서_ID는_한_건만_남긴다() {
        // given
        NewsArticle first = article("삼성전자 실적", "요약", "https://a.com/1", NOW.minusHours(1));
        NewsArticle duplicate = article("삼성전자 실적", "요약", "https://A.com/1#top", NOW.minusHours(1));

        // when
        StockNewsFeed feed = StockNewsFeed.of(STOCK, List.of(first, duplicate), true, WINDOW);

        // then
        assertThat(feed.items()).hasSize(1);
        assertThat(feed.total()).isEqualTo(1);
    }

    @Test
    void 최신순으로_정렬하고_시각이_같으면_문서_ID순이다() {
        // given
        NewsArticle oldest = article("삼성전자 1", "요약", "https://a.com/1", NOW.minusHours(3));
        NewsArticle sameTimeA = article("삼성전자 2", "요약", "https://a.com/2", NOW.minusHours(1));
        NewsArticle sameTimeB = article("삼성전자 3", "요약", "https://a.com/3", NOW.minusHours(1));

        // when
        StockNewsFeed feed = StockNewsFeed.of(STOCK, List.of(oldest, sameTimeA, sameTimeB), true, WINDOW);

        // then
        List<NewsArticle> sameTime = List.of(sameTimeA, sameTimeB).stream()
                .sorted((a, b) -> a.documentId().compareTo(b.documentId()))
                .toList();
        assertThat(feed.items()).containsExactly(sameTime.get(0), sameTime.get(1), oldest);
    }

    @Test
    void 관련_기사가_21건이면_최신_20건만_담는다() {
        // given
        List<NewsArticle> candidates = new ArrayList<>();
        IntStream.range(0, 21).forEach(i ->
                candidates.add(article("삼성전자 " + i, "요약", "https://a.com/" + i, NOW.minusHours(i))));

        // when
        StockNewsFeed feed = StockNewsFeed.of(STOCK, candidates, true, WINDOW);

        // then
        assertThat(feed.items()).hasSize(20);
        assertThat(feed.items().getLast()).isEqualTo(candidates.get(19));
    }

    @Test
    void 관련_기사가_없으면_빈_목록이다() {
        // given
        NewsArticle unrelated = article("SK하이닉스 실적", "요약", "https://a.com/1", NOW.minusHours(1));

        // when
        StockNewsFeed feed = StockNewsFeed.of(STOCK, List.of(unrelated), true, WINDOW);

        // then
        assertThat(feed.items()).isEmpty();
        assertThat(feed.total()).isZero();
    }

    private static NewsArticle article(String title, String summary, String url, OffsetDateTime publishedAt) {
        return NewsArticle.of(title, summary, URI.create(url), NewsArticle.LinkKind.ORIGINAL, publishedAt).orElseThrow();
    }
}
