package com.swyp.ploutos.stock.snapshot.service;

import java.util.List;

import com.swyp.ploutos.stock.snapshot.StockSnapshot;

/** 저장된 종목 시세를 읽는 계약. 읽는 쪽은 외부 시세를 부르지 않는다. */
public interface StockSnapshotReader {

    /** 저장된 전부. 국가로 거르지 않는다 — 스냅샷에는 시장 정보가 없다. */
    List<StockSnapshot> readAll();

    /** 주어진 종목만. 값이 없는 종목은 결과에서 빠진다. */
    List<StockSnapshot> read(List<Long> stockIds);
}
