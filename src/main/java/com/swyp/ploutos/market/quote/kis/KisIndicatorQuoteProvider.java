package com.swyp.ploutos.market.quote.kis;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.quote.IndicatorQuote;
import com.swyp.ploutos.market.quote.service.IndicatorQuoteProvider;

import lombok.RequiredArgsConstructor;

/**
 * KIS 현재값 API로 지표 시세를 받는다. 지표 종류에 따라 국내 지수 API와 해외 기간별 시세 API를 고른다.
 * 해외 지수·환율은 일봉 API를 기간 하루로 불러 output1의 현재값만 쓴다.
 */
@Component
@RequiredArgsConstructor
class KisIndicatorQuoteProvider implements IndicatorQuoteProvider {

    static final String DOMESTIC_PATH = "/uapi/domestic-stock/v1/quotations/inquire-index-price";
    static final String DOMESTIC_TR_ID = "FHPUP02100000";
    static final String OVERSEAS_PATH = "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice";
    static final String OVERSEAS_TR_ID = "FHKST03030100";
    static final String DOMESTIC_MARKET_CODE = "U";
    static final String OVERSEAS_INDEX_MARKET_CODE = "N";
    static final String EXCHANGE_RATE_MARKET_CODE = "X";

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final KisApiClient kisApiClient;
    private final Clock clock;

    @Override
    public IndicatorQuote fetch(MarketIndicator indicator) {
        return switch (indicator.kind()) {
            case DOMESTIC_INDEX -> fetchDomesticIndex(indicator);
            case OVERSEAS_INDEX -> fetchOverseas(indicator, OVERSEAS_INDEX_MARKET_CODE);
            case EXCHANGE_RATE -> fetchOverseas(indicator, EXCHANGE_RATE_MARKET_CODE);
        };
    }

    private IndicatorQuote fetchDomesticIndex(MarketIndicator indicator) {
        KisDomesticIndexPriceResponse response = kisApiClient.get(DOMESTIC_PATH, DOMESTIC_TR_ID, Map.of(
                "FID_COND_MRKT_DIV_CODE", DOMESTIC_MARKET_CODE,
                "FID_INPUT_ISCD", indicator.symbol()
        ), KisDomesticIndexPriceResponse.class);
        return response.toQuote(indicator, receivedAt(indicator));
    }

    /** 기간을 지표 타임존의 오늘 하루로 좁혀 일봉 목록 없이 현재값만 받는다. */
    private IndicatorQuote fetchOverseas(MarketIndicator indicator, String marketCode) {
        String today = LocalDate.ofInstant(clock.instant(), indicator.zoneId()).format(DATE);
        KisOverseasChartPriceResponse response = kisApiClient.get(OVERSEAS_PATH, OVERSEAS_TR_ID, Map.of(
                "FID_COND_MRKT_DIV_CODE", marketCode,
                "FID_INPUT_ISCD", indicator.symbol(),
                "FID_INPUT_DATE_1", today,
                "FID_INPUT_DATE_2", today,
                "FID_PERIOD_DIV_CODE", "D"
        ), KisOverseasChartPriceResponse.class);
        return response.toQuote(indicator, receivedAt(indicator));
    }

    /** 시세를 받은 시각. 지표 타임존 오프셋을 붙여 클라이언트가 몇 초 전 값인지 알게 한다. */
    private OffsetDateTime receivedAt(MarketIndicator indicator) {
        return OffsetDateTime.ofInstant(clock.instant(), indicator.zoneId());
    }
}
