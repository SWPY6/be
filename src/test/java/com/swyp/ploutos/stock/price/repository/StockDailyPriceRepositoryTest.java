package com.swyp.ploutos.stock.price.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.StockDailyPrices;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class StockDailyPriceRepositoryTest {

    private static final Long STOCK_ID = 1L;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private StockDailyPriceRepository repository;

    @Test
    void 같은_종목_같은_거래일은_중복_저장할_수_없다() {
        // given
        LocalDate tradeAt = LocalDate.of(2026, 8, 11);
        repository.saveAndFlush(new StockDailyPrices(STOCK_ID, price(tradeAt, 100)));

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(new StockDailyPrices(STOCK_ID, price(tradeAt, 200))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 구간을_조회하면_거래일_오름차순으로_반환한다() {
        // given
        repository.saveAll(List.of(
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 13), 3)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 11), 1)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 12), 2)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 20), 9)),
                new StockDailyPrices(2L, price(LocalDate.of(2026, 8, 12), 7))
        ));

        // when
        List<StockDailyPrices> found = repository.findByStockIdAndTradeAtBetweenOrderByTradeAtAsc(
                STOCK_ID, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 13));

        // then
        assertThat(found)
                .extracting(p -> p.toDailyPrice().volume())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void 최근_N건을_조회한다() {
        // given
        repository.saveAll(List.of(
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 11), 1)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 12), 2)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 13), 3))
        ));

        // when
        List<StockDailyPrices> latest = repository.findByStockIdOrderByTradeAtDesc(STOCK_ID, Limit.of(2));

        // then
        assertThat(latest)
                .extracting(p -> p.toDailyPrice().volume())
                .containsExactly(3L, 2L);
    }

    @Test
    void 가장_이른_거래일과_가장_늦은_거래일을_조회한다() {
        // given
        repository.saveAll(List.of(
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 12), 2)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 11), 1)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 13), 3))
        ));

        // when
        LocalDate earliest = repository.findEarliestTradeAt(STOCK_ID).orElseThrow();
        LocalDate latest = repository.findLatestTradeAt(STOCK_ID).orElseThrow();

        // then
        assertThat(earliest).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(latest).isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    void 저장된_일봉이_없으면_가장_이른_거래일과_가장_늦은_거래일이_비어_있다() {
        // when & then
        assertThat(repository.findEarliestTradeAt(STOCK_ID)).isEmpty();
        assertThat(repository.findLatestTradeAt(STOCK_ID)).isEmpty();
    }

    @Test
    void 구간_안에_저장된_거래일_목록을_조회한다() {
        // given
        repository.saveAll(List.of(
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 11), 1)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 13), 3)),
                new StockDailyPrices(STOCK_ID, price(LocalDate.of(2026, 8, 20), 9))
        ));

        // when
        List<LocalDate> dates = repository.findTradeAtsBetween(STOCK_ID, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 13));

        // then
        assertThat(dates).containsExactlyInAnyOrder(LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 13));
    }

    private static DailyPrice price(LocalDate tradeAt, long volume) {
        return new DailyPrice(tradeAt, new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"), new BigDecimal("105"), volume);
    }
}
