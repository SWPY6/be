package com.swyp.ploutos.market.price.repository;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.price.MarketDailyPrices;
import com.swyp.ploutos.stock.price.DailyPrice;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class MarketDailyPriceRepositoryTest {

    private static final MarketIndicator INDICATOR = MarketIndicator.KOSPI;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private MarketDailyPriceRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 같은_지표_같은_거래일은_중복_저장할_수_없다() {
        // given
        LocalDate tradeAt = LocalDate.of(2026, 9, 29);
        repository.saveAndFlush(new MarketDailyPrices(INDICATOR, price(tradeAt, "6870.81")));

        // when & then
        assertThatThrownBy(() ->
                repository.saveAndFlush(new MarketDailyPrices(INDICATOR, price(tradeAt, "6900.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 지표_컬럼은_varchar다() {
        // given 네이티브 enum 이면 지표를 추가할 때마다 ALTER TABLE 이 필요해진다

        // when
        Object dataType = entityManager.createNativeQuery("""
                        select data_type from information_schema.columns
                        where table_schema = database()
                          and table_name = 'market_daily_prices'
                          and column_name = 'indicator'
                        """)
                .getSingleResult();

        // then
        assertThat(dataType.toString()).isEqualToIgnoringCase("varchar");
    }

    @Test
    void 구간을_조회하면_거래일_오름차순으로_반환한다() {
        // given 다른 지표의 행도 함께 저장한다
        repository.saveAll(List.of(
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 25), "6950.00")),
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 23), "7080.92")),
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 24), "7000.00")),
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 29), "6870.81")),
                new MarketDailyPrices(MarketIndicator.KOSDAQ, price(LocalDate.of(2026, 9, 24), "849.80"))
        ));

        // when
        List<MarketDailyPrices> found = repository.findByIndicatorAndTradeAtBetweenOrderByTradeAtAsc(
                INDICATOR, LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 25));

        // then 요청한 지표의 구간만 오름차순으로 온다. 같은 날짜의 코스닥 행은 섞이지 않는다
        assertThat(found)
                .extracting(p -> p.toDailyPrice().tradeAt())
                .containsExactly(
                        LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25));
    }

    @Test
    void 환율은_소수_넷째자리까지_저장된다() {
        // given 환율은 KIS에서 소수 넷째 자리로 온다
        repository.save(new MarketDailyPrices(
                MarketIndicator.USD_KRW, price(LocalDate.of(2026, 9, 28), "1359.9000")));

        // when DB를 실제로 거쳐 다시 읽는다
        entityManager.flush();
        entityManager.clear();
        List<MarketDailyPrices> found = repository.findByIndicatorAndTradeAtBetweenOrderByTradeAtAsc(
                MarketIndicator.USD_KRW, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 28));

        // then 자릿수가 깎이지 않는다
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().toDailyPrice().close()).isEqualTo(new BigDecimal("1359.9000"));
    }

    @Test
    void 가장_이른_거래일과_가장_늦은_거래일을_조회한다() {
        // given
        repository.saveAll(List.of(
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 24), "7000.00")),
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 23), "7080.92")),
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 29), "6870.81")),
                new MarketDailyPrices(MarketIndicator.KOSDAQ, price(LocalDate.of(2026, 9, 1), "820.00"))
        ));

        // when
        LocalDate earliest = repository.findEarliestTradeAt(INDICATOR).orElseThrow();
        LocalDate latest = repository.findLatestTradeAt(INDICATOR).orElseThrow();

        // then 다른 지표의 거래일은 섞이지 않는다
        assertThat(earliest).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(latest).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    void 저장된_일봉이_없으면_가장_이른_거래일과_가장_늦은_거래일이_비어_있다() {
        // given 저장된 일봉이 없다

        // when & then
        assertThat(repository.findEarliestTradeAt(INDICATOR)).isEmpty();
        assertThat(repository.findLatestTradeAt(INDICATOR)).isEmpty();
    }

    @Test
    void 구간_안에_저장된_거래일_목록을_조회한다() {
        // given
        repository.saveAll(List.of(
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 23), "7080.92")),
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 25), "6950.00")),
                new MarketDailyPrices(INDICATOR, price(LocalDate.of(2026, 9, 29), "6870.81"))
        ));

        // when
        List<LocalDate> dates = repository.findTradeAtsBetween(
                INDICATOR, LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 25));

        // then
        assertThat(dates)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 25));
    }

    private static DailyPrice price(LocalDate tradeAt, String close) {
        BigDecimal value = new BigDecimal(close);
        return new DailyPrice(tradeAt, value, value, value, value, 0L);
    }
}
