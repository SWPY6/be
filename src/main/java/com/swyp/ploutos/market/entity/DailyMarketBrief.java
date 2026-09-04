package com.swyp.ploutos.market.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "daily_market_briefs")
public class DailyMarketBrief {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_market_brief_id")
    private Long dailyMarketBriefId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "markets_id", nullable = false)
    private Markets marketsId;

    @Column(name = "headline", length = 200)
    private String headline;

    @Lob
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected DailyMarketBrief() {
    	
    }
}