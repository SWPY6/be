package com.swyp.ploutos.market.price;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.stock.price.DailyPrice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 시장 지표의 확정 일봉. 지표에는 거래량이 없어 OHLC만 저장한다. */
@Entity
@Table(
        name = "market_daily_prices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"indicator", "trade_at"})
)
public class MarketDailyPrices {

    /** 지표에는 거래량이 없다. */
    private static final long NO_VOLUME = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long marketDailyPriceId;

    @Column(nullable = false)
    private LocalDate tradeAt;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal openValue;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal highValue;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal lowValue;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal closeValue;

    /**
     * 네이티브 enum이 아니라 varchar로 매핑한다. Hibernate는 {@code @Enumerated(STRING)}을
     * MySQL 네이티브 enum으로 만들어, 지표를 추가할 때마다 ALTER TABLE 을 요구한다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MarketIndicator indicator;

    protected MarketDailyPrices() {

    }

    public MarketDailyPrices(MarketIndicator indicator, DailyPrice price) {
        this.indicator = indicator;
        this.tradeAt = price.tradeAt();
        this.openValue = price.open();
        this.highValue = price.high();
        this.lowValue = price.low();
        this.closeValue = price.close();
    }

    public DailyPrice toDailyPrice() {
        return new DailyPrice(tradeAt, openValue, highValue, lowValue, closeValue, NO_VOLUME);
    }

}
