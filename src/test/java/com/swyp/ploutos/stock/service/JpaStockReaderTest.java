package com.swyp.ploutos.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.StockStatus;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.market.service.MarketReader;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.Stocks;
import com.swyp.ploutos.stock.repository.StockRepository;
import com.swyp.ploutos.market.Markets;

@ExtendWith(MockitoExtension.class)
class JpaStockReaderTest {

    private static final Long STOCK_ID = 1L;
    private static final Long MARKET_ID = 10L;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private MarketReader marketReader;

    @InjectMocks
    private JpaStockReader stockReader;

    @Test
    void 종목이_있으면_시장과_함께_반환한다() {
        // given
        Stocks stock = new Stocks(MARKET_ID, "005930", "삼성전자", null, StockStatus.ACTIVE,
                Exchange.KRX, 5_919_637_922L, "전영현", LocalDate.of(1975, 6, 11));
        Markets market = new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW);
        given(stockRepository.findById(STOCK_ID)).willReturn(Optional.of(stock));
        given(marketReader.read(MARKET_ID)).willReturn(market);

        // when
        StockWithMarket result = stockReader.read(STOCK_ID);

        // then
        assertThat(result.stock().ticker()).isEqualTo("005930");
        assertThat(result.market().code()).isEqualTo(MarketCode.KOSPI);
        assertThat(result.stock().exchange()).isEqualTo(Exchange.KRX);
    }

    @Test
    void 종목이_없으면_STOCK_NOT_FOUND를_던진다() {
        // given
        given(stockRepository.findById(STOCK_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stockReader.read(STOCK_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }
}
