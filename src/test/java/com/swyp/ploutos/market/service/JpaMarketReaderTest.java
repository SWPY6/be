package com.swyp.ploutos.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.TradingSession;
import com.swyp.ploutos.market.Markets;
import com.swyp.ploutos.market.repository.MarketRepository;

@ExtendWith(MockitoExtension.class)
class JpaMarketReaderTest {

    private static final Long MARKET_ID = 10L;

    @Mock
    private MarketRepository marketRepository;

    @InjectMocks
    private JpaMarketReader marketReader;

    @Test
    void 시장이_있으면_반환한다() {
        // given
        Markets market = new Markets(MarketCode.NASDAQ, Country.US, TradingSession.REGULAR, Currency.USD);
        given(marketRepository.findById(MARKET_ID)).willReturn(Optional.of(market));

        // when
        Markets result = marketReader.read(MARKET_ID);

        // then
        assertThat(result.code()).isEqualTo(MarketCode.NASDAQ);
        assertThat(result.country()).isEqualTo(Country.US);
    }

    @Test
    void 시장이_없으면_정합성_오류를_던진다() {
        // given
        given(marketRepository.findById(MARKET_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> marketReader.read(MARKET_ID))
                .isInstanceOf(IllegalStateException.class);
    }
}
