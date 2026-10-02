package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.swyp.ploutos.common.enums.Country;

import lombok.Getter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 산업 하나의 평균 등락률 스냅샷. 산업 × 국가로 최대 18행이며 갱신할 때마다 덮어쓴다.
 * 순위는 나머지 산업과의 관계에서 나오는 파생값이라 저장하지 않는다 —
 * 저장하면 한 산업만 갱신됐을 때 1위가 둘인 상태가 만들어진다.
 */
@Getter
@Entity
@Table(
        name = "industry_flows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_industry_flows_industry_country",
                columnNames = {"industry_id", "country"})
)
public class IndustryFlows {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long industryFlowId;

    @Column(nullable = false)
    private Long industryId;	// FK : Industries.industryId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Country country;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal avgChangeRate;

    /** 평균에 실제로 반영된 종목 수. 시세를 못 구한 종목은 빠진다. */
    @Column(nullable = false)
    private int stockCount;

    /** 그중 오른 종목 수. 카드의 "표시된 종목 4개 중 3개가 상승했어요"에 쓴다. */
    @Column(nullable = false)
    private int risingCount;

    /** 그중 내린 종목 수. 보합은 어느 쪽에도 세지 않으므로 stockCount 에서 역산할 수 없다. */
    @Column(nullable = false)
    private int fallingCount;

    /**
     * 20거래일 평균 대비 거래대금 변화율 %. 견줄 수 있는 종목이 없으면 null 이다 —
     * 0.00 으로 채우면 "계산 실패"가 "변화 없음"으로 위장한다.
     * ±99999.99% 까지 담는다. 거래대금이 평소의 1000배가 되는 이상치도 들어간다.
     */
    @Column(precision = 7, scale = 2)
    private BigDecimal tradingValueChangeRate;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    // 시가총액 상위 2개의 계산 시점 스냅샷. 반영된 종목이 0·1개면 비어 있다.
    // 조회 시점에 종목명·등락률을 다시 구하면 KIS를 호출하게 되므로 여기 박아둔다.
    // stockId 도 함께 저장한다 — 관련 뉴스를 이 식별자로 찾는다.
    @Column
    private Long firstStockId;

    @Column(length = 20)
    private String firstTicker;

    @Column(length = 100)
    private String firstName;

    @Column(precision = 10, scale = 2)
    private BigDecimal firstChangeRate;

    @Column
    private Long secondStockId;

    @Column(length = 20)
    private String secondTicker;

    @Column(length = 100)
    private String secondName;

    @Column(precision = 10, scale = 2)
    private BigDecimal secondChangeRate;

    protected IndustryFlows() {

    }

    public IndustryFlows(Long industryId, Country country, IndustryFlowSnapshot snapshot,
            LocalDateTime calculatedAt) {
        this.industryId = industryId;
        this.country = country;
        apply(snapshot, calculatedAt);
    }

    /** 같은 산업·국가의 행을 새 계산 결과로 덮어쓴다. */
    public void refresh(IndustryFlowSnapshot snapshot, LocalDateTime calculatedAt) {
        apply(snapshot, calculatedAt);
    }

    public List<MajorStock> majorStocks() {
        if (firstTicker == null) {
            return List.of();
        }
        if (secondTicker == null) {
            return List.of(new MajorStock(firstStockId, firstTicker, firstName, firstChangeRate));
        }
        return List.of(
                new MajorStock(firstStockId, firstTicker, firstName, firstChangeRate),
                new MajorStock(secondStockId, secondTicker, secondName, secondChangeRate));
    }

    private void apply(IndustryFlowSnapshot snapshot, LocalDateTime calculatedAt) {
        this.avgChangeRate = snapshot.avgChangeRate();
        this.stockCount = snapshot.stockCount();
        this.risingCount = snapshot.risingCount();
        this.fallingCount = snapshot.fallingCount();
        this.tradingValueChangeRate = snapshot.tradingValueChangeRate();
        this.calculatedAt = calculatedAt;
        clearMajorStocks();
        List<MajorStock> majorStocks = snapshot.majorStocks();
        if (majorStocks.size() > IndustryFlowSnapshot.MAJOR_STOCK_LIMIT) {
            throw new IllegalArgumentException(
                    "대표 종목은 " + IndustryFlowSnapshot.MAJOR_STOCK_LIMIT + "개까지다: " + majorStocks.size());
        }
        if (majorStocks.isEmpty()) {
            return;
        }
        MajorStock first = majorStocks.get(0);
        this.firstStockId = first.stockId();
        this.firstTicker = first.ticker();
        this.firstName = first.name();
        this.firstChangeRate = first.changeRate();
        if (majorStocks.size() == 1) {
            return;
        }
        MajorStock second = majorStocks.get(1);
        this.secondStockId = second.stockId();
        this.secondTicker = second.ticker();
        this.secondName = second.name();
        this.secondChangeRate = second.changeRate();
    }

    private void clearMajorStocks() {
        this.firstStockId = null;
        this.firstTicker = null;
        this.firstName = null;
        this.firstChangeRate = null;
        this.secondStockId = null;
        this.secondTicker = null;
        this.secondName = null;
        this.secondChangeRate = null;
    }

}
