package com.swyp.ploutos.stock.price.kis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.price.DailyPrice;

/** 국내주식 기간별 시세(FHKST03010100) 응답. output2가 최신순 일봉이다. */
record KisDomesticDailyPriceResponse(
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
            @JsonProperty("stck_oprc") String open,
            @JsonProperty("stck_hgpr") String high,
            @JsonProperty("stck_lwpr") String low,
            @JsonProperty("stck_clpr") String close,
            @JsonProperty("acml_vol") String volume
    ) {

        // KIS는 건수가 모자라면 빈 문자열로 채운 행을 돌려주기도 한다.
        boolean isPresent() {
            return date != null && !date.isBlank();
        }

        DailyPrice toDailyPrice() {
            return new DailyPrice(
                    LocalDate.parse(date, DATE),
                    new BigDecimal(open),
                    new BigDecimal(high),
                    new BigDecimal(low),
                    new BigDecimal(close),
                    Long.parseLong(volume)
            );
        }
    }
}
