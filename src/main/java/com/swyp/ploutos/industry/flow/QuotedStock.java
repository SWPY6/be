package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.quote.Quote;

/**
 * 시세를 붙인 종목. 평균을 내는 데 필요한 값만 한 단계로 노출해, 계산기가 종목과 시세 중
 * 어디에 있는 값인지 알 필요가 없게 한다.
 */
public record QuotedStock(
        StockWithMarket stock,
        Quote quote
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

    public MajorStock toMajorStock() {
        return new MajorStock(ticker(), name(), changeRate());
    }
}
