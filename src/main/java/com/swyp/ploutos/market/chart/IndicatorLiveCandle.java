package com.swyp.ploutos.market.chart;

import java.time.LocalDate;
import java.util.Optional;

import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.stock.chart.ChartRange;
import com.swyp.ploutos.stock.chart.LiveCandle;
import com.swyp.ploutos.stock.price.DailyPrices;

/**
 * 지표의 현재값을 진행 중인 봉으로 쓸 수 있는지 판단한다. 쓸 수 없으면 비어 있다.
 *
 * <p>시가가 0인지만으로는 부족하다. 장 시작 전 KIS가 전날 시세를 주는데
 * 그때 시가는 0이 아니다. 그대로 붙이면 오늘 자리에 전날과 똑같은 봉이 생긴다.
 * 그래서 현재값이 마지막 확정 봉에서 이어지는 값인지도 본다. 날짜가 아니라 값의
 * 연속성으로 보기 때문에 국내·해외·환율에 같은 규칙이 통한다.
 *
 * <p>판정 규칙은 종목 차트와 같다 — {@code ChartRange.contains}와
 * {@code DailyPrices.lastCloseIs}를 함께 쓴다 ({@code SPEC-stock-chart.md}).
 *
 * <p>오늘 날짜의 확정 봉이 이미 있는 경우는 여기서 보지 않는다. {@code Chart}가 판단한다.
 */
public final class IndicatorLiveCandle {

    private IndicatorLiveCandle() {
    }

    public static Optional<LiveCandle> of(IndicatorQuote quote, DailyPrices closed, ChartRange range,
            LocalDate today) {
        if (cannotAttach(quote, closed, range, today)) {
            return Optional.empty();
        }
        return Optional.of(new LiveCandle(quote.asDailyPrice(today), quote.valueAt()));
    }

    private static boolean cannotAttach(IndicatorQuote quote, DailyPrices closed, ChartRange range,
            LocalDate today) {
        return quote.notOpenedToday()
                || !range.contains(today)
                || !closed.lastCloseIs(quote.previousClose());
    }
}
