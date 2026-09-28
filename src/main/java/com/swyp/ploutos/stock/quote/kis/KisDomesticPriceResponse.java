package com.swyp.ploutos.stock.quote.kis;

import static com.swyp.ploutos.stock.quote.kis.KisNumbers.amount;
import static com.swyp.ploutos.stock.quote.kis.KisNumbers.count;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;

/** 주식현재가 시세(FHKST01010100) 응답. output이 단건 객체다. */
record KisDomesticPriceResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") Output output
) implements KisResponse {

    /** 억원을 원으로 옮기는 자릿수. */
    private static final int HUNDRED_MILLION_DIGITS = 8;

    Quote toQuote(Currency currency, OffsetDateTime priceAt, PriceTiming priceTiming) {
        return new Quote(
                amount(output.price()),
                amount(output.previousClose()),
                amount(output.open()),
                amount(output.high()),
                amount(output.low()),
                count(output.volume()),
                amount(output.tradingValue()),
                amount(output.marketCap()).movePointRight(HUNDRED_MILLION_DIGITS),
                currency,
                priceAt,
                priceTiming
        );
    }

    record Output(
            @JsonProperty("stck_prpr") String price,
            @JsonProperty("stck_sdpr") String previousClose,
            @JsonProperty("stck_oprc") String open,
            @JsonProperty("stck_hgpr") String high,
            @JsonProperty("stck_lwpr") String low,
            @JsonProperty("acml_vol") String volume,
            @JsonProperty("acml_tr_pbmn") String tradingValue,
            /** 억원 단위로 온다. */
            @JsonProperty("hts_avls") String marketCap
    ) {
    }
}
