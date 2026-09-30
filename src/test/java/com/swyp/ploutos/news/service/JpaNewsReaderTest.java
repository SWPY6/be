package com.swyp.ploutos.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.news.RelatedNews;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
@Import(JpaNewsReader.class)
class JpaNewsReaderTest {

    /** 직전 거래일(9/3) 장 마감부터 당일(9/4) 마감까지. 명세의 시간 창과 같은 모양이다. */
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 3, 15, 30);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 9, 4, 15, 30);

    private static final long HYUNDAI = 10L;
    private static final long KIA = 20L;
    private static final long OTHER_STOCK = 99L;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private NewsReader newsReader;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 같은_뉴스가_여러_종목에_걸려도_한_번만_읽는다() {
        // given 자동차 수출 뉴스가 현대차와 기아 둘 다에 연결돼 있다
        insertNews(1L, "자동차 수출 증가 발표", "산업통상자원부", LocalDateTime.of(2026, 9, 4, 9, 0));
        link(1L, HYUNDAI, 1L);
        link(2L, KIA, 1L);

        // when
        List<RelatedNews> found = newsReader.readByStockIds(List.of(HYUNDAI, KIA), FROM, TO);

        // then
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().title()).isEqualTo("자동차 수출 증가 발표");
    }

    @Test
    void 시간_창_밖에_발표된_뉴스는_읽지_않는다() {
        // given 창 안 1건, 창보다 이른 1건, 창보다 늦은 1건
        insertNews(1L, "창 안", "연합뉴스", LocalDateTime.of(2026, 9, 4, 9, 0));
        insertNews(2L, "직전 거래일 장중", "연합뉴스", LocalDateTime.of(2026, 9, 3, 10, 0));
        insertNews(3L, "당일 마감 이후", "연합뉴스", LocalDateTime.of(2026, 9, 4, 16, 0));
        link(1L, HYUNDAI, 1L);
        link(2L, HYUNDAI, 2L);
        link(3L, HYUNDAI, 3L);

        // when
        List<RelatedNews> found = newsReader.readByStockIds(List.of(HYUNDAI), FROM, TO);

        // then
        assertThat(found).extracting(RelatedNews::title).containsExactly("창 안");
    }

    @Test
    void 발표_시각_내림차순으로_읽는다() {
        // given
        insertNews(1L, "오전", "연합뉴스", LocalDateTime.of(2026, 9, 4, 9, 0));
        insertNews(2L, "오후", "연합뉴스", LocalDateTime.of(2026, 9, 4, 14, 0));
        insertNews(3L, "점심", "연합뉴스", LocalDateTime.of(2026, 9, 4, 11, 0));
        link(1L, HYUNDAI, 1L);
        link(2L, HYUNDAI, 2L);
        link(3L, HYUNDAI, 3L);

        // when
        List<RelatedNews> found = newsReader.readByStockIds(List.of(HYUNDAI), FROM, TO);

        // then 최신이 먼저다. 화면은 이 중 앞의 한 건만 쓴다
        assertThat(found).extracting(RelatedNews::title).containsExactly("오후", "점심", "오전");
    }

    @Test
    void 요청하지_않은_종목의_뉴스는_읽지_않는다() {
        // given
        insertNews(1L, "현대차 뉴스", "연합뉴스", LocalDateTime.of(2026, 9, 4, 9, 0));
        insertNews(2L, "무관한 종목 뉴스", "연합뉴스", LocalDateTime.of(2026, 9, 4, 10, 0));
        link(1L, HYUNDAI, 1L);
        link(2L, OTHER_STOCK, 2L);

        // when
        List<RelatedNews> found = newsReader.readByStockIds(List.of(HYUNDAI), FROM, TO);

        // then
        assertThat(found).extracting(RelatedNews::title).containsExactly("현대차 뉴스");
    }

    @Test
    void 종목_목록이_비면_빈_목록을_돌려준다() {
        // given 산업에 매핑된 종목이 아직 없는 경우. 조회하면 `in ()` 으로 SQL 오류가 난다
        insertNews(1L, "자동차 수출 증가 발표", "산업통상자원부", LocalDateTime.of(2026, 9, 4, 9, 0));
        link(1L, HYUNDAI, 1L);

        // when
        List<RelatedNews> found = newsReader.readByStockIds(List.of(), FROM, TO);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void 연결된_뉴스가_없으면_빈_목록을_돌려준다() {
        // when 뉴스를 한 건도 넣지 않았다
        List<RelatedNews> found = newsReader.readByStockIds(List.of(HYUNDAI), FROM, TO);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void 제목_출처_발표시각_링크를_그대로_읽는다() {
        // given
        LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 4, 9, 0);
        insertNews(1L, "자동차 수출 증가 발표", "산업통상자원부", publishedAt);
        link(1L, HYUNDAI, 1L);

        // when
        RelatedNews news = newsReader.readByStockIds(List.of(HYUNDAI), FROM, TO).getFirst();

        // then
        assertThat(news.newsId()).isEqualTo(1L);
        assertThat(news.title()).isEqualTo("자동차 수출 증가 발표");
        assertThat(news.publisher()).isEqualTo("산업통상자원부");
        assertThat(news.publishedAt()).isEqualTo(publishedAt);
        assertThat(news.url()).isEqualTo("https://example.com/news/1");
    }

    /**
     * {@code News} 엔티티에 공개 생성자가 없어 네이티브로 넣는다. 이 모듈은 다른 담당자의 것이므로
     * 엔티티를 수정하지 않는다. 검증 대상이 JPQL 쿼리이므로 엔티티 생성 경로에 기대지 않는 편이 낫다.
     */
    private void insertNews(long newsId, String title, String publisher, LocalDateTime publishedAt) {
        entityManager.getEntityManager().createNativeQuery("""
                        insert into news
                            (news_id, title, url, publisher, summary, category,
                             published_at, created_at, updated_at)
                        values (?1, ?2, ?3, ?4, '예시 요약', 'STOCK', ?5, ?6, ?7)
                        """)
                .setParameter(1, newsId)
                .setParameter(2, title)
                .setParameter(3, "https://example.com/news/" + newsId)
                .setParameter(4, publisher)
                .setParameter(5, publishedAt)
                .setParameter(6, publishedAt)
                .setParameter(7, publishedAt)
                .executeUpdate();
    }

    private void link(long stockNewsId, long stockId, long newsId) {
        entityManager.getEntityManager().createNativeQuery("""
                        insert into stock_news (stock_news_id, stock_id, news_id)
                        values (?1, ?2, ?3)
                        """)
                .setParameter(1, stockNewsId)
                .setParameter(2, stockId)
                .setParameter(3, newsId)
                .executeUpdate();
    }
}
