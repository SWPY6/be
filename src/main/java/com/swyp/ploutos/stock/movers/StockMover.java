package com.swyp.ploutos.stock.movers;

import java.math.BigDecimal;

import com.swyp.ploutos.common.enums.IndustryCode;

/**
 * 목록에 실리는 종목 한 줄.
 *
 * @param stockId      우리 종목 마스터에서 찾지 못하면 {@code null}이다. ETF·우선주·신규 상장처럼
 *                     외부 순위에는 있고 우리 쪽에 없는 종목은 <b>항상 있다</b>
 * @param industry     같은 이유로 {@code null}일 수 있다
 * @param tradingValue 공급자가 주지 않는 조건에서는 {@code null}이다
 * @param marketCap    같다. 임의의 수치로 채우지 않는다(RQ-0706)
 * @param volumeRatio  거래량 배수. 분모는 {@link VolumeBaseline}이 알려 준다.
 *                     거래량 급증이 아닌 조건에서는 {@code null}이다
 */
public record StockMover(
        Long stockId,
        String ticker,
        String name,
        IndustryCode industry,
        BigDecimal price,
        BigDecimal changeRate,
        long volume,
        BigDecimal tradingValue,
        BigDecimal marketCap,
        BigDecimal volumeRatio
) {

    /** 외부 순위가 준 종목에 우리 쪽 식별자를 붙인다. 찾지 못했으면 둘 다 {@code null}로 둔다. */
    public StockMover identifiedAs(Long stockId, IndustryCode industry) {
        return new StockMover(stockId, ticker, name, industry, price, changeRate, volume,
                tradingValue, marketCap, volumeRatio);
    }
}
