package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.industry.flow.IndustryTradingValue.StockTradingValue;
import com.swyp.ploutos.stock.quote.Quote;

/**
 * 시세를 붙인 종목. 평균을 내는 데 필요한 값만 한 단계로 노출해, 계산기가 종목과 시세 중
 * 어디에 있는 값인지 알 필요가 없게 한다.
 *
 * @param averageTradingValue20d 저장된 일봉으로 근사한 20거래일 평균 거래대금.
 *                               일봉이 20개에 못 미치면 {@code null}이다
 */
public record QuotedStock(
        StockWithMarket stock,
        Quote quote,
        BigDecimal averageTradingValue20d
) {

    public String ticker() {
        return stock.ticker();
    }

    public String name() {
        return stock.name();
    }

    public Country country() {
        return stock.country();
    }

    public BigDecimal changeRate() {
        return quote.changeRate();
    }

    public BigDecimal marketCap() {
        return quote.marketCap();
    }

    /** 직전 거래일 종가보다 올랐는지. 보합(0.00)은 오른 것도 내린 것도 아니다. */
    public boolean rose() {
        return changeRate().signum() > 0;
    }

    /** 직전 거래일 종가보다 내렸는지. */
    public boolean fell() {
        return changeRate().signum() < 0;
    }

    public MajorStock toMajorStock() {
        return new MajorStock(ticker(), name(), changeRate());
    }

    /** 오늘 누적 거래대금과 20거래일 평균을 짝지어 낸다. 평균이 없으면 견줄 수 없는 종목이 된다. */
    public StockTradingValue toTradingValue() {
        return new StockTradingValue(quote.tradingValue(), averageTradingValue20d);
    }
}
