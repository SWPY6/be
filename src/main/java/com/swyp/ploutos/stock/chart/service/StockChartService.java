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
import com.swyp.ploutos.stock.price.DailyPrice;
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

    /**
     * 진행 중인 봉을 붙일 수 있는지 판단한다. 다음 중 하나라도 해당하면 없다.
     *
     * <ol>
     *   <li>당일 시가가 없다. 아직 개장하지 않았다.</li>
     *   <li>오늘 봉으로 만든 값이 마지막 확정 봉과 똑같다. 장 시작 전·휴장일에 KIS는 시가가 0이 아닌
     *       직전 거래일의 시세를 그대로 준다.</li>
     *   <li>오늘이 구간 밖이다. 붙이면 요청하지 않은 오늘 봉이 끼어든다.</li>
     * </ol>
     *
     * <p>지표 차트({@code IndicatorLiveCandle})와 달리 전일 종가를 견주지 않는다. 모의 도메인에서
     * 주식 현재가의 기준가({@code stck_sdpr})가 저장된 종가와 자주 어긋나 정상적인 오늘 봉까지 지웠다
     * (2026-10-07 실측, 국내 35종목 중 30종목).
     */
    private static Optional<LiveCandle> liveCandle(Quote quote, DailyPrices closed, ChartRange range,
            LocalDate today) {
        DailyPrice todayPrice = quote.asDailyPrice(today);
        if (quote.notOpenedToday()
                || closed.repeatsLast(todayPrice)
                || !range.contains(today)) {
            return Optional.empty();
        }
        return Optional.of(new LiveCandle(todayPrice, quote.priceAt()));
    }
}
