package com.swyp.ploutos.stock.price.kis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Exchange;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.price.DailyPrice;
import com.swyp.ploutos.stock.price.service.DailyPriceProvider;

import lombok.RequiredArgsConstructor;

/**
 * KIS 기간별 시세 API로 일봉을 받는다. 한 번에 최대 100건이므로 구간을 거슬러 올라가며 반복한다.
 */
@Component
@RequiredArgsConstructor
class KisDailyPriceProvider implements DailyPriceProvider {

    static final String DOMESTIC_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    static final String DOMESTIC_TR_ID = "FHKST03010100";
    static final String OVERSEAS_PATH = "/uapi/overseas-price/v1/quotations/dailyprice";
    static final String OVERSEAS_TR_ID = "HHDFS76240000";
    static final int PAGE_SIZE = 100;

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisApiClient kisApiClient;

    @Override
    public List<DailyPrice> fetch(StockWithMarket stock, LocalDate from, LocalDate to) {
        if (stock.exchange().isDomestic()) {
            return fetchDomestic(stock.ticker(), from, to);
        }
        return fetchOverseas(exchangeCode(stock.exchange()), stock.ticker(), from, to);
    }

    private List<DailyPrice> fetchDomestic(String ticker, LocalDate from, LocalDate to) {
        List<DailyPrice> result = new ArrayList<>();
        LocalDate end = to;
        while (!end.isBefore(from)) {
            KisDomesticDailyPriceResponse response = kisApiClient.get(DOMESTIC_PATH, DOMESTIC_TR_ID, Map.of(
                    "FID_COND_MRKT_DIV_CODE", "J",
                    "FID_INPUT_ISCD", ticker,
                    "FID_INPUT_DATE_1", from.format(DATE),
                    "FID_INPUT_DATE_2", end.format(DATE),
                    "FID_PERIOD_DIV_CODE", "D",
                    "FID_ORG_ADJ_PRC", "0"
            ), KisDomesticDailyPriceResponse.class);
            List<DailyPrice> page = response.toDailyPrices();
            if (page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < PAGE_SIZE) {
                break;
            }
            end = oldestTradeAt(page).minusDays(1);
        }
        return within(result, from, to);
    }

    private List<DailyPrice> fetchOverseas(String exchangeCode, String ticker, LocalDate from, LocalDate to) {
        List<DailyPrice> result = new ArrayList<>();
        LocalDate baseDate = to;
        while (!baseDate.isBefore(from)) {
            KisOverseasDailyPriceResponse response = kisApiClient.get(OVERSEAS_PATH, OVERSEAS_TR_ID, Map.of(
                    "AUTH", "",
                    "EXCD", exchangeCode,
                    "SYMB", ticker,
                    "GUBN", "0",
                    "BYMD", baseDate.format(DATE),
                    "MODP", "1"
            ), KisOverseasDailyPriceResponse.class);
            List<DailyPrice> page = response.toDailyPrices();
            if (page.isEmpty()) {
                break;
            }
            result.addAll(page);
            if (page.size() < PAGE_SIZE) {
                break;
            }
            baseDate = oldestTradeAt(page).minusDays(1);
        }
        return within(result, from, to);
    }

    private static String exchangeCode(Exchange exchange) {
        return switch (exchange) {
            case NASDAQ -> "NAS";
            case NYSE -> "NYS";
            case KRX -> throw new IllegalArgumentException("국내 거래소는 해외 API로 조회하지 않는다");
        };
    }

    private static LocalDate oldestTradeAt(List<DailyPrice> page) {
        return page.stream().map(DailyPrice::tradeAt).min(Comparator.naturalOrder()).orElseThrow();
    }

    private static List<DailyPrice> within(List<DailyPrice> prices, LocalDate from, LocalDate to) {
        return prices.stream()
                .filter(price -> !price.tradeAt().isBefore(from) && !price.tradeAt().isAfter(to))
                .toList();
    }
}
