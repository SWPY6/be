package com.swyp.ploutos.stock.snapshot.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swyp.ploutos.stock.snapshot.StockSnapshot;
import com.swyp.ploutos.stock.snapshot.StockSnapshots;
import com.swyp.ploutos.stock.snapshot.repository.StockSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * 스냅샷을 덮어쓴다. 저장만 떼어 놓은 것은 부르는 쪽({@code IndustryFlowRefresher})이 종목마다
 * 쉬어 가며 외부 시세를 받아 한 산업에 수십 초를 쓰기 때문이다 — 거기에 트랜잭션을 걸면
 * 그동안 DB 커넥션을 붙잡는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
class StockSnapshotService implements StockSnapshotWriter {

    private final StockSnapshotRepository repository;

    /**
     * 기존 행을 한 번에 읽어 와 종목별로 맞춰 덮어쓴다. 종목마다 따로 조회하면 한 바퀴에
     * 종목 수만큼 쿼리가 나간다.
     */
    @Override
    public void save(List<StockSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        Map<Long, StockSnapshots> stored = repository
                .findByStockIdIn(snapshots.stream().map(StockSnapshot::stockId).toList())
                .stream()
                .collect(Collectors.toMap(StockSnapshots::stockId, Function.identity()));
        repository.saveAll(snapshots.stream()
                .map(snapshot -> merged(stored, snapshot))
                .toList());
    }

    private static StockSnapshots merged(Map<Long, StockSnapshots> stored, StockSnapshot snapshot) {
        StockSnapshots entity = stored.get(snapshot.stockId());
        if (entity == null) {
            return new StockSnapshots(snapshot);
        }
        entity.refresh(snapshot);
        return entity;
    }
}
