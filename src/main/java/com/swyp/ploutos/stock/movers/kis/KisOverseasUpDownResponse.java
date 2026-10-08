package com.swyp.ploutos.stock.movers.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.amount;
import static com.swyp.ploutos.external.kis.KisNumbers.count;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.movers.StockMover;

/**
 * 해외 상승률·하락률 순위(HHDFS76290000) 응답. 목록은 {@code output2}에 온다.
 * 한 번에 100건이고 {@code tr_cont=F}로 다음 페이지가 있지만 이어 받지 않는다 —
 * 100건이면 요구사항을 채운다.
 *
 * <p>거래대금({@code tamt})은 오지만 시가총액은 없다.
 */
record KisOverseasUpDownResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output2") List<Row> rows
) implements KisResponse {

    List<StockMover> toMovers() {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(Row::toMover).toList();
    }

    record Row(
            @JsonProperty("symb") String ticker,
            @JsonProperty("name") String name,
            @JsonProperty("last") String price,
            @JsonProperty("rate") String changeRate,
            @JsonProperty("tvol") String volume,
            @JsonProperty("tamt") String tradingValue
    ) {

        StockMover toMover() {
            return new StockMover(null, ticker, name, null, amount(price), amount(changeRate),
                    count(volume), amount(tradingValue), null, null);
        }
    }
}
