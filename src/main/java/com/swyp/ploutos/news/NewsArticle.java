package com.swyp.ploutos.news;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.web.util.HtmlUtils;

/**
 * 검색 결과 한 건을 화면에 보여 줄 수 있게 정제한 기사.
 * 출처는 원문 호스트이고 언론사명은 사전에 있을 때만 채운다. 시각은 공급자가 준 값이며
 * 원문 최초 발표 시각이라고 단정하지 않는다.
 */
public record NewsArticle(
        String documentId,
        String title,
        String summary,
        String url,
        LinkKind linkKind,
        String source,
        String publisherName,
        OffsetDateTime publishedAt
) {

    private static final int DOCUMENT_ID_LENGTH = 16;

    public enum LinkKind {
        /** 언론사 원문 링크 */
        ORIGINAL,
        /** 원문 링크가 없어 대신 쓴 네이버 뉴스 링크 */
        NAVER
    }

    /**
     * 검색 결과 원본으로 기사를 만든다. 제목이 비었거나, 쓸 수 있는 http(s) 링크가 없거나,
     * 시각이 없으면 보여 줄 수 없는 기사이므로 비어 있다.
     */
    public static Optional<NewsArticle> from(
            String rawTitle, String rawDescription, String originalLink, String link, OffsetDateTime publishedAt
    ) {
        String title = plainText(rawTitle);
        if (title.isEmpty() || publishedAt == null) {
            return Optional.empty();
        }
        Optional<URI> original = webUri(originalLink);
        if (original.isPresent()) {
            return Optional.of(of(title, rawDescription, original.get(), LinkKind.ORIGINAL, publishedAt));
        }
        return webUri(link).map(uri -> of(title, rawDescription, uri, LinkKind.NAVER, publishedAt));
    }

    private static NewsArticle of(
            String title, String rawDescription, URI uri, LinkKind linkKind, OffsetDateTime publishedAt
    ) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String summary = plainText(rawDescription);
        return new NewsArticle(
                documentIdOf(uri),
                title,
                summary.isEmpty() ? null : summary,
                uri.toString(),
                linkKind,
                host.startsWith("www.") ? host.substring("www.".length()) : host,
                Publishers.nameOf(host),
                publishedAt
        );
    }

    /** 제목이나 요약에 이름이 들어 있는지. 대소문자는 가리지 않는다. */
    public boolean mentions(String name) {
        String target = name.toLowerCase(Locale.ROOT);
        if (title.toLowerCase(Locale.ROOT).contains(target)) {
            return true;
        }
        return summary != null && summary.toLowerCase(Locale.ROOT).contains(target);
    }

    public boolean publishedIn(NewsWindow window) {
        return window.contains(publishedAt);
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

    /**
     * 같은 원문이면 같은 ID가 나오도록 URL을 정규화해 해시한다.
     * 스킴·호스트는 소문자로, fragment는 버리고, 기사 번호가 들어 있을 수 있는 query는 보존한다.
     */
    private static String documentIdOf(URI uri) {
        String key = uri.getScheme().toLowerCase(Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(Locale.ROOT)
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + (uri.getRawPath() == null ? "" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, DOCUMENT_ID_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256은 모든 JDK가 제공해야 한다.", e);
        }
    }
}
