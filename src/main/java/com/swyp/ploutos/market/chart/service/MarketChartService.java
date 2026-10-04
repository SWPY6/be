package com.swyp.ploutos.market.chart.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.chart.IndicatorLiveCandle;
import com.swyp.ploutos.market.price.service.IndicatorDailyPriceReader;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.market.quote.service.IndicatorQuoteReader;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartInterval;
import com.swyp.ploutos.stock.chart.ChartRange;
import com.swyp.ploutos.stock.price.DailyPrices;

import lombok.RequiredArgsConstructor;

/**
 * 구간·봉 단위별 지표 차트 데이터를 모은다. 확정 봉은 일봉 모듈에서, 진행 중 봉은 현재값 모듈에서 온다.
 * 이 서비스는 외부 시세 API를 직접 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MarketChartService {

    private final IndicatorDailyPriceReader dailyPriceReader;
    private final IndicatorQuoteReader quoteReader;
    private final Clock clock;

    /** 봉 단위를 가장 먼저 검증한다. 잘못된 요청이 DB나 외부 시세에 닿지 않게 하려는 것이다. */
    public MarketChartDetail read(MarketIndicator indicator, LocalDate from, LocalDate to, String intervalCode) {
        ChartInterval interval = ChartInterval.from(intervalCode);
        LocalDate today = LocalDate.ofInstant(clock.instant(), indicator.zoneId());
        ChartRange range = ChartRange.of(from, to, today);
        DailyPrices closed = dailyPriceReader.findBetween(indicator, range.from(), range.to());
        IndicatorQuote quote = quoteReader.read(indicator);
        return new MarketChartDetail(
                indicator,
                interval,
                Chart.of(closed, IndicatorLiveCandle.of(quote, closed, range, today), interval)
        );
    }
}
