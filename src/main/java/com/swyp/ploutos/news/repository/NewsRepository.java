package com.swyp.ploutos.news.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swyp.ploutos.news.News;
import com.swyp.ploutos.news.RelatedNews;

public interface NewsRepository extends JpaRepository<News, Long> {

    /**
     * 종목들에 연결된 뉴스를 발표 시각 구간으로 읽는다. 발표 시각 내림차순.
     *
     * <p>{@code News}에 접근자가 없어 엔티티를 그대로 돌려줄 수 없다. 생성자 프로젝션으로 값만
     * 꺼내면 엔티티를 수정하지 않아도 된다 — 이 모듈은 다른 담당자의 것이다.
     *
     * <p>{@code StockNews}에 연관 매핑이 없어 식별자를 {@code where}로 맞춰 조인한다.
     * {@code distinct}는 한 뉴스가 같은 산업의 여러 종목에 걸리는 경우를 DB에서 접어 준다.
     */
    @Query("""
            select distinct new com.swyp.ploutos.news.RelatedNews(
                    n.newsId, n.title, n.publisher, n.publishedAt, n.url)
            from News n, StockNews sn
            where sn.newsId = n.newsId
              and sn.stockId in :stockIds
              and n.publishedAt between :from and :to
            order by n.publishedAt desc
            """)
    List<RelatedNews> findRelated(@Param("stockIds") List<Long> stockIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
