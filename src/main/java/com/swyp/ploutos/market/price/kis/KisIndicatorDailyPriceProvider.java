package com.swyp.ploutos.market.price.kis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.price.service.IndicatorDailyPriceProvider;
import com.swyp.ploutos.stock.price.DailyPrice;

import lombok.RequiredArgsConstructor;

/**
 * KIS 기간별 시세 API로 지표 일봉을 받는다. 한 번에 오는 건수에 상한이 있어(국내 지수 50, 해외 지수·환율 100)
 * 구간을 거슬러 올라가며 반복한다.
 */
@Component
@RequiredArgsConstructor
class KisIndicatorDailyPriceProvider implements IndicatorDailyPriceProvider {

    static final String DOMESTIC_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice";
    static final String DOMESTIC_TR_ID = "FHKUP03500100";
    static final String OVERSEAS_PATH = "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice";
    static final String OVERSEAS_TR_ID = "FHKST03030100";
    static final String DOMESTIC_MARKET_CODE = "U";
    static final String OVERSEAS_INDEX_MARKET_CODE = "N";
    static final String EXCHANGE_RATE_MARKET_CODE = "X";

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisApiClient kisApiClient;

    @Override
    public List<DailyPrice> fetch(MarketIndicator indicator, LocalDate from, LocalDate to) {
        return switch (indicator.kind()) {
            case DOMESTIC_INDEX -> paginate(from, to,
                    (pageFrom, pageTo) -> domesticPage(indicator, pageFrom, pageTo));
            case OVERSEAS_INDEX -> paginate(from, to,
                    (pageFrom, pageTo) -> overseasPage(indicator, OVERSEAS_INDEX_MARKET_CODE, pageFrom, pageTo));
            case EXCHANGE_RATE -> paginate(from, to,
                    (pageFrom, pageTo) -> overseasPage(indicator, EXCHANGE_RATE_MARKET_CODE, pageFrom, pageTo));
        };
    }

    /**
     * 종료일을 가장 오래된 거래일의 전날로 옮기며 시작일까지 거슬러 올라간다.
     *
     * <p>받은 건수가 상한보다 적어도 멈추지 않는다. 해외 지수·환율 API는 상한보다 적게 주면서
     * 시작 쪽 행을 빼먹는다(2026-10-07 실측). 건수로 멈추면 빠진 앞부분을 다시 받지 않는다.
     *
     * <p>진행 판단은 요청한 {@code [from, end]} 안의 행으로만 한다. 늦은 행이 섞여 와도 {@code end}가
     * 반드시 줄어들어 같은 요청을 되풀이하지 않는다.
     */
    private List<DailyPrice> paginate(LocalDate from, LocalDate to,
            BiFunction<LocalDate, LocalDate, List<DailyPrice>> fetchPage) {
        List<DailyPrice> result = new ArrayList<>();
        LocalDate end = to;
        while (!end.isBefore(from)) {
            List<DailyPrice> page = within(fetchPage.apply(from, end), from, end);
            if (page.isEmpty()) {
                break;
            }
            result.addAll(page);
            LocalDate oldest = oldestTradeAt(page);
            if (!oldest.isAfter(from)) {
                break;
            }
            end = oldest.minusDays(1);
        }
        return result;
    }

    private List<DailyPrice> domesticPage(MarketIndicator indicator, LocalDate from, LocalDate to) {
        KisDomesticIndexDailyResponse response = kisApiClient.get(DOMESTIC_PATH, DOMESTIC_TR_ID,
                periodParams(DOMESTIC_MARKET_CODE, indicator.symbol(), from, to),
                KisDomesticIndexDailyResponse.class);
        return response.toDailyPrices();
    }

    private List<DailyPrice> overseasPage(
            MarketIndicator indicator, String marketCode, LocalDate from, LocalDate to) {
        KisOverseasChartDailyResponse response = kisApiClient.get(OVERSEAS_PATH, OVERSEAS_TR_ID,
                periodParams(marketCode, indicator.symbol(), from, to),
                KisOverseasChartDailyResponse.class);
        return response.toDailyPrices();
    }

    /** 두 API가 같은 파라미터 이름을 쓴다. 시장구분 코드만 다르다. */
    private static Map<String, String> periodParams(
            String marketCode, String symbol, LocalDate from, LocalDate to) {
        return Map.of(
                "FID_COND_MRKT_DIV_CODE", marketCode,
                "FID_INPUT_ISCD", symbol,
                "FID_INPUT_DATE_1", from.format(DATE),
                "FID_INPUT_DATE_2", to.format(DATE),
                "FID_PERIOD_DIV_CODE", "D"
        );
    }

    private static LocalDate oldestTradeAt(List<DailyPrice> page) {
        return page.stream().map(DailyPrice::tradeAt).min(Comparator.naturalOrder()).orElseThrow();
    }

    private static List<DailyPrice> within(List<DailyPrice> prices, LocalDate from, LocalDate to) {
        return prices.stream()
                .filter(price -> price.tradedBetween(from, to))
                .toList();
    }
}
