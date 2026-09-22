package com.swyp.ploutos.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.common.enums.StockStatus;

import lombok.Getter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@Entity
@Table(name = "stocks")
public class Stocks {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private Long stockId;
	
    @Column(nullable = false)
	private Long marketId;	// FK : Markets.marketId

	@Column(nullable = false, length = 20)
	private String ticker;

	@Column(nullable = false, length = 100)
	private String name;
	
	@Column(length = 2100)
	private String imgUrl;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StockStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Exchange exchange;

	@Column(nullable = false)
	private Long floatShares;

	@Column(nullable = false, length = 50)
	private String ceo;

	@Column(nullable = false)
	private LocalDate listedAt;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column
	private LocalDateTime updatedAt;

	protected Stocks() {

	    }

	public Stocks(Long marketId, String ticker, String name, String imgUrl, StockStatus status,
			Exchange exchange, Long floatShares, String ceo, LocalDate listedAt) {
		this.marketId = marketId;
		this.ticker = ticker;
		this.name = name;
		this.imgUrl = imgUrl;
		this.status = status;
		this.exchange = exchange;
		this.floatShares = floatShares;
		this.ceo = ceo;
		this.listedAt = listedAt;
	}

	public boolean listedAfter(LocalDate date) {
		return listedAt.isAfter(date);
	}
}

