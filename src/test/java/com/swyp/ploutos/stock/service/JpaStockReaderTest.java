package com.swyp.ploutos.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.util.List;
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

    @Test
    void 여러_종목을_읽어도_시장은_한_번만_읽는다() {
        // given 같은 시장의 종목 둘
        given(stockRepository.findAllById(List.of(1L, 2L)))
                .willReturn(List.of(stock("005930", "삼성전자"), stock("000660", "SK하이닉스")));
        given(marketReader.readAll()).willReturn(List.of(market()));

        // when
        List<StockWithMarket> result = stockReader.readAll(List.of(1L, 2L));

        // then 종목마다 시장을 읽으면 조회가 종목 수만큼 나간다
        assertThat(result).extracting(StockWithMarket::ticker)
                .containsExactly("005930", "000660");
        then(marketReader).should(times(1)).readAll();
    }

    @Test
    void 종목코드로_읽으면_같은_코드가_여럿이어도_모두_돌려준다() {
        // given 한 종목이 두 시장에 속해 있다
        given(stockRepository.findByTickerIn(List.of("AAPL")))
                .willReturn(List.of(stock("AAPL", "애플"), stock("AAPL", "애플")));
        given(marketReader.readAll()).willReturn(List.of(market()));

        // when
        List<StockWithMarket> result = stockReader.readAllByTickers(List.of("AAPL"));

        // then 어느 것을 쓸지는 호출자가 정한다
        assertThat(result).hasSize(2);
    }

    @Test
    void 시장을_찾지_못한_종목은_결과에서_뺀다() {
        // given 종목의 시장이 목록에 없다
        given(stockRepository.findAllById(List.of(1L))).willReturn(List.of(stock("005930", "삼성전자")));
        given(marketReader.readAll()).willReturn(List.of());

        // when
        List<StockWithMarket> result = stockReader.readAll(List.of(1L));

        // then 시장 없이 종목을 만들 수 없다
        assertThat(result).isEmpty();
    }

    @Test
    void 식별자가_비어_있으면_조회하지_않는다() {
        // given

        // when
        List<StockWithMarket> result = stockReader.readAll(List.of());

        // then
        assertThat(result).isEmpty();
        then(stockRepository).should(never()).findAllById(any());
    }

    private static Stocks stock(String ticker, String name) {
        return new Stocks(MARKET_ID, ticker, name, null, StockStatus.ACTIVE, Exchange.KRX,
                1L, "대표", LocalDate.of(2000, 1, 1));
    }

    /** 식별자는 DB가 정하므로 직접 만든 엔티티에는 없다. 조인 키라서 여기서 채워 준다. */
    private static Markets market() {
        return new Markets(MarketCode.KOSPI, Country.KR, TradingSession.REGULAR, Currency.KRW) {
            @Override
            public Long marketId() {
                return MARKET_ID;
            }
        };
    }
}
