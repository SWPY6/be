package com.swyp.ploutos.stock.movers.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.amount;
import static com.swyp.ploutos.external.kis.KisNumbers.count;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.movers.StockMover;
import com.swyp.ploutos.stock.movers.VolumeRatio;

/**
 * 해외 거래증가율순위(HHDFS76330000) 응답. 목록은 {@code output2}에 온다.
 *
 * <p><b>거래량급증 API(HHDFS76270000)를 쓰지 않는 이유</b> — 그쪽은 {@code MINX}(N분 전)가
 * 기준이라 분모가 "그 시점의 거래량"이다. 실측에서 기준거래량이 1·23·80처럼 나와 100건 전부
 * 증가율이 포화했고, 문서에 적힌 {@code trat} 필드는 응답에 아예 없었다.
 *
 * <p>이쪽의 {@code n_tvol}은 진짜 평균이다. {@code NDAY=5}로 부르면 <b>직전 20거래일 평균</b>이며
 * 당일 봉은 빠진다 — 같은 종목의 일봉으로 직접 평균을 내 오차 없이 일치하는 것을 확인했다.
 * {@code n_rate}는 쓰지 않는다. 역시 {@code 9999.99}에서 포화한다.
 */
record KisOverseasTradeGrowthResponse(
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
            /** 직전 20거래일 평균 거래량({@code NDAY=5} 기준). */
            @JsonProperty("n_tvol") String averageVolume,
            @JsonProperty("tamt") String tradingValue
    ) {

        StockMover toMover() {
            return new StockMover(null, ticker, name, null, amount(price), amount(changeRate),
                    count(volume), amount(tradingValue), null,
                    VolumeRatio.of(count(volume), count(averageVolume)).orElse(null));
        }
    }
}
