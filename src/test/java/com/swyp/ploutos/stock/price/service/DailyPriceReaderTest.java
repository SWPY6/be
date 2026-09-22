package com.swyp.ploutos.stock.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

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
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.StockDailyPriceSyncPolicy;
import com.swyp.ploutos.stock.price.StockDailyPrices;
import com.swyp.ploutos.stock.price.repository.StockDailyPriceRepository;
import com.swyp.ploutos.stock.Stocks;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class DailyPriceReaderTest {

    // 2026-08-12 수요일 09:00 KST. 어제(08-11 화)가 마지막 거래일이다.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate FROM = LocalDate.of(2026, 7, 12);

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

    @Autowired
    private TestEntityManager em;

    private Long stockId;
    private FakeDailyPriceProvider provider;
    private DailyPriceReader reader;

    @BeforeEach
    void setUp() {
        Markets market = em.persist(new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW));
        Stocks stock = em.persist(new Stocks(market.marketId(), "005930", "삼성전자", null, StockStatus.ACTIVE,
                Exchange.KRX, 1L, "대표", LocalDate.of(2000, 1, 1)));
        stockId = stock.stockId();
        provider = new FakeDailyPriceProvider();
        reader = new StockDailyPriceService(id -> new StockWithMarket(stock, market), repository, provider,
                new StockDailyPriceSyncPolicy(CLOCK));
    }

    @Test
    void 저장된_봉이_구간을_덮지_못하면_외부에서_받아_저장한다() {
        // given
        provider.willReturn(List.of(price(YESTERDAY.minusDays(2)), price(YESTERDAY.minusDays(1)), price(YESTERDAY)));

        // when
        DailyPrices found = reader.findBetween(stockId, FROM, YESTERDAY);

        // then
        assertThat(found.values()).extracting(DailyPrice::tradeAt)
                .containsExactly(YESTERDAY.minusDays(2), YESTERDAY.minusDays(1), YESTERDAY);
        assertThat(repository.count()).isEqualTo(3);
        assertThat(provider.calls).hasSize(1);
        assertThat(provider.calls.getFirst().from()).isEqualTo(FROM.minusDays(StockDailyPriceService.FETCH_MARGIN_DAYS));
        assertThat(provider.calls.getFirst().to()).isEqualTo(YESTERDAY);
    }

    @Test
    void 저장된_봉이_구간을_덮으면_외부를_호출하지_않는다() {
        // given
        repository.saveAll(List.of(
                new StockDailyPrices(stockId, price(FROM.minusDays(1))),
                new StockDailyPrices(stockId, price(YESTERDAY))
        ));

        // when
        DailyPrices found = reader.findBetween(stockId, FROM, YESTERDAY);

        // then
        assertThat(found.values()).extracting(DailyPrice::tradeAt).containsExactly(YESTERDAY);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 당일_봉은_저장하지_않는다() {
        // given
        provider.willReturn(List.of(price(YESTERDAY), price(TODAY)));

        // when
        reader.findBetween(stockId, FROM, TODAY);

        // then
        assertThat(repository.findTradeAtsBetween(stockId, FROM, TODAY)).containsExactly(YESTERDAY);
    }

    @Test
    void 이미_있는_거래일은_다시_저장하지_않는다() {
        // given
        LocalDate stored = YESTERDAY.minusDays(1);
        repository.save(new StockDailyPrices(stockId, price(stored, 999)));
        provider.willReturn(List.of(price(stored, 1), price(YESTERDAY, 2)));

        // when
        DailyPrices found = reader.findBetween(stockId, FROM, YESTERDAY);

        // then
        assertThat(found.values()).extracting(DailyPrice::volume).containsExactly(999L, 2L);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void 외부_호출이_실패하면_예외를_전파한다() {
        // given
        provider.willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when & then
        assertThatThrownBy(() -> reader.findBetween(stockId, FROM, YESTERDAY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThat(repository.count()).isZero();
    }

    @Test
    void 최근_20거래일_거래량의_평균을_반환한다() {
        // given
        // 어제까지 40일치(동기화 판정 구간 today-30을 덮는다), 거래량은 오래된 순으로 1..40.
        // 최근 20일 = 21..40, 평균 30.5 → 30
        List<StockDailyPrices> rows = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> new StockDailyPrices(stockId, price(YESTERDAY.minusDays(40 - i), i)))
                .toList();
        repository.saveAll(rows);

        // when
        Optional<Long> average = reader.averageVolume20d(stockId);

        // then
        assertThat(average).contains(30L);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 거래일이_20일_미만이면_평균은_없다() {
        // given
        provider.willReturn(List.of(price(YESTERDAY.minusDays(1)), price(YESTERDAY)));

        // when
        Optional<Long> average = reader.averageVolume20d(stockId);

        // then
        assertThat(average).isEmpty();
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return price(tradeAt, 100);
    }

    private static DailyPrice price(LocalDate tradeAt, long volume) {
        return new DailyPrice(tradeAt, BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ONE, BigDecimal.TWO, volume);
    }

    private static final class FakeDailyPriceProvider implements DailyPriceProvider {

        record Call(LocalDate from, LocalDate to) {
        }

        private final List<Call> calls = new ArrayList<>();
        private List<DailyPrice> result = List.of();
        private RuntimeException failure;

        void willReturn(List<DailyPrice> prices) {
            this.result = prices;
        }

        void willThrow(RuntimeException exception) {
            this.failure = exception;
        }

        @Override
        public List<DailyPrice> fetch(StockWithMarket stock, LocalDate from, LocalDate to) {
            calls.add(new Call(from, to));
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
