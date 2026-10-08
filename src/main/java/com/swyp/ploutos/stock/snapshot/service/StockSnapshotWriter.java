package com.swyp.ploutos.stock.snapshot.service;

import java.util.List;

import com.swyp.ploutos.stock.snapshot.StockSnapshot;

/** 종목 시세 스냅샷을 남기는 계약. 시세를 이미 손에 쥔 쪽이 부른다. */
public interface StockSnapshotWriter {

    /** 같은 종목의 기존 행을 덮어쓴다. 종목당 한 행만 남는다. */
    void save(List<StockSnapshot> snapshots);
}
