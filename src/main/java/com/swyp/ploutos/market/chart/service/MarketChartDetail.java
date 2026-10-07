package com.swyp.ploutos.market.chart.service;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartInterval;

/**
 * 차트 데이터와 그것을 읽는 데 쓴 조건(지표·봉 단위).
 * 통화를 따로 담지 않는다. 지표가 자기 단위를 안다.
 */
public record MarketChartDetail(
        MarketIndicator indicator,
        ChartInterval interval,
        Chart chart
) {
}
