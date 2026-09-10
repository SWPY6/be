package com.swyp.ploutos.market;

import java.time.LocalDateTime;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.MarketCode;
import com.swyp.ploutos.common.enums.TradingSession;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "markets")
public class Markets {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long marketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketCode code;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 10)
    private Country country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradingSession tradingSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 10)
    private Currency currency;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    protected Markets() {
    	
    }
    
}
