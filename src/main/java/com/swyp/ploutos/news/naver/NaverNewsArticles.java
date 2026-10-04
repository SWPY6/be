package com.swyp.ploutos.news.naver;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.web.util.HtmlUtils;

import com.swyp.ploutos.news.NewsArticle;
import com.swyp.ploutos.news.NewsArticle.LinkKind;

/**
 * 네이버 검색 결과 한 건을 기사로 바꾼다. 제목·요약의 HTML 강조 태그와 엔티티를 일반 텍스트로 풀고,
 * 언론사 원문 링크(originallink)를 먼저, 없으면 네이버 뉴스 링크(link)를 쓴다.
 */
final class NaverNewsArticles {

    private NaverNewsArticles() {
    }

    static Optional<NewsArticle> from(NaverNewsResponse.Item item) {
        return from(item.title(), item.description(), item.originallink(), item.link(), parsePubDate(item.pubDate()));
    }

    /** 쓸 수 있는 http(s) 링크가 없거나, 제목이 비었거나, 시각이 없으면 보여 줄 수 없는 기사이므로 비어 있다. */
    static Optional<NewsArticle> from(
            String rawTitle, String rawDescription, String originalLink, String link, OffsetDateTime publishedAt
    ) {
        String title = plainText(rawTitle);
        String summary = plainText(rawDescription);
        Optional<URI> original = webUri(originalLink);
        if (original.isPresent()) {
            return NewsArticle.of(title, summary, original.get(), LinkKind.ORIGINAL, publishedAt);
        }
        return webUri(link).flatMap(uri -> NewsArticle.of(title, summary, uri, LinkKind.NAVER, publishedAt));
    }

    /** 태그를 먼저 지운 뒤 엔티티를 푼다. 순서가 반대면 {@code &lt;b&gt;} 같은 본문 글자까지 태그로 지워진다. */
    private static String plainText(String html) {
        if (html == null) {
            return "";
        }
        String withoutTags = html.replaceAll("<[^>]*>", "");
        return HtmlUtils.htmlUnescape(withoutTags).replaceAll("\\s+", " ").strip();
    }

    private static Optional<URI> webUri(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(value.strip());
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return Optional.empty();
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** 파싱하지 못한 시각은 null이며, 그 기사는 빠진다. */
    private static OffsetDateTime parsePubDate(String pubDate) {
        if (pubDate == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(pubDate.strip(), DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
