package com.swyp.ploutos.market.price.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.requiredAmount;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.price.DailyPrice;

/** 해외 지수·환율 기간별 시세(FHKST03030100) 응답. output2가 최신순 일봉이다. */
record KisOverseasChartDailyResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg1,
        @JsonProperty("output2") List<Row> rows
) implements KisResponse {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    List<DailyPrice> toDailyPrices() {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .filter(Row::isPresent)
                .map(Row::toDailyPrice)
                .toList();
    }

    record Row(
            @JsonProperty("stck_bsop_date") String date,
            @JsonProperty("ovrs_nmix_oprc") String open,
            @JsonProperty("ovrs_nmix_hgpr") String high,
            @JsonProperty("ovrs_nmix_lwpr") String low,
            /** 확정된 행에서는 현재값이 그날 종가다. */
            @JsonProperty("ovrs_nmix_prpr") String close
    ) {

        /** KIS는 건수가 모자라면 빈 문자열로 채운 행을 돌려주기도 한다. */
        boolean isPresent() {
            return date != null && !date.isBlank();
        }

        /** 지표에는 거래량이 없다. 해외 지수와 환율은 KIS가 거래량을 0으로 준다. */
        DailyPrice toDailyPrice() {
            return new DailyPrice(
                    LocalDate.parse(date, DATE),
                    requiredAmount(open, "ovrs_nmix_oprc"),
                    requiredAmount(high, "ovrs_nmix_hgpr"),
                    requiredAmount(low, "ovrs_nmix_lwpr"),
                    requiredAmount(close, "ovrs_nmix_prpr"),
                    0L
            );
        }
    }
}
