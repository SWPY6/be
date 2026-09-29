package com.swyp.ploutos.stock.chart.service;

import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartInterval;

/** 차트 데이터와 그것을 읽는 데 쓴 조건(종목·봉 단위·통화). */
public record StockChartDetail(
        Long stockId,
        ChartInterval interval,
        Currency currency,
        Chart chart
) {
}
