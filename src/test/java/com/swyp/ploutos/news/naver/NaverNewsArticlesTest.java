package com.swyp.ploutos.news.naver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.NewsArticle.LinkKind;

class NaverNewsArticlesTest {

    private static final OffsetDateTime PUBLISHED_AT =
            OffsetDateTime.of(2026, 9, 30, 9, 12, 0, 0, ZoneOffset.ofHours(9));
    private static final String ORIGINAL = "https://news.mt.co.kr/mtview.php?no=2026093009120012345";
    private static final String NAVER = "https://n.news.naver.com/mnews/article/008/0005012345";

    @Test
    void 제목과_요약의_강조_태그와_HTML_엔티티를_일반_텍스트로_바꾼다() {
        // given
        String title = "<b>삼성전자</b>, &quot;HBM&quot; 공급 &amp; 증설";
        String description = "<b>삼성전자</b>가  &lt;b&gt;태그&lt;/b&gt; 문자열을\n포함한 요약";

        // when
        NewsArticle article = NaverNewsArticles.from(title, description, ORIGINAL, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.title()).isEqualTo("삼성전자, \"HBM\" 공급 & 증설");
        assertThat(article.summary()).isEqualTo("삼성전자가 <b>태그</b> 문자열을 포함한 요약");
    }

    @Test
    void 태그를_지우면_제목이_비면_기사를_만들지_않는다() {
        // given
        String title = "<b> </b>";

        // when
        Optional<NewsArticle> article = NaverNewsArticles.from(title, "요약", ORIGINAL, NAVER, PUBLISHED_AT);

        // then
        assertThat(article).isEmpty();
    }

    @Test
    void 요약이_비어_있으면_요약은_null이다() {
        // given
        String description = "";

        // when
        NewsArticle article = NaverNewsArticles.from("제목", description, ORIGINAL, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.summary()).isNull();
    }

    @Test
    void 시각이_없으면_기사를_만들지_않는다() {
        // given
        OffsetDateTime publishedAt = null;

        // when
        Optional<NewsArticle> article = NaverNewsArticles.from("제목", "요약", ORIGINAL, NAVER, publishedAt);

        // then
        assertThat(article).isEmpty();
    }

    @Test
    void 원문_링크가_있으면_원문_링크를_쓴다() {
        // given
        String originalLink = ORIGINAL;

        // when
        NewsArticle article = NaverNewsArticles.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.url()).isEqualTo(ORIGINAL);
        assertThat(article.linkKind()).isEqualTo(LinkKind.ORIGINAL);
    }

    @Test
    void 원문_링크가_없으면_네이버_링크를_쓴다() {
        // given
        String originalLink = "";

        // when
        NewsArticle article = NaverNewsArticles.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.url()).isEqualTo(NAVER);
        assertThat(article.linkKind()).isEqualTo(LinkKind.NAVER);
        assertThat(article.source()).isEqualTo("n.news.naver.com");
    }

    @Test
    void 원문_링크가_http가_아니면_네이버_링크를_쓴다() {
        // given
        String originalLink = "javascript:alert(1)";

        // when
        NewsArticle article = NaverNewsArticles.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.url()).isEqualTo(NAVER);
        assertThat(article.linkKind()).isEqualTo(LinkKind.NAVER);
    }

    @Test
    void 쓸_수_있는_링크가_하나도_없으면_기사를_만들지_않는다() {
        // given
        String originalLink = "ftp://news.mt.co.kr/a";
        String link = "not a url";

        // when
        Optional<NewsArticle> article = NaverNewsArticles.from("제목", "요약", originalLink, link, PUBLISHED_AT);

        // then
        assertThat(article).isEmpty();
    }
}
