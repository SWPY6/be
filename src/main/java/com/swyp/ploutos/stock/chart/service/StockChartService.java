package com.swyp.ploutos.stock.chart.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.chart.Chart;
import com.swyp.ploutos.stock.chart.ChartInterval;
import com.swyp.ploutos.stock.chart.ChartRange;
import com.swyp.ploutos.stock.chart.LiveCandle;
import com.swyp.ploutos.stock.price.DailyPrices;
import com.swyp.ploutos.stock.price.service.DailyPriceReader;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.quote.service.QuoteReader;
import com.swyp.ploutos.stock.service.StockReader;

import lombok.RequiredArgsConstructor;

/**
 * 구간·봉 단위별 차트 데이터를 모은다. 확정 봉은 일봉 모듈에서, 진행 중 봉은 현재가 모듈에서 온다.
 * 이 서비스는 외부 시세 API를 직접 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StockChartService {

    private final StockReader stockReader;
    private final DailyPriceReader dailyPriceReader;
    private final QuoteReader quoteReader;
    private final Clock clock;

    public StockChartDetail read(Long stockId, LocalDate from, LocalDate to, String intervalCode) {
        ChartInterval interval = ChartInterval.from(intervalCode);
        StockWithMarket stock = stockReader.read(stockId);
        LocalDate today = stock.localDateAt(clock.instant());
        ChartRange range = ChartRange.of(from, to, today);
        DailyPrices closed = dailyPriceReader.findBetween(stockId, range.from(), range.to());
        Quote quote = quoteReader.read(stockId);
        return new StockChartDetail(
                stockId,
                interval,
                stock.currency(),
                Chart.of(closed, liveCandle(quote, today), interval)
        );
    }

    /** 당일 시가가 없으면 아직 개장하지 않은 것으로 본다. 그때는 진행 중인 봉이 없다. */
    private static Optional<LiveCandle> liveCandle(Quote quote, LocalDate today) {
        if (quote.notOpenedToday()) {
            return Optional.empty();
        }
        return Optional.of(new LiveCandle(quote.asDailyPrice(today), quote.priceAt()));
    }
}
