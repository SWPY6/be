package com.swyp.ploutos.industry.flow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    private static final int MAJOR_STOCK_LIMIT = 2;

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
     * 오늘 누적 거래대금 합계. 견줄 수 있는 종목이 없으면 null 이고, 그때는
     * avgTradingValue20d 도 함께 null 이다 — 둘은 한 객체에서 나오므로 따로 존재하지 않는다.
     *
     * <p>나눈 결과가 아니라 금액을 저장한다. 비교 기준을 조회 시점에 고를 수 있고
     * (자기 평균 대비 / 시장 전체 대비), 기준이 바뀌어도 저장된 값이 그대로 쓰인다.
     * 10^18 까지 담는다 — 종목 87개 × 수백억 원이면 수조 단위다.
     */
    @Column(precision = 20, scale = 2)
    private BigDecimal todayTradingValue;

    /** 20거래일 평균 거래대금 합계. todayTradingValue 와 함께 있거나 함께 없다. */
    @Column(precision = 20, scale = 2)
    private BigDecimal avgTradingValue20d;

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

    /**
     * 저장된 거래대금. 둘 중 하나라도 없으면 비어 있다 — 꺼내 쓰는 쪽이 null 두 개를 맞춰 보지
     * 않게 한다.
     */
    public Optional<IndustryTradingValue> tradingValue() {
        if (todayTradingValue == null || avgTradingValue20d == null) {
            return Optional.empty();
        }
        return Optional.of(new IndustryTradingValue(todayTradingValue, avgTradingValue20d));
    }

    private void applyTradingValue(IndustryTradingValue tradingValue) {
        if (tradingValue == null) {
            this.todayTradingValue = null;
            this.avgTradingValue20d = null;
            return;
        }
        this.todayTradingValue = tradingValue.today();
        this.avgTradingValue20d = tradingValue.average20d();
    }

    private void apply(IndustryFlowSnapshot snapshot, LocalDateTime calculatedAt) {
        this.avgChangeRate = snapshot.avgChangeRate();
        this.stockCount = snapshot.stockCount();
        this.risingCount = snapshot.risingCount();
        this.fallingCount = snapshot.fallingCount();
        applyTradingValue(snapshot.tradingValue());
        this.calculatedAt = calculatedAt;
        clearMajorStocks();
        List<MajorStock> majorStocks = snapshot.majorStocks();
        if (majorStocks.size() > MAJOR_STOCK_LIMIT) {
            throw new IllegalArgumentException("대표 종목은 " + MAJOR_STOCK_LIMIT + "개까지다: " + majorStocks.size());
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
