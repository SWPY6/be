package com.swyp.ploutos.stock.summary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.summary.StockSummary;

class StockSummaryServiceTest {

    private static final Long DOMESTIC_ID = 1L;
    private static final Long US_ID = 2L;
    private static final Long NO_LOGO_ID = 3L;
    private static final Long MISSING_ID = 99L;

    private static final Markets KOSPI = new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW);
    private static final Markets NASDAQ = new Markets(MarketCode.NASDAQ, Country.US, TradingSession.REGULAR, Currency.USD);

    private final Map<Long, StockWithMarket> stocks = Map.of(
            DOMESTIC_ID, new StockWithMarket(stock("005930", "삼성전자", "https://logo/005930.png", Exchange.KRX), KOSPI),
            US_ID, new StockWithMarket(stock("AAPL", "애플", "https://logo/AAPL.png", Exchange.NASDAQ), NASDAQ),
            NO_LOGO_ID, new StockWithMarket(stock("000660", "SK하이닉스", null, Exchange.KRX), KOSPI));

    private final IndustryReader industryReader = mock(IndustryReader.class);

    private final StockSummaryService service = new StockSummaryService(id -> {
        StockWithMarket stock = stocks.get(id);
        if (stock == null) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        }
        return stock;
    }, industryReader);

    @Test
    void 국내_종목은_KR_KRW_서울_시간대로_돌려준다() {
        // when
        StockSummary summary = service.read(DOMESTIC_ID);

        // then
        assertThat(summary.name()).isEqualTo("삼성전자");
        assertThat(summary.ticker()).isEqualTo("005930");
        assertThat(summary.logoUrl()).isEqualTo("https://logo/005930.png");
        assertThat(summary.country()).isEqualTo(Country.KR);
        assertThat(summary.currency()).isEqualTo(Currency.KRW);
        assertThat(summary.timezone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 미국_종목은_US_USD_뉴욕_시간대로_돌려준다() {
        // when
        StockSummary summary = service.read(US_ID);

        // then
        assertThat(summary.ticker()).isEqualTo("AAPL");
        assertThat(summary.country()).isEqualTo(Country.US);
        assertThat(summary.currency()).isEqualTo(Currency.USD);
        assertThat(summary.timezone()).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void 티커의_선행_0을_보존한다() {
        // when
        StockSummary summary = service.read(NO_LOGO_ID);

        // then
        assertThat(summary.ticker()).isEqualTo("000660");
    }

    @Test
    void 로고가_없으면_logoUrl은_null이다() {
        // when
        StockSummary summary = service.read(NO_LOGO_ID);

        // then
        assertThat(summary.logoUrl()).isNull();
        assertThat(summary.name()).isEqualTo("SK하이닉스");
    }

    @Test
    void 산업이_여러_개면_산업_모듈이_정렬한_순서대로_코드를_담는다() {
        // given
        given(industryReader.readByStockId(DOMESTIC_ID)).willReturn(List.of(
                new Industries(1L, IndustryCode.AUTOMOBILE),
                new Industries(2L, IndustryCode.CHEMICAL)));

        // when
        StockSummary summary = service.read(DOMESTIC_ID);

        // then
        assertThat(summary.industries()).containsExactly(IndustryCode.AUTOMOBILE, IndustryCode.CHEMICAL);
    }

    @Test
    void 연결된_산업이_없으면_industries는_빈_목록이다() {
        // given
        given(industryReader.readByStockId(DOMESTIC_ID)).willReturn(List.of());

        // when
        StockSummary summary = service.read(DOMESTIC_ID);

        // then
        assertThat(summary.industries()).isEmpty();
    }

    @Test
    void 없는_종목이면_STOCK_NOT_FOUND를_던진다() {
        // when & then
        assertThatThrownBy(() -> service.read(MISSING_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    private static Stocks stock(String ticker, String name, String imgUrl, Exchange exchange) {
        return new Stocks(1L, ticker, name, imgUrl, StockStatus.ACTIVE, exchange, 1L, "대표", LocalDate.of(2000, 1, 1));
    }
}
