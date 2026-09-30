package com.swyp.ploutos.market.quote.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.amount;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;

/** 국내업종 현재지수(FHPUP02100000) 응답. output이 단건 객체다. */
record KisDomesticIndexPriceResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") Output output
) implements KisResponse {

    /**
     * 이 응답에는 전일 종가 필드가 없어 현재값에서 전일 대비를 빼서 구한다.
     * 전일 대비에 부호가 붙어 오므로 prdy_vrss_sign은 보지 않는다.
     */
    IndicatorQuote toQuote(MarketIndicator indicator, OffsetDateTime valueAt) {
        BigDecimal value = amount(output.value());
        return new IndicatorQuote(
                indicator,
                value,
                value.subtract(amount(output.previousChange())),
                amount(output.open()),
                amount(output.high()),
                amount(output.low()),
                valueAt
        );
    }

    record Output(
            @JsonProperty("bstp_nmix_prpr") String value,
            /** 전일 대비. 부호가 붙어 온다. */
            @JsonProperty("bstp_nmix_prdy_vrss") String previousChange,
            @JsonProperty("bstp_nmix_oprc") String open,
            @JsonProperty("bstp_nmix_hgpr") String high,
            @JsonProperty("bstp_nmix_lwpr") String low
    ) {
    }
}
