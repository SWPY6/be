package com.swyp.ploutos.news;

import java.time.LocalDateTime;

/**
 * 종목에 연결된 뉴스 한 건. 화면이 쓰는 값만 담는다 — 본문 요약과 분류는 뺐다.
 *
 * <p>이 모듈이 소유하는 이유는 뉴스를 가진 쪽이 뉴스의 값 객체를 가져야 의존이 한 방향으로
 * 유지되기 때문이다. 읽는 모듈에 두면 계약을 제공하는 {@code news}가 소비하는 쪽을 알아야 한다.
 */
public record RelatedNews(
        Long newsId,
        String title,
        String publisher,
        LocalDateTime publishedAt,
        String url
) {
}
