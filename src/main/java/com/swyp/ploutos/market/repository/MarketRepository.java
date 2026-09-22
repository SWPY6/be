package com.swyp.ploutos.market.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.market.Markets;

public interface MarketRepository extends JpaRepository<Markets, Long> {
}
