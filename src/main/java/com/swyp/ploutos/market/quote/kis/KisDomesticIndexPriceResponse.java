package com.swyp.ploutos.market.quote.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.amount;
import static com.swyp.ploutos.external.kis.KisNumbers.requiredAmount;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
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
     *
     * <p>현재값과 전일 대비는 없으면 시세를 만들 수 없다. 전일 대비를 0으로 읽으면
     * 전일 종가가 현재값과 같아져 등락률이 조용히 0이 된다.
     * 시가·고가·저가는 개장 전에 비어 올 수 있어 0으로 읽는다 — 카드에 나가지 않는 값이다.
     */
    IndicatorQuote toQuote(MarketIndicator indicator, OffsetDateTime valueAt) {
        if (output == null) {
            throw new BusinessException(ErrorCode.MARKET_DATA_UNAVAILABLE);
        }
        BigDecimal value = requiredAmount(output.value(), "bstp_nmix_prpr");
        return new IndicatorQuote(
                indicator,
                value,
                value.subtract(requiredAmount(output.previousChange(), "bstp_nmix_prdy_vrss")),
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
