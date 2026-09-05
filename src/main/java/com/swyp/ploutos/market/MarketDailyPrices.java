package com.swyp.ploutos.market;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "market_daily_prices")
public class MarketDailyPrices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long marketDailyPriceId;

    @Column(nullable = false)
    private Long marketId;	// FK : Markets.marketId
    
    @Column(nullable = false)
    private LocalDate tradingDate;

    @Column(precision = 20, scale = 4, nullable = false)
    private BigDecimal closeValue;

    @Column(precision = 10, scale = 4, nullable = false)
    private BigDecimal changeRate;

    protected MarketDailyPrices() {
    	
    }
    
}
