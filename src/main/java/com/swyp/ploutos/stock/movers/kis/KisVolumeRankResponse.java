package com.swyp.ploutos.stock.movers.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.amount;
import static com.swyp.ploutos.external.kis.KisNumbers.count;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.movers.StockMover;
import com.swyp.ploutos.stock.movers.VolumeSurge;

/**
 * 국내 거래량순위(FHPST01710000) 응답. 한 번에 30건이다.
 *
 * <p>쓰지 않는 필드가 둘 있다. <b>{@code avrg_vol}</b>은 이름이 "평균 거래량"이지만 실측에서
 * 30건 모두 {@code acml_vol}과 같은 값이 와 배수가 항상 1.0이 된다. <b>{@code vol_inrt}</b>는
 * 이름이 "거래량증가율"이지만 실제로는 {@code (누적 ÷ 전일) × 100}이고 {@code 9999.99}에서
 * 포화한다. 그래서 전일 거래량으로 직접 나눈다.
 */
record KisVolumeRankResponse(
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
            @JsonProperty("mksc_shrn_iscd") String ticker,
            @JsonProperty("hts_kor_isnm") String name,
            @JsonProperty("stck_prpr") String price,
            @JsonProperty("prdy_ctrt") String changeRate,
            @JsonProperty("acml_vol") String volume,
            @JsonProperty("prdy_vol") String previousVolume,
            @JsonProperty("acml_tr_pbmn") String tradingValue,
            /** 상장주식수. 시가총액은 현재가와 곱해 얻는다 — 응답에 시가총액 필드가 없다. */
            @JsonProperty("lstn_stcn") String listedShares
    ) {

        StockMover toMover() {
            BigDecimal price = amount(this.price);
            return new StockMover(null, ticker, name, null, price, amount(changeRate),
                    count(volume), amount(tradingValue), marketCap(price),
                    VolumeSurge.ratio(count(volume), count(previousVolume)).orElse(null));
        }

        private BigDecimal marketCap(BigDecimal price) {
            return price.multiply(BigDecimal.valueOf(count(listedShares)));
        }
    }
}
