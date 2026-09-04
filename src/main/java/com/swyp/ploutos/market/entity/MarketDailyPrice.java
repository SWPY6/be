package com.swyp.ploutos.market.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "market_daily_price")
public class MarketDailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_daily_price_id")
    private Long marketDailyPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "markets_id", nullable = false)
    private Markets marketsId;

    @Column(name = "close_value", precision = 20, scale = 4, nullable = false)
    private BigDecimal closeValue;

    @Column(name = "change_rate", precision = 10, scale = 4, nullable = false)
    private BigDecimal changeRate;

    protected MarketDailyPrice() {
    }
}