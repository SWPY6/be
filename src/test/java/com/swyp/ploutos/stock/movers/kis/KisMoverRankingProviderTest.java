package com.swyp.ploutos.stock.movers.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.external.kis.KisApiClient;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KisMoverRankingProviderTest {

    @Mock
    private KisApiClient kisApiClient;

    @InjectMocks
    private KisMoverRankingProvider provider;

    @Captor
    private ArgumentCaptor<Map<String, String>> params;

    @Test
    void 국내는_코스피와_코스닥을_따로_불러_합친다() {
        // given 시장마다 다른 종목이 온다
        givenFluctuation("0001", fluctuation("005380", "현대차", "1.00"));
        givenFluctuation("1001", fluctuation("035720", "카카오", "5.00"));

        // when
        List<StockMover> movers = provider.rank(Country.KR, MoverCondition.RISING);

        // then 한 번에 30건이 상한이라 나눠 부르고, 합쳐서 다시 정렬한다
        assertThat(movers).extracting(StockMover::ticker).containsExactly("035720", "005380");
    }

    @Test
    void 조회_건수를_0으로_보낸다() {
        // given
        givenFluctuation("0001", fluctuation("005380", "현대차", "1.00"));
        givenFluctuation("1001", fluctuation("035720", "카카오", "5.00"));

        // when
        provider.rank(Country.KR, MoverCondition.RISING);

        // then fid_input_cnt_1 에 값을 주면 정렬이 무너진다 — 반드시 0이다
        then(kisApiClient).should(org.mockito.Mockito.atLeastOnce())
                .get(any(), any(), params.capture(), eq(KisFluctuationResponse.class));
        assertThat(params.getValue()).containsEntry("fid_input_cnt_1", "0");
    }

    @Test
    void 하락은_정렬_코드와_방향이_모두_반대다() {
        // given
        givenFluctuation("0001", fluctuation("005380", "현대차", "-1.00"));
        givenFluctuation("1001", fluctuation("035720", "카카오", "-5.00"));

        // when
        List<StockMover> movers = provider.rank(Country.KR, MoverCondition.FALLING);

        // then 하락률이 큰 종목부터 온다
        assertThat(movers).extracting(StockMover::ticker).containsExactly("035720", "005380");
        then(kisApiClient).should(org.mockito.Mockito.atLeastOnce())
                .get(any(), any(), params.capture(), eq(KisFluctuationResponse.class));
        assertThat(params.getValue()).containsEntry("fid_rank_sort_cls_code", "1");
    }

    @Test
    void 거래량_급증은_두_배에_못_미쳐도_배수_순으로_싣는다() {
        // given 3배 종목과 1.5배 종목
        givenVolumeRank("0001", volumeRank("005380", "현대차", 30_000L, 10_000L));
        givenVolumeRank("1001", volumeRank("035720", "카카오", 15_000L, 10_000L));

        // when
        List<StockMover> movers = provider.rank(Country.KR, MoverCondition.VOLUME_SURGE);

        // then 장중에는 배수가 1보다 작은 것이 정상이라 고정 숫자로 거르지 않는다
        assertThat(movers).extracting(StockMover::ticker).containsExactly("005380", "035720");
    }

    @Test
    void 배수를_재지_못한_종목은_거래량_급증에서_뺀다() {
        // given 전일 거래량이 0이라 나눌 수 없다
        givenVolumeRank("0001", volumeRank("005380", "현대차", 30_000L, 0L));
        givenVolumeRank("1001", volumeRank("035720", "카카오", 15_000L, 10_000L));

        // when
        List<StockMover> movers = provider.rank(Country.KR, MoverCondition.VOLUME_SURGE);

        // then 줄을 세울 수 없는 종목은 뺀다
        assertThat(movers).extracting(StockMover::ticker).containsExactly("035720");
    }

    @Test
    void 국내_시가총액은_상장주식수와_현재가를_곱해_만든다() {
        // given 현재가 200원, 상장주식수 1000주
        givenVolumeRank("0001", new KisVolumeRankResponse("0", "", "", List.of(
                new KisVolumeRankResponse.Row("005380", "현대차", "200", "1.00",
                        "30000", "10000", "500", "1000"))));
        givenVolumeRank("1001", new KisVolumeRankResponse("0", "", "", List.of()));

        // when
        List<StockMover> movers = provider.rank(Country.KR, MoverCondition.VOLUME_SURGE);

        // then 응답에 시가총액 필드가 없어 직접 곱한다
        assertThat(movers.getFirst().marketCap()).isEqualByComparingTo("200000");
    }

    @Test
    void 해외는_나스닥과_뉴욕을_부른다() {
        // given
        givenUpDown("NAS", upDown("AAPL", "애플", "2.00"));
        givenUpDown("NYS", upDown("KO", "코카콜라", "7.00"));

        // when
        List<StockMover> movers = provider.rank(Country.US, MoverCondition.RISING);

        // then 거래소 단위 API 라 둘을 합친다
        assertThat(movers).extracting(StockMover::ticker).containsExactly("KO", "AAPL");
    }

    @Test
    void 해외_급증은_20거래일_평균을_쓴다() {
        // given
        givenTradeGrowth("NAS", tradeGrowth("AAPL", "애플", 30_000L, 10_000L));
        givenTradeGrowth("NYS", tradeGrowth("KO", "코카콜라", 15_000L, 10_000L));

        // when
        List<StockMover> movers = provider.rank(Country.US, MoverCondition.VOLUME_SURGE);

        // then NDAY=5 가 20일이다. 배수 큰 순으로 온다
        assertThat(movers).extracting(StockMover::ticker).containsExactly("AAPL", "KO");
        then(kisApiClient).should(org.mockito.Mockito.atLeastOnce())
                .get(any(), any(), params.capture(), eq(KisOverseasTradeGrowthResponse.class));
        assertThat(params.getValue()).containsEntry("NDAY", "5");
    }

    @Test
    void 전체_종목_조건은_외부_순위로_답하지_않는다() {
        // given 전체 종목은 모집단이 우리 것이다

        // when, then
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> provider.rank(Country.KR, MoverCondition.ALL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 외부_순위가_준_종목에는_우리_식별자가_없다() {
        // given
        givenFluctuation("0001", fluctuation("005380", "현대차", "1.00"));
        givenFluctuation("1001", new KisFluctuationResponse("0", "", "", List.of()));

        // when
        List<StockMover> movers = provider.rank(Country.KR, MoverCondition.RISING);

        // then KIS 는 우리 종목 마스터를 모른다 — 채우는 일은 서비스가 한다
        assertThat(movers.getFirst().stockId()).isNull();
        assertThat(movers.getFirst().industry()).isNull();
    }

    private void givenFluctuation(String market, KisFluctuationResponse response) {
        given(kisApiClient.get(any(), any(), argThatHas("fid_input_iscd", market),
                eq(KisFluctuationResponse.class))).willReturn(response);
    }

    private void givenVolumeRank(String market, KisVolumeRankResponse response) {
        given(kisApiClient.get(any(), any(), argThatHas("FID_INPUT_ISCD", market),
                eq(KisVolumeRankResponse.class))).willReturn(response);
    }

    private void givenUpDown(String exchange, KisOverseasUpDownResponse response) {
        given(kisApiClient.get(any(), any(), argThatHas("EXCD", exchange),
                eq(KisOverseasUpDownResponse.class))).willReturn(response);
    }

    private void givenTradeGrowth(String exchange, KisOverseasTradeGrowthResponse response) {
        given(kisApiClient.get(any(), any(), argThatHas("EXCD", exchange),
                eq(KisOverseasTradeGrowthResponse.class))).willReturn(response);
    }

    private static Map<String, String> argThatHas(String key, String value) {
        return org.mockito.ArgumentMatchers
                .argThat(map -> map != null && value.equals(map.get(key)));
    }

    private static KisFluctuationResponse fluctuation(String ticker, String name, String changeRate) {
        return new KisFluctuationResponse("0", "", "", List.of(
                new KisFluctuationResponse.Row(ticker, name, "1000", changeRate, "100")));
    }

    private static KisVolumeRankResponse volumeRank(String ticker, String name, long volume,
            long previousVolume) {
        return new KisVolumeRankResponse("0", "", "", List.of(
                new KisVolumeRankResponse.Row(ticker, name, "1000", "1.00",
                        String.valueOf(volume), String.valueOf(previousVolume), "500", "10")));
    }

    private static KisOverseasUpDownResponse upDown(String ticker, String name, String changeRate) {
        return new KisOverseasUpDownResponse("0", "", "", List.of(
                new KisOverseasUpDownResponse.Row(ticker, name, "100", changeRate, "100", "500")));
    }

    private static KisOverseasTradeGrowthResponse tradeGrowth(String ticker, String name,
            long volume, long averageVolume) {
        return new KisOverseasTradeGrowthResponse("0", "", "", List.of(
                new KisOverseasTradeGrowthResponse.Row(ticker, name, "100", "1.00",
                        String.valueOf(volume), String.valueOf(averageVolume), "500")));
    }
}
