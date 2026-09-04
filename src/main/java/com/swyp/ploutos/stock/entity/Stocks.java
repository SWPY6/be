package com.swyp.ploutos.stock.entity;

import java.time.LocalDateTime;

import com.swyp.ploutos.market.entity.Markets;

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
@Table(name = "stocks")
public class Stocks {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "stocks_id", nullable = false) 
	private Long stocksId;
	
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "markets_id", nullable = false)
	private Markets marketsId;

	@Column(name = "ticker", length = 20)
	private String ticker;

	@Column(name = "name", length = 100)
	private String name;

	@Column(name = "status")
	private String status;

	@Column(name = "float_shares")
	private Long floatShares;

	@Column(name = "ceo", length = 50)
	private String ceo;

	@Column(name = "listing_date")
	private LocalDateTime listingDate;

	protected Stocks() {
		
	    }

}
