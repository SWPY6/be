package com.swyp.ploutos.stock.summary;

import java.time.ZoneId;
import java.util.List;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.Currency;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.stock.StockWithMarket;

/**
 * 종목 상세 상단의 기본정보. 가격은 담지 않는다 — 프론트가 quote API와 조합한다.
 * {@code logoUrl}은 로고가 없으면 null이다. {@code industries}는 한글 표시명 가나다순이며 연결이 없으면 빈 목록이다.
 */
public record StockSummary(
        Long stockId,
        String name,
        String ticker,
        String logoUrl,
        Country market,
        Currency currency,
        List<IndustryCode> industries
) {

    public static StockSummary from(StockWithMarket stock, List<Industries> industries) {
        return new StockSummary(
                stock.stockId(),
                stock.name(),
                stock.ticker(),
                stock.imgUrl(),
                stock.country(),
                stock.currency(),
                industries.stream().map(Industries::name).toList()
        );
    }

    /** 시장의 현지 시간대. 고정 UTC 오프셋이 아니라 IANA 시간대다. */
    public ZoneId timezone() {
        return market.zoneId();
    }
}
