package com.swyp.ploutos.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_news")
public class StockNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long stockNewsId;

    @Column(nullable = false)
    private Long stockId;	// FK : Stocks.stockId

    @Column(nullable = false)
    private Long newsId;	// FK : News.newsId

    protected StockNews() {
    	
    }
    
}
