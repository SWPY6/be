package com.swyp.ploutos.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.swyp.ploutos.common.enums.StockStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@EntityListeners(AuditingEntityListener.class)
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

	@Column(nullable = false)
	private Long floatShares;

	@Column(nullable = false, length = 50)
	private String ceo;

	@Column(nullable = false)
	private LocalDate listedAt;
	
	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column
	private LocalDateTime updatedAt;

	protected Stocks() {
		
	    }

}

