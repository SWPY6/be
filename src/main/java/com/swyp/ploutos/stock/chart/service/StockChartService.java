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
                Chart.of(closed, liveCandle(quote, closed, range, today), interval)
        );
    }

    private static Optional<LiveCandle> liveCandle(Quote quote, DailyPrices closed, ChartRange range,
            LocalDate today) {
        if (cannotAttach(quote, closed, range, today)) {
            return Optional.empty();
        }
        return Optional.of(new LiveCandle(quote.asDailyPrice(today), quote.priceAt()));
    }

    /**
     * 명세의 "붙이지 않는다" 조건 1~3. 하나라도 해당하면 진행 중인 봉이 없다.
     * 조건 4(오늘 날짜의 확정 봉이 이미 있다)는 {@code Chart}가 판단한다.
     *
     * <p>시가 0만으로는 부족하다. 휴장일과 장 마감 후에는 KIS가 직전 거래일의 값을 그대로 주는데
     * 그때 시가는 0이 아니다. 그대로 붙이면 거래가 없던 날짜에 가짜 봉이 생긴다.
     */
    private static boolean cannotAttach(Quote quote, DailyPrices closed, ChartRange range, LocalDate today) {
        return quote.notOpenedToday()
                || !range.contains(today)
                || !closed.lastCloseIs(quote.previousClose());
    }
}
