package com.swyp.ploutos.stock;

import java.time.LocalDate;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.market.Markets;

/**
 * 종목과 그 종목이 속한 시장. 시세 조회에 필요한 값을 한 단계로 노출해,
 * 사용하는 쪽이 종목과 시장 중 어디에 있는 값인지 알 필요가 없게 한다.
 */
public record StockWithMarket(
        Stocks stock,
        Markets market
) {

    public Long stockId() {
        return stock.stockId();
    }

    public String ticker() {
        return stock.ticker();
    }

    public Exchange exchange() {
        return stock.exchange();
    }

    public boolean listedAfter(LocalDate date) {
        return stock.listedAfter(date);
    }

    public Country country() {
        return market.country();
    }

    public Currency currency() {
        return market.currency();
    }
}
