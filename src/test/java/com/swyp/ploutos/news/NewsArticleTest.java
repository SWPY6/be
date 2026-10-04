package com.swyp.ploutos.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.news.NewsArticle.LinkKind;

class NewsArticleTest {

    private static final OffsetDateTime PUBLISHED_AT =
            OffsetDateTime.of(2026, 9, 30, 9, 12, 0, 0, ZoneOffset.ofHours(9));
    private static final String ORIGINAL = "https://news.mt.co.kr/mtview.php?no=2026093009120012345";

    @Test
    void 제목이_비었거나_시각이_없으면_기사를_만들지_않는다() {
        // when
        Optional<NewsArticle> blankTitle = article(" ", "요약", ORIGINAL, PUBLISHED_AT);
        Optional<NewsArticle> noTime = article("제목", "요약", ORIGINAL, null);

        // then
        assertThat(blankTitle).isEmpty();
        assertThat(noTime).isEmpty();
    }

    @Test
    void 요약이_비어_있으면_요약은_null이다() {
        // when
        NewsArticle article = article("제목", "", ORIGINAL, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.summary()).isNull();
    }

    @Test
    void 사전에_있는_도메인의_하위_도메인이면_언론사명을_채운다() {
        // when
        NewsArticle article = article("제목", "요약", ORIGINAL, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.source()).isEqualTo("news.mt.co.kr");
        assertThat(article.publisherName()).isEqualTo("머니투데이");
    }

    @Test
    void 사전에_없는_도메인이면_언론사명은_비우고_www를_뗀_호스트를_출처로_쓴다() {
        // when
        NewsArticle article = article("제목", "요약", "https://www.example-news.com/article/1", PUBLISHED_AT)
                .orElseThrow();

        // then
        assertThat(article.source()).isEqualTo("example-news.com");
        assertThat(article.publisherName()).isNull();
    }

    @Test
    void 호스트_대소문자와_fragment만_다르면_같은_문서_ID다() {
        // given
        String link = "https://news.mt.co.kr/mtview.php?no=1";
        String sameArticle = "https://NEWS.MT.CO.KR/mtview.php?no=1#comment";

        // when
        NewsArticle first = article("제목", "요약", link, PUBLISHED_AT).orElseThrow();
        NewsArticle second = article("제목", "요약", sameArticle, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(first.documentId()).hasSize(16).isEqualTo(second.documentId());
    }

    @Test
    void 기사_번호_query가_다르면_다른_문서_ID다() {
        // given
        String link = "https://news.mt.co.kr/mtview.php?no=1";
        String otherArticle = "https://news.mt.co.kr/mtview.php?no=2";

        // when
        NewsArticle first = article("제목", "요약", link, PUBLISHED_AT).orElseThrow();
        NewsArticle second = article("제목", "요약", otherArticle, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(first.documentId()).isNotEqualTo(second.documentId());
    }

    @Test
    void 제목이_같아도_URL이_다르면_다른_문서_ID다() {
        // given
        String title = "삼성전자, 3분기 실적 발표";

        // when
        NewsArticle first = article(title, "요약", "https://www.mk.co.kr/news/1", PUBLISHED_AT).orElseThrow();
        NewsArticle second = article(title, "요약", "https://www.hankyung.com/article/1", PUBLISHED_AT).orElseThrow();

        // then
        assertThat(first.documentId()).isNotEqualTo(second.documentId());
    }

    private static Optional<NewsArticle> article(String title, String summary, String url, OffsetDateTime publishedAt) {
        return NewsArticle.of(title, summary, URI.create(url), LinkKind.ORIGINAL, publishedAt);
    }
}
