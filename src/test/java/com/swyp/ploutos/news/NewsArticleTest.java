package com.swyp.ploutos.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.news.NewsArticle.LinkKind;

class NewsArticleTest {

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
        NewsArticle article = NewsArticle.from(title, description, ORIGINAL, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.title()).isEqualTo("삼성전자, \"HBM\" 공급 & 증설");
        assertThat(article.summary()).isEqualTo("삼성전자가 <b>태그</b> 문자열을 포함한 요약");
    }

    @Test
    void 태그를_지우면_제목이_비면_기사를_만들지_않는다() {
        // given
        String title = "<b> </b>";

        // when
        Optional<NewsArticle> article = NewsArticle.from(title, "요약", ORIGINAL, NAVER, PUBLISHED_AT);

        // then
        assertThat(article).isEmpty();
    }

    @Test
    void 요약이_비어_있으면_요약은_null이다() {
        // given
        String description = "";

        // when
        NewsArticle article = NewsArticle.from("제목", description, ORIGINAL, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.summary()).isNull();
    }

    @Test
    void 시각이_없으면_기사를_만들지_않는다() {
        // given
        OffsetDateTime publishedAt = null;

        // when
        Optional<NewsArticle> article = NewsArticle.from("제목", "요약", ORIGINAL, NAVER, publishedAt);

        // then
        assertThat(article).isEmpty();
    }

    @Test
    void 원문_링크가_있으면_원문_링크를_쓴다() {
        // given
        String originalLink = ORIGINAL;

        // when
        NewsArticle article = NewsArticle.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.url()).isEqualTo(ORIGINAL);
        assertThat(article.linkKind()).isEqualTo(LinkKind.ORIGINAL);
    }

    @Test
    void 원문_링크가_없으면_네이버_링크를_쓴다() {
        // given
        String originalLink = "";

        // when
        NewsArticle article = NewsArticle.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

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
        NewsArticle article = NewsArticle.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

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
        Optional<NewsArticle> article = NewsArticle.from("제목", "요약", originalLink, link, PUBLISHED_AT);

        // then
        assertThat(article).isEmpty();
    }

    @Test
    void 사전에_있는_도메인의_하위_도메인이면_언론사명을_채운다() {
        // given
        String originalLink = ORIGINAL;

        // when
        NewsArticle article = NewsArticle.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(article.source()).isEqualTo("news.mt.co.kr");
        assertThat(article.publisherName()).isEqualTo("머니투데이");
    }

    @Test
    void 사전에_없는_도메인이면_언론사명은_비우고_www를_뗀_호스트를_출처로_쓴다() {
        // given
        String originalLink = "https://www.example-news.com/article/1";

        // when
        NewsArticle article = NewsArticle.from("제목", "요약", originalLink, NAVER, PUBLISHED_AT).orElseThrow();

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
        NewsArticle first = NewsArticle.from("제목", "요약", link, NAVER, PUBLISHED_AT).orElseThrow();
        NewsArticle second = NewsArticle.from("제목", "요약", sameArticle, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(first.documentId()).hasSize(16).isEqualTo(second.documentId());
    }

    @Test
    void 기사_번호_query가_다르면_다른_문서_ID다() {
        // given
        String link = "https://news.mt.co.kr/mtview.php?no=1";
        String otherArticle = "https://news.mt.co.kr/mtview.php?no=2";

        // when
        NewsArticle first = NewsArticle.from("제목", "요약", link, NAVER, PUBLISHED_AT).orElseThrow();
        NewsArticle second = NewsArticle.from("제목", "요약", otherArticle, NAVER, PUBLISHED_AT).orElseThrow();

        // then
        assertThat(first.documentId()).isNotEqualTo(second.documentId());
    }

    @Test
    void 제목이_같아도_URL이_다르면_다른_문서_ID다() {
        // given
        String title = "삼성전자, 3분기 실적 발표";

        // when
        NewsArticle first = NewsArticle.from(title, "요약", "https://www.mk.co.kr/news/1", NAVER, PUBLISHED_AT)
                .orElseThrow();
        NewsArticle second = NewsArticle.from(title, "요약", "https://www.hankyung.com/article/1", NAVER, PUBLISHED_AT)
                .orElseThrow();

        // then
        assertThat(first.documentId()).isNotEqualTo(second.documentId());
    }
}
