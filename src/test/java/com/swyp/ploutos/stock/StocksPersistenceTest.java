package com.swyp.ploutos.stock;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.market.Markets;

/**
 * 종목 마스터 적재가 비워 두는 열이 DB 에서도 비어도 되는지 본다.
 *
 * <p>테스트 스키마는 {@code ddl-auto=create}로 엔티티에서 생성된다 — 그래서 이 테스트는
 * {@code @Column(nullable = false)}를 한 곳이라도 덜 지웠을 때 저장에서 걸린다.
 * {@code JpaStockReaderTest}는 {@code @Mock} 리포지토리를 쓰는 단위 테스트라 이것을
 * 증명하지 못한다 — 명세: {@code docs/SPEC-stock-master.md}「테스트 전략」.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class StocksPersistenceTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private TestEntityManager em;

    @Test
    void 대표자명과_상장일과_유동주식수가_없어도_종목을_저장하고_읽는다() {
        // given 해외 종목은 KIS 종목정보 파일에 이 셋이 없다. 가짜로 채우지 않고 비워 둔다
        Markets market = em.persist(new Markets(MarketCode.NASDAQ, Country.US,
                TradingSession.REGULAR, Currency.USD));
        Stocks saved = em.persist(new Stocks(market.marketId(), "AAPL", "애플", null,
                StockStatus.ACTIVE, Exchange.NASDAQ, null, null, null));
        em.flush();

        // when 영속성 컨텍스트를 비워 DB 에서 다시 읽게 한다. 비우지 않으면 방금 만든 객체를
        // 메모리에서 돌려주어 DB 를 한 번도 거치지 않는다
        em.clear();
        Stocks found = em.find(Stocks.class, saved.stockId());

        // then
        assertThat(found.ceo()).isNull();
        assertThat(found.listedAt()).isNull();
        assertThat(found.floatShares()).isNull();
        assertThat(found.name()).isEqualTo("애플");
    }
}
