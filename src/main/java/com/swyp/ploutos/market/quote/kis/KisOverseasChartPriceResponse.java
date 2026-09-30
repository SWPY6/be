package com.swyp.ploutos.market.quote.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.amount;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/**
 * 해외 지수·환율 기간별 시세(FHKST03030100) 응답. 일봉 API지만 output1에 현재값이 함께 온다.
 * 이 응답에서는 output1만 쓰고 output2(일봉 목록)는 읽지 않는다.
 */
record KisOverseasChartPriceResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output1") Output output1
) implements KisResponse {

    IndicatorQuote toQuote(MarketIndicator indicator, OffsetDateTime valueAt) {
        return new IndicatorQuote(
                indicator,
                amount(output1.value()),
                amount(output1.previousClose()),
                amount(output1.open()),
                amount(output1.high()),
                amount(output1.low()),
                valueAt
        );
    }

    record Output(
            @JsonProperty("ovrs_nmix_prpr") String value,
            @JsonProperty("ovrs_nmix_prdy_clpr") String previousClose,
            @JsonProperty("ovrs_prod_oprc") String open,
            @JsonProperty("ovrs_prod_hgpr") String high,
            @JsonProperty("ovrs_prod_lwpr") String low
    ) {
    }
}
