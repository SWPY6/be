package com.swyp.ploutos.stock.movers.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.amount;
import static com.swyp.ploutos.external.kis.KisNumbers.count;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.movers.StockMover;

/**
 * 국내 등락률 순위(FHPST01700000) 응답. 한 번에 30건이고 이어 받을 수 없다.
 *
 * <p>거래대금·시가총액·평균 거래량이 없다 — 그 열은 상승·하락 조건에서 비워 둔다.
 */
record KisFluctuationResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output") List<Row> rows
) implements KisResponse {

    List<StockMover> toMovers() {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(Row::toMover).toList();
    }

    record Row(
            @JsonProperty("stck_shrn_iscd") String ticker,
            @JsonProperty("hts_kor_isnm") String name,
            @JsonProperty("stck_prpr") String price,
            @JsonProperty("prdy_ctrt") String changeRate,
            @JsonProperty("acml_vol") String volume
    ) {

        StockMover toMover() {
            return new StockMover(null, ticker, name, null, amount(price), amount(changeRate),
                    count(volume), null, null, null);
        }
    }
}
