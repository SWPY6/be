package com.swyp.ploutos.market.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.price.IndicatorDailyPriceSyncPolicy;
import com.swyp.ploutos.market.price.MarketDailyPrices;
import com.swyp.ploutos.market.price.repository.MarketDailyPriceRepository;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.DailyPrices;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class MarketDailyPriceServiceTest {

    private static final MarketIndicator INDICATOR = MarketIndicator.KOSPI;
    // 2026-08-12 수요일 09:00 KST. 마지막 거래일 추정치는 08-11(화)다.
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
    private MarketDailyPriceRepository repository;

    private FakeIndicatorDailyPriceProvider provider;
    private IndicatorDailyPriceReader reader;

    @BeforeEach
    void setUp() {
        provider = new FakeIndicatorDailyPriceProvider();
        reader = new MarketDailyPriceService(repository, provider, new IndicatorDailyPriceSyncPolicy(CLOCK));
    }

    @Test
    void 저장된_봉이_구간을_덮지_못하면_외부에서_받아_저장한다() {
        // given 저장된 봉이 없다
        provider.willReturn(List.of(
                price(YESTERDAY.minusDays(2)), price(YESTERDAY.minusDays(1)), price(YESTERDAY)));

        // when
        DailyPrices found = reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // then 거래일 오름차순으로 돌려주고, 외부는 한 번만 부른다
        assertThat(found.values()).extracting(DailyPrice::tradeAt)
                .containsExactly(YESTERDAY.minusDays(2), YESTERDAY.minusDays(1), YESTERDAY);
        assertThat(repository.count()).isEqualTo(3);
        assertThat(provider.calls).hasSize(1);
    }

    @Test
    void 저장된_봉이_구간을_덮으면_외부를_호출하지_않는다() {
        // given 시작일 이전부터 마지막 거래일까지 저장돼 있다
        repository.saveAll(List.of(
                new MarketDailyPrices(INDICATOR, price(FROM.minusDays(1))),
                new MarketDailyPrices(INDICATOR, price(YESTERDAY))
        ));

        // when
        DailyPrices found = reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // then
        assertThat(found.values()).extracting(DailyPrice::tradeAt).containsExactly(YESTERDAY);
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void 앞이_비었으면_요청_시작일부터_받는다() {
        // given 저장된 봉이 없다. 지표는 거래량을 다루지 않아 여유 기간이 필요 없다
        provider.willReturn(List.of(price(YESTERDAY)));

        // when
        reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // then 주식과 달리 30일을 더 당기지 않는다
        assertThat(provider.calls.getFirst().from()).isEqualTo(FROM);
        assertThat(provider.calls.getFirst().to()).isEqualTo(YESTERDAY);
    }

    @Test
    void 끝만_낡았으면_저장된_마지막_거래일부터_받는다() {
        // given 앞은 덮지만 끝이 하루 낡았다
        LocalDate storedLatest = LocalDate.of(2026, 8, 10);
        repository.saveAll(List.of(
                new MarketDailyPrices(INDICATOR, price(FROM.minusDays(1))),
                new MarketDailyPrices(INDICATOR, price(storedLatest))
        ));
        provider.willReturn(List.of(price(YESTERDAY)));

        // when
        reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // then 구간 전체를 다시 받지 않고 낡은 끝만 채운다
        assertThat(provider.calls.getFirst().from()).isEqualTo(storedLatest);
        assertThat(provider.calls.getFirst().to()).isEqualTo(YESTERDAY);
    }

    @Test
    void 당일_봉은_저장하지_않는다() {
        // given 외부가 진행 중인 날의 행을 함께 준다
        provider.willReturn(List.of(price(YESTERDAY), price(TODAY)));

        // when
        reader.findBetween(INDICATOR, FROM, TODAY);

        // then
        assertThat(repository.findTradeAtsBetween(INDICATOR, FROM, TODAY)).containsExactly(YESTERDAY);
    }

    @Test
    void 이미_있는_거래일은_다시_저장하지_않는다() {
        // given 저장된 행과 같은 거래일을 외부가 다른 값으로 준다
        LocalDate stored = YESTERDAY.minusDays(1);
        repository.save(new MarketDailyPrices(INDICATOR, price(stored, "999")));
        provider.willReturn(List.of(price(stored, "1"), price(YESTERDAY, "2")));

        // when
        DailyPrices found = reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // then 기존 행이 덮이지 않는다
        assertThat(found.values()).hasSize(2);
        assertThat(found.values().getFirst().close()).isEqualByComparingTo("999");
        assertThat(found.values().getLast().close()).isEqualByComparingTo("2");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void 외부_호출이_실패하면_예외를_전파한다() {
        // given
        provider.willThrow(new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE));

        // when & then
        assertThatThrownBy(() -> reader.findBetween(INDICATOR, FROM, YESTERDAY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MARKET_DATA_UNAVAILABLE);
        assertThat(repository.count()).isZero();
    }

    @Test
    void 같은_날_같은_구간을_다시_요청하면_외부를_부르지_않는다() {
        // given 외부가 빈 목록을 줘서 DB가 계속 비어 있다. 덮음 판정은 두 번째에도 거짓이다
        provider.willReturn(List.of());
        reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // when
        DailyPrices found = reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // then 시도권이 없으면 기다리지 않고 저장된 행만 돌려준다
        assertThat(provider.calls).hasSize(1);
        assertThat(found.isEmpty()).isTrue();
    }

    @Test
    void 거래량은_0으로_읽는다() {
        // given 지표에는 거래량이 없다
        provider.willReturn(List.of(price(YESTERDAY)));

        // when
        DailyPrices found = reader.findBetween(INDICATOR, FROM, YESTERDAY);

        // then
        assertThat(found.values()).extracting(DailyPrice::volume).containsExactly(0L);
    }

    private static DailyPrice price(LocalDate tradeAt) {
        return price(tradeAt, "100");
    }

    private static DailyPrice price(LocalDate tradeAt, String close) {
        BigDecimal value = new BigDecimal(close);
        return new DailyPrice(tradeAt, value, value, value, value, 0L);
    }

    private static final class FakeIndicatorDailyPriceProvider implements IndicatorDailyPriceProvider {

        record Call(MarketIndicator indicator, LocalDate from, LocalDate to) {
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
        public List<DailyPrice> fetch(MarketIndicator indicator, LocalDate from, LocalDate to) {
            calls.add(new Call(indicator, from, to));
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
