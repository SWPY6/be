package com.swyp.ploutos.industry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_industries")
public class StockIndustries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockIndustryId;

    @Column(nullable = false)
    private Long stockId;	// FK : Stocks.stockId

    @Column(nullable = false)
    private Long industryId; // FK : Industries.industryId

    protected StockIndustries() {
    	
    }
    
}
