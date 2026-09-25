package com.swyp.ploutos.stock.price.kis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.ploutos.external.kis.KisResponse;
import com.swyp.ploutos.stock.price.DailyPrice;

/** 해외주식 기간별 시세(HHDFS76240000) 응답. output2가 기준일 이전 최신순 일봉이다. */
record KisOverseasDailyPriceResponse(
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
            @JsonProperty("xymd") String date,
            @JsonProperty("open") String open,
            @JsonProperty("high") String high,
            @JsonProperty("low") String low,
            @JsonProperty("clos") String close,
            @JsonProperty("tvol") String volume
    ) {

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
