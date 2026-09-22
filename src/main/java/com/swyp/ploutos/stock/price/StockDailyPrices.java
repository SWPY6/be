package com.swyp.ploutos.stock.price;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "stock_daily_prices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "trade_at"})
)
public class StockDailyPrices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long stockDailyPriceId;

    @Column(nullable = false)
    private Long stockId;	// FK : Stocks.stockId

    @Column(nullable = false)
    private LocalDate tradeAt;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal openPrice;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal highPrice;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal lowPrice;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal closePrice;

    @Column(nullable = false)
    private Long volume;

    protected StockDailyPrices() {

    }

    public StockDailyPrices(Long stockId, DailyPrice price) {
        this.stockId = stockId;
        this.tradeAt = price.tradeAt();
        this.openPrice = price.open();
        this.highPrice = price.high();
        this.lowPrice = price.low();
        this.closePrice = price.close();
        this.volume = price.volume();
    }

    public DailyPrice toDailyPrice() {
        return new DailyPrice(tradeAt, openPrice, highPrice, lowPrice, closePrice, volume);
    }

}
