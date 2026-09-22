package com.swyp.ploutos.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.stock.Stocks;

public interface StockRepository extends JpaRepository<Stocks, Long> {
}
