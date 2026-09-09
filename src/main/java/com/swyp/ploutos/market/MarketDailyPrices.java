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
    @Column(nullable = false)
    private Long marketDailyPriceId;

    @Column(nullable = false)
    private Long marketId;	// FK : Markets.marketId
    
    @Column(nullable = false)
    private LocalDate tradeAt;

    @Column(precision = 20, scale = 4, nullable = false)
    private BigDecimal closeValue;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal changeRate;

    protected MarketDailyPrices() {
    	
    }
    
}
