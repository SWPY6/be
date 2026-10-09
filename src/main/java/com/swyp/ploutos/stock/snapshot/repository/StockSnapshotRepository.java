package com.swyp.ploutos.stock.snapshot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.stock.snapshot.StockSnapshots;

public interface StockSnapshotRepository extends JpaRepository<StockSnapshots, Long> {

    List<StockSnapshots> findByStockIdIn(List<Long> stockIds);
}
