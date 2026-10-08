package com.swyp.ploutos.stock.snapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;

/**
 * 종목별 시세 스냅샷. 주요 변동 종목 화면이 외부 호출 없이 정렬·필터할 수 있게 한다.
 *
 * <p>값은 산업 흐름 갱신기가 채운다. 그쪽이 산업마다 소속 종목 전체의 시세를 이미 받아
 * 평균만 내고 버리므로, 그 값을 여기 남기는 데 <b>외부 호출이 늘지 않는다.</b>
 *
 * <p>종목당 한 행이고 갱신될 때마다 덮어쓴다 — 이력이 필요하면 일봉을 본다.
 */
@Getter
@Entity
@Table(
        name = "stock_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_snapshots_stock", columnNames = "stock_id")
)
public class StockSnapshots {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long stockSnapshotId;

    @Column(nullable = false)
    private Long stockId;   // FK : Stocks.stockId

    /** 해외 종목의 센트 단위까지 담는다. */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal changeRate;

    @Column(nullable = false)
    private long volume;

    /** 당일 누적 거래대금. 시세 공급자가 주지 않으면 {@code null}이다. */
    @Column(precision = 20, scale = 4)
    private BigDecimal tradingValue;

    /** 시가총액. 시세 공급자가 주지 않으면 {@code null}이다. */
    @Column(precision = 20, scale = 4)
    private BigDecimal marketCap;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    protected StockSnapshots() {

    }

    public StockSnapshots(StockSnapshot snapshot) {
        this.stockId = snapshot.stockId();
        refresh(snapshot);
    }

    /** 같은 종목의 새 값으로 바꾼다. 식별자와 종목은 그대로 둔다. */
    public void refresh(StockSnapshot snapshot) {
        this.price = snapshot.price();
        this.changeRate = snapshot.changeRate();
        this.volume = snapshot.volume();
        this.tradingValue = snapshot.tradingValue();
        this.marketCap = snapshot.marketCap();
        this.calculatedAt = snapshot.calculatedAt();
    }

    public StockSnapshot toSnapshot() {
        return new StockSnapshot(stockId, price, changeRate, volume, tradingValue, marketCap,
                calculatedAt);
    }
}
