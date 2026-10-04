package com.swyp.ploutos.news;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * 화면에 보여 줄 수 있게 정제한 기사. 공급자 형식(HTML 강조 태그, 링크 구조)은 어댑터가 풀어서 넘긴다.
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
     * 정제된 값으로 기사를 만든다. 제목이 비었거나 시각이 없으면 보여 줄 수 없는 기사이므로 비어 있다.
     *
     * @param summary 일반 텍스트 요약. 비었으면 null로 둔다.
     * @param url     호스트가 있는 http(s) 링크
     */
    public static Optional<NewsArticle> of(
            String title, String summary, URI url, LinkKind linkKind, OffsetDateTime publishedAt
    ) {
        if (title == null || title.isBlank() || publishedAt == null) {
            return Optional.empty();
        }
        String host = url.getHost().toLowerCase(Locale.ROOT);
        return Optional.of(new NewsArticle(
                documentIdOf(url),
                title,
                summary == null || summary.isBlank() ? null : summary,
                url.toString(),
                linkKind,
                host.startsWith("www.") ? host.substring("www.".length()) : host,
                Publishers.nameOf(host),
                publishedAt
        ));
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
