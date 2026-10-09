package com.swyp.ploutos.stock.movers.kis;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;
import com.swyp.ploutos.stock.movers.VolumeRatio;
import com.swyp.ploutos.stock.movers.service.MoverRankingProvider;

import lombok.RequiredArgsConstructor;

/**
 * KIS 순위 API 어댑터. 모집단이 전 종목이라 우리가 셀 수 없는 조건을 KIS 가 센 결과로 답한다.
 *
 * <p>국내는 한 번에 30건이 상한이고 이어 받을 수 없어 <b>코스피·코스닥을 따로 불러</b> 60건을
 * 만든다. 해외는 거래소 단위라 <b>나스닥·뉴욕을 따로 불러</b> 합친다. 어느 쪽이든 받아 온 뒤
 * 다시 정렬한다 — 시장이 섞인 하나의 순위여야 한다.
 */
@Component
@RequiredArgsConstructor
class KisMoverRankingProvider implements MoverRankingProvider {

    private static final String FLUCTUATION_PATH = "/uapi/domestic-stock/v1/ranking/fluctuation";
    private static final String FLUCTUATION_TR_ID = "FHPST01700000";
    private static final String VOLUME_RANK_PATH = "/uapi/domestic-stock/v1/quotations/volume-rank";
    private static final String VOLUME_RANK_TR_ID = "FHPST01710000";
    private static final String OVERSEAS_UPDOWN_PATH = "/uapi/overseas-stock/v1/ranking/updown-rate";
    private static final String OVERSEAS_UPDOWN_TR_ID = "HHDFS76290000";
    private static final String OVERSEAS_GROWTH_PATH = "/uapi/overseas-stock/v1/ranking/trade-growth";
    private static final String OVERSEAS_GROWTH_TR_ID = "HHDFS76330000";

    /** 국내 시장 코드. 전체(0000)로 부르면 30건뿐이라 나눠 부른다. */
    private static final List<String> DOMESTIC_MARKETS = List.of("0001", "1001");

    /** 해외 거래소. 우리 {@code Exchange}에 있는 둘만 부른다. */
    private static final List<String> OVERSEAS_EXCHANGES = List.of("NAS", "NYS");

    private static final int DOMESTIC_LIMIT = 60;
    private static final int OVERSEAS_LIMIT = 100;

    /**
     * 제외 대상 10자리 코드. 순서는 투자위험/경고/주의 · 관리종목 · 정리매매 · 불성실공시 ·
     * 우선주 · 거래정지 · ETF · ETN · 신용주문불가 · SPAC 이다. ETF·ETN·스팩을 뺀다 —
     * 빼지 않으면 상위가 레버리지 ETF 로 채워지고, ETF 만 빼면 그 자리를 스팩이 채운다.
     */
    private static final String EXCLUDED = "0000001101";

    /** 해외 거래증가율의 기간. 5가 20일이고, 그 평균이 직전 20거래일 평균과 일치한다. */
    private static final String OVERSEAS_AVERAGE_DAYS = "5";

    private final KisApiClient kisApiClient;

    @Override
    public List<StockMover> rank(Country country, MoverCondition condition) {
        if (!condition.isRanking()) {
            throw new IllegalArgumentException("전체 종목은 외부 순위로 답하지 않는다: " + condition);
        }
        if (country == Country.KR) {
            return domestic(condition);
        }
        return overseas(condition);
    }

    private List<StockMover> domestic(MoverCondition condition) {
        if (condition == MoverCondition.VOLUME_SURGE) {
            return merge(DOMESTIC_MARKETS, this::domesticVolumeRank, condition, DOMESTIC_LIMIT);
        }
        return merge(DOMESTIC_MARKETS, market -> domesticFluctuation(market, condition),
                condition, DOMESTIC_LIMIT);
    }

    private List<StockMover> overseas(MoverCondition condition) {
        if (condition == MoverCondition.VOLUME_SURGE) {
            return merge(OVERSEAS_EXCHANGES, this::overseasTradeGrowth, condition, OVERSEAS_LIMIT);
        }
        return merge(OVERSEAS_EXCHANGES, exchange -> overseasUpDown(exchange, condition),
                condition, OVERSEAS_LIMIT);
    }

    /**
     * 나눠 부른 결과를 합쳐 하나의 순위로 만든다. 같은 종목이 두 호출에 걸리는 일은 없지만,
     * 티커로 한 번 걸러 응답에 같은 줄이 두 번 실리지 않게 한다.
     */
    private List<StockMover> merge(List<String> scopes, Function<String, List<StockMover>> fetch,
            MoverCondition condition, int limit) {
        Map<String, StockMover> unique = new LinkedHashMap<>();
        scopes.stream()
                .flatMap(scope -> fetch.apply(scope).stream())
                .forEach(mover -> unique.putIfAbsent(mover.ticker(), mover));
        return unique.values().stream()
                .filter(mover -> passes(mover, condition))
                .sorted(order(condition))
                .limit(limit)
                .toList();
    }

    /**
     * 조건에 맞지 않는 종목을 뺀다. 저장된 스냅샷으로 만드는 경로와 같은 규칙이어야 한다 —
     * 산업을 고르고 말고에 따라 같은 질문에 다른 답이 나오면 안 된다.
     *
     * <p><b>상승·하락은 부호로 거른다.</b> KIS 순위는 "상승률순 정렬"일 뿐 양수만 주지 않는다.
     * 시장 전체가 내린 날에는 1위조차 음수일 수 있어, 거르지 않으면 상승 TOP 에 내린 종목이 실린다.
     *
     * <p><b>급증은 "2배 이상"으로 거르지 않는다.</b> 장중에는 분자가 당일 누적이라 배수가 거의
     * 언제나 1보다 작고, 고정 숫자로 거르면 목록이 빈다. 배수를 잰 종목을 큰 순으로 줄 세운다.
     */
    private static boolean passes(StockMover mover, MoverCondition condition) {
        return switch (condition) {
            case RISING -> mover.changeRate().signum() > 0;
            case FALLING -> mover.changeRate().signum() < 0;
            case VOLUME_SURGE -> VolumeRatio.measurable(mover.volumeRatio());
            case ALL -> throw new IllegalArgumentException("전체 종목은 외부 순위로 답하지 않는다");
        };
    }

    private static Comparator<StockMover> order(MoverCondition condition) {
        return switch (condition) {
            case RISING -> Comparator.comparing(StockMover::changeRate).reversed();
            case FALLING -> Comparator.comparing(StockMover::changeRate);
            case VOLUME_SURGE -> Comparator.comparing(StockMover::volumeRatio,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case ALL -> throw new IllegalArgumentException("전체 종목은 외부 순위로 답하지 않는다");
        };
    }

    /**
     * {@code fid_input_cnt_1}은 반드시 {@code 0}이다. 이름이 "조회할 종목 수"지만 값을 주면
     * 건수는 그대로이고 <b>정렬이 무너진다</b> — 상승률 1위 자리에 하락 종목이 온다.
     */
    private List<StockMover> domesticFluctuation(String market, MoverCondition condition) {
        return kisApiClient.get(FLUCTUATION_PATH, FLUCTUATION_TR_ID, Map.ofEntries(
                Map.entry("fid_cond_mrkt_div_code", "J"),
                Map.entry("fid_cond_scr_div_code", "20170"),
                Map.entry("fid_input_iscd", market),
                Map.entry("fid_rank_sort_cls_code", condition == MoverCondition.RISING ? "0" : "1"),
                Map.entry("fid_input_cnt_1", "0"),
                Map.entry("fid_prc_cls_code", "0"),
                Map.entry("fid_input_price_1", ""),
                Map.entry("fid_input_price_2", ""),
                Map.entry("fid_vol_cnt", ""),
                Map.entry("fid_trgt_cls_code", "0"),
                Map.entry("fid_trgt_exls_cls_code", EXCLUDED),
                Map.entry("fid_div_cls_code", "0"),
                Map.entry("fid_rsfl_rate1", ""),
                Map.entry("fid_rsfl_rate2", "")
        ), KisFluctuationResponse.class).toMovers();
    }

    private List<StockMover> domesticVolumeRank(String market) {
        return kisApiClient.get(VOLUME_RANK_PATH, VOLUME_RANK_TR_ID, Map.ofEntries(
                Map.entry("FID_COND_MRKT_DIV_CODE", "J"),
                Map.entry("FID_COND_SCR_DIV_CODE", "20171"),
                Map.entry("FID_INPUT_ISCD", market),
                Map.entry("FID_DIV_CLS_CODE", "0"),
                Map.entry("FID_BLNG_CLS_CODE", "1"),
                Map.entry("FID_TRGT_CLS_CODE", "111111111"),
                Map.entry("FID_TRGT_EXLS_CLS_CODE", EXCLUDED),
                Map.entry("FID_INPUT_PRICE_1", ""),
                Map.entry("FID_INPUT_PRICE_2", ""),
                Map.entry("FID_VOL_CNT", ""),
                Map.entry("FID_INPUT_DATE_1", "")
        ), KisVolumeRankResponse.class).toMovers();
    }

    private List<StockMover> overseasUpDown(String exchange, MoverCondition condition) {
        return kisApiClient.get(OVERSEAS_UPDOWN_PATH, OVERSEAS_UPDOWN_TR_ID, Map.of(
                "AUTH", "",
                "EXCD", exchange,
                "NDAY", "0",
                "GUBN", condition == MoverCondition.RISING ? "1" : "0",
                "VOL_RANG", "0",
                "KEYB", ""
        ), KisOverseasUpDownResponse.class).toMovers();
    }

    private List<StockMover> overseasTradeGrowth(String exchange) {
        return kisApiClient.get(OVERSEAS_GROWTH_PATH, OVERSEAS_GROWTH_TR_ID, Map.of(
                "AUTH", "",
                "EXCD", exchange,
                "NDAY", OVERSEAS_AVERAGE_DAYS,
                "VOL_RANG", "0",
                "KEYB", ""
        ), KisOverseasTradeGrowthResponse.class).toMovers();
    }
}
