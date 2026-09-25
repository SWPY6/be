package com.swyp.ploutos.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.market.Markets;

class StockWithMarketTest {

    private static final LocalDate LISTED_AT = LocalDate.of(2026, 7, 1);

    @Test
    void 상장일이_기준일보다_늦으면_참이다() {
        // given
        StockWithMarket stock = stock(Exchange.KRX, Country.KR, Currency.KRW);

        // when & then
        assertThat(stock.listedAfter(LISTED_AT.minusDays(1))).isTrue();
        assertThat(stock.listedAfter(LISTED_AT)).isFalse();
        assertThat(stock.listedAfter(LISTED_AT.plusDays(1))).isFalse();
    }

    @Test
    void 종목과_시장의_값을_한_단계로_노출한다() {
        // given
        StockWithMarket stock = stock(Exchange.NYSE, Country.US, Currency.USD);

        // when & then
        assertThat(stock.ticker()).isEqualTo("JPM");
        assertThat(stock.exchange()).isEqualTo(Exchange.NYSE);
        assertThat(stock.country()).isEqualTo(Country.US);
        assertThat(stock.currency()).isEqualTo(Currency.USD);
    }

    private static StockWithMarket stock(Exchange exchange, Country country, Currency currency) {
        Stocks stocks = new Stocks(1L, exchange == Exchange.KRX ? "005930" : "JPM", "종목", null,
                StockStatus.ACTIVE, exchange, 1L, "대표", LISTED_AT);
        Markets markets = new Markets(MarketCode.KOSPI, country, TradingSession.REGULAR, currency);
        return new StockWithMarket(stocks, markets);
    }
}
