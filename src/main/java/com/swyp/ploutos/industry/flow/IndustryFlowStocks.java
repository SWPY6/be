package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;

import lombok.Getter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 한 산업의 대표 종목 스냅샷 한 줄. {@link IndustryFlows} 하나에 0~4행이 달린다.
 *
 * <p>평면 컬럼({@code first_*}·{@code second_*})을 대체한다. 목록을 번호 붙인 컬럼으로 펼치면
 * <b>"몇 개까지 담을 수 있는가"가 스키마에 굳는다</b> — 그 숫자가 상수로 엔티티와 계산기 두 곳에
 * 흩어져 있었고, 뜻이 다른데(저장 자리 수 / 표시 개수) 값만 같아 한쪽만 올리면 런타임에 터졌다.
 * 행으로 두면 개수 변경이 스키마 변경이 아니라 데이터 문제가 된다.
 *
 * <p>종목명과 현재가를 함께 담는 것은 <b>계산 시점의 스냅샷</b>이기 때문이다. 조회할 때 다시
 * 구하면 외부 시세를 호출하게 되고, 등락률과 다른 시점의 현재가가 나란히 놓인다.
 *
 * <p>{@code industryFlowId}는 평범한 {@code Long}이다 — 이 저장소는 JPA 연관관계
 * 애너테이션을 쓰지 않는다.
 */
@Getter
@Entity
@Table(
        name = "industry_flow_stocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_industry_flow_stocks_order",
                columnNames = {"industry_flow_id", "display_order"}),
        indexes = @Index(
                name = "idx_industry_flow_stocks_flow",
                columnList = "industry_flow_id")
)
public class IndustryFlowStocks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long industryFlowStockId;

    @Column(nullable = false)
    private Long industryFlowId;    // FK : IndustryFlows.industryFlowId

    @Column(nullable = false)
    private Long stockId;           // FK : Stocks.stockId

    @Column(nullable = false, length = 20)
    private String ticker;

    /** 계산 시점의 종목명. 종목명이 바뀌어도 이 행은 그때의 값을 유지한다. */
    @Column(nullable = false, length = 100)
    private String name;

    /** 계산 시점의 현재가. 해외 종목의 센트 단위까지 담는다. */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal changeRate;

    /**
     * 시가총액 순위. 0부터 시작한다.
     *
     * <p>{@code (industry_flow_id, display_order)}에 유일 제약이 있어 같은 자리에 두 종목이
     * 들어가는 것을 DB가 막는다. 조회 순서를 이 값으로 정하므로 저장 순서에 기대지 않는다.
     */
    @Column(nullable = false)
    private int displayOrder;

    protected IndustryFlowStocks() {

    }

    public IndustryFlowStocks(Long industryFlowId, Long stockId, String ticker, String name,
            BigDecimal price, BigDecimal changeRate, int displayOrder) {
        this.industryFlowId = industryFlowId;
        this.stockId = stockId;
        this.ticker = ticker;
        this.name = name;
        this.price = price;
        this.changeRate = changeRate;
        this.displayOrder = displayOrder;
    }
}
