package com.swyp.ploutos.news.service;

import java.time.LocalDateTime;
import java.util.List;

import com.swyp.ploutos.news.RelatedNews;

/**
 * 종목에 연결된 뉴스를 읽는다. 이 모듈이 다른 모듈에 노출하는 유일한 계약이다.
 */
public interface NewsReader {

    /**
     * 종목들에 연결된 뉴스를 발표 시각 구간으로 읽는다. 발표 시각 내림차순이고,
     * 같은 뉴스가 여러 종목에 걸려도 한 번만 담긴다. 종목 목록이 비면 빈 목록이다.
     *
     * <p>건수를 제한하지 않는다. 몇 건을 보여줄지는 화면의 결정이므로 호출자가 자른다.
     */
    List<RelatedNews> readByStockIds(List<Long> stockIds, LocalDateTime from, LocalDateTime to);
}
