package com.swyp.ploutos.industry.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.enums.Country;

/**
 * 산업에 국내·해외 종목이 함께 매핑된 상태를 만들고 국가별 조회를 확인한다.
 *
 * <p>엔티티에 public 생성자가 없어 네이티브 INSERT 로 넣는다. 매핑에는 국가가 없고
 * {@code stocks → markets} 에만 있어, 이 조인이 맞는지는 실제 SQL 로만 증명된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class StockIndustryRepositoryTest {

    private static final Long INDUSTRY_ID = 1L;
    private static final Long OTHER_INDUSTRY_ID = 2L;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private StockIndustryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    /** 자동차 산업에 국내 2개(1·2)와 해외 2개(3·4)를 매핑한다. 시드와 같은 모양이다. */
    @BeforeEach
    void setUp() {
        insertMarket(100L, "KOSPI", Country.KR, "KRW");
        insertMarket(200L, "SP500", Country.US, "USD");
        insertStock(1L, 100L, "005380");
        insertStock(2L, 100L, "000270");
        insertStock(3L, 200L, "F");
        insertStock(4L, 200L, "GM");
        insertMapping(1L, INDUSTRY_ID, 1L);
        insertMapping(2L, INDUSTRY_ID, 2L);
        insertMapping(3L, INDUSTRY_ID, 3L);
        insertMapping(4L, INDUSTRY_ID, 4L);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 국내로_조회하면_국내_종목만_돌려준다() {
        // when
        List<Long> stockIds = repository.findStockIdsByIndustryIdAndCountry(INDUSTRY_ID, Country.KR);

        // then
        assertThat(stockIds).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 해외로_조회하면_해외_종목만_돌려준다() {
        // when
        List<Long> stockIds = repository.findStockIdsByIndustryIdAndCountry(INDUSTRY_ID, Country.US);

        // then
        assertThat(stockIds).containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    void 국가를_거르지_않으면_두_나라가_함께_나온다() {
        // when 기존 메서드다. 갱신 배치는 전부를 받아 스스로 나누므로 이 동작이 맞다
        List<Long> stockIds = repository.findStockIdsByIndustryId(INDUSTRY_ID);

        // then 조회 API 가 이것을 쓰면 다른 나라 종목이 섞인다
        assertThat(stockIds).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void 그_국가의_종목이_없으면_빈_목록이다() {
        // given 국내 종목만 매핑된 산업
        insertMapping(5L, OTHER_INDUSTRY_ID, 1L);
        entityManager.flush();

        // when
        List<Long> stockIds = repository.findStockIdsByIndustryIdAndCountry(
                OTHER_INDUSTRY_ID, Country.US);

        // then
        assertThat(stockIds).isEmpty();
    }

    @Test
    void 매핑이_없는_산업은_빈_목록이다() {
        // when
        List<Long> stockIds = repository.findStockIdsByIndustryIdAndCountry(99L, Country.KR);

        // then
        assertThat(stockIds).isEmpty();
    }

    private void insertMarket(Long marketId, String code, Country country, String currency) {
        entityManager.getEntityManager().createNativeQuery("""
                        insert into markets (market_id, code, country, currency, trading_session,
                                             created_at, updated_at)
                        values (?, ?, ?, ?, 'REGULAR', now(6), now(6))
                        """)
                .setParameter(1, marketId)
                .setParameter(2, code)
                .setParameter(3, country.name())
                .setParameter(4, currency)
                .executeUpdate();
    }

    private void insertStock(Long stockId, Long marketId, String ticker) {
        entityManager.getEntityManager().createNativeQuery("""
                        insert into stocks (stock_id, market_id, ticker, name, status, exchange,
                                            float_shares, ceo, listed_at, created_at, updated_at)
                        values (?, ?, ?, ?, 'ACTIVE', 'KRX', 0, '테스트', '1900-01-01', now(6), now(6))
                        """)
                .setParameter(1, stockId)
                .setParameter(2, marketId)
                .setParameter(3, ticker)
                .setParameter(4, "종목" + stockId)
                .executeUpdate();
    }

    private void insertMapping(Long stockIndustryId, Long industryId, Long stockId) {
        entityManager.getEntityManager().createNativeQuery("""
                        insert into stock_industries (stock_industry_id, industry_id, stock_id)
                        values (?, ?, ?)
                        """)
                .setParameter(1, stockIndustryId)
                .setParameter(2, industryId)
                .setParameter(3, stockId)
                .executeUpdate();
    }
}
