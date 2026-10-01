package com.swyp.ploutos.market.price.kis;

import static com.swyp.ploutos.external.kis.KisNumbers.requiredAmount;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.price.DailyPrice;

/** 국내업종 기간별 지수(FHKUP03500100) 응답. output2가 최신순 일봉이다. */
record KisDomesticIndexDailyResponse(
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
            @JsonProperty("bstp_nmix_oprc") String open,
            @JsonProperty("bstp_nmix_hgpr") String high,
            @JsonProperty("bstp_nmix_lwpr") String low,
            /** 확정된 행에서는 현재값이 그날 종가다. */
            @JsonProperty("bstp_nmix_prpr") String close
    ) {

        /** KIS는 건수가 모자라면 빈 문자열로 채운 행을 돌려주기도 한다. */
        boolean isPresent() {
            return date != null && !date.isBlank();
        }

        /** 지표에는 거래량이 없다. 확정 봉의 OHLC가 비면 봉을 만들 수 없어 시세 조회 실패로 알린다. */
        DailyPrice toDailyPrice() {
            return new DailyPrice(
                    LocalDate.parse(date, DATE),
                    requiredAmount(open, "bstp_nmix_oprc"),
                    requiredAmount(high, "bstp_nmix_hgpr"),
                    requiredAmount(low, "bstp_nmix_lwpr"),
                    requiredAmount(close, "bstp_nmix_prpr"),
                    0L
            );
        }
    }
}
