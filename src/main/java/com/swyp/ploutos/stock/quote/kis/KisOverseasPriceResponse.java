package com.swyp.ploutos.stock.quote.kis;

import static com.swyp.ploutos.stock.quote.kis.KisNumbers.amount;
import static com.swyp.ploutos.stock.quote.kis.KisNumbers.count;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;

/** 해외주식 현재가상세(HHDFS76200200) 응답. 금액은 모두 현지 통화 기본 단위다. */
record KisOverseasPriceResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") Output output
) implements KisResponse {

    Quote toQuote(Currency currency, OffsetDateTime priceAt, PriceTiming priceTiming) {
        return new Quote(
                amount(output.price()),
                amount(output.previousClose()),
                amount(output.open()),
                amount(output.high()),
                amount(output.low()),
                count(output.volume()),
                amount(output.tradingValue()),
                amount(output.marketCap()),
                currency,
                priceAt,
                priceTiming
        );
    }

    record Output(
            @JsonProperty("last") String price,
            @JsonProperty("base") String previousClose,
            @JsonProperty("open") String open,
            @JsonProperty("high") String high,
            @JsonProperty("low") String low,
            @JsonProperty("tvol") String volume,
            @JsonProperty("tamt") String tradingValue,
            @JsonProperty("tomv") String marketCap
    ) {
    }
}
