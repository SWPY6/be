package com.swyp.ploutos.stock.quote.kis;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.quote.PriceTiming;
import com.swyp.ploutos.stock.quote.Quote;
import com.swyp.ploutos.stock.quote.service.QuoteProvider;

import lombok.RequiredArgsConstructor;

/** KIS 현재가 API로 시세 스냅샷을 받는다. 거래소에 따라 국내·해외 API를 고른다. */
@Component
@RequiredArgsConstructor
class KisQuoteProvider implements QuoteProvider {

    static final String DOMESTIC_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    static final String DOMESTIC_TR_ID = "FHKST01010100";
    static final String OVERSEAS_PATH = "/uapi/overseas-price/v1/quotations/price-detail";
    static final String OVERSEAS_TR_ID = "HHDFS76200200";

    private final KisApiClient kisApiClient;
    private final Clock clock;

    @Override
    public Quote fetch(StockWithMarket stock) {
        if (stock.isDomestic()) {
            return fetchDomestic(stock);
        }
        return fetchOverseas(stock);
    }

    private Quote fetchDomestic(StockWithMarket stock) {
        KisDomesticPriceResponse response = kisApiClient.get(DOMESTIC_PATH, DOMESTIC_TR_ID, Map.of(
                "FID_COND_MRKT_DIV_CODE", "J",
                "FID_INPUT_ISCD", stock.ticker()
        ), KisDomesticPriceResponse.class);
        return response.toQuote(stock.currency(), receivedAt(stock), PriceTiming.of(stock.country()));
    }

    private Quote fetchOverseas(StockWithMarket stock) {
        KisOverseasPriceResponse response = kisApiClient.get(OVERSEAS_PATH, OVERSEAS_TR_ID, Map.of(
                "AUTH", "",
                "EXCD", exchangeCode(stock.exchange()),
                "SYMB", stock.ticker()
        ), KisOverseasPriceResponse.class);
        return response.toQuote(stock.currency(), receivedAt(stock), PriceTiming.of(stock.country()));
    }

    /** 시세를 받은 시각. 시장 타임존 오프셋을 붙여 클라이언트가 몇 초 전 값인지 알게 한다. */
    private OffsetDateTime receivedAt(StockWithMarket stock) {
        return stock.localTimeAt(clock.instant());
    }

    private static String exchangeCode(Exchange exchange) {
        return switch (exchange) {
            case NASDAQ -> "NAS";
            case NYSE -> "NYS";
            case KRX -> throw new IllegalArgumentException("국내 거래소는 해외 API로 조회하지 않는다");
        };
    }
}
