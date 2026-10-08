package com.swyp.ploutos.stock.movers.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.service.IndustryReader;
import com.swyp.ploutos.stock.StockWithMarket;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.StockMover;
import com.swyp.ploutos.stock.movers.VolumeRatio;
import com.swyp.ploutos.stock.service.StockListReader;
import com.swyp.ploutos.stock.snapshot.StockSnapshot;
import com.swyp.ploutos.stock.snapshot.service.StockSnapshotReader;

import lombok.RequiredArgsConstructor;

/**
 * 주요 변동 종목 목록을 만든다. 조건과 산업 필터에 따라 <b>두 경로 중 하나</b>로 간다.
 *
 * <pre>
 * 산업 필터 없는 순위  →  외부 순위 API   (모집단이 전 종목이라 우리가 셀 수 없다)
 * 산업 필터 있는 순위  →  저장된 스냅샷   (외부는 우리 산업 분류를 모른다)
 * 전체 종목           →  저장된 스냅샷
 * </pre>
 *
 * <p><b>필터를 결과에 걸지 않는다.</b> 외부 순위 30건을 받아 산업으로 거르면 특정 산업 종목이
 * 몇 개 들어올지 보장할 수 없어 목록이 비어 버린다. 산업은 모집단을 좁히는 조건이므로
 * 그 경우엔 우리가 아는 종목으로 처음부터 다시 센다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StockMoverService {

    private final MoverRankingProvider rankingProvider;
    private final StockSnapshotReader snapshotReader;
    private final StockListReader stockListReader;
    private final IndustryReader industryReader;

    public StockMoverDetail read(Country country, MoverCondition condition, IndustryCode industry,
            String query) {
        List<StockMover> stocks = usesRanking(condition, industry)
                ? fromRanking(country, condition)
                : fromSnapshots(country, condition, industry);
        return new StockMoverDetail(condition, matching(stocks, query));
    }

    /** 외부 순위로 답할 수 있는 요청인지. 산업이 붙으면 모집단이 우리 것이라 아니다. */
    private static boolean usesRanking(MoverCondition condition, IndustryCode industry) {
        return condition.isRanking() && industry == null;
    }

    /** 외부 순위가 준 종목에 우리 식별자와 산업을 붙인다. 찾지 못해도 목록에서 빼지 않는다. */
    private List<StockMover> fromRanking(Country country, MoverCondition condition) {
        List<StockMover> ranked = rankingProvider.rank(country, condition);
        Map<String, StockWithMarket> matched = matchByTicker(ranked, country);
        Map<Long, IndustryCode> industries = industryReader.readCodesByStockIds(
                matched.values().stream().map(StockWithMarket::stockId).toList());
        return ranked.stream()
                .map(mover -> identify(mover, matched.get(mover.ticker()), industries))
                .toList();
    }

    private static StockMover identify(StockMover mover, StockWithMarket stock,
            Map<Long, IndustryCode> industries) {
        if (stock == null) {
            return mover;
        }
        return mover.identifiedAs(stock.stockId(), industries.get(stock.stockId()));
    }

    /**
     * 종목코드로 우리 종목을 찾는다. 키는 <b>종목코드와 거래소</b>다 — 같은 코드가 여러 시장에
     * 속할 수 있고({@code AAPL}이 NASDAQ·S&amp;P500), 외부 순위가 주는 값은 거래소다.
     *
     * <p>한 코드에 둘 이상이 걸리면 넣지 않는다. 틀린 종목 상세로 보내는 것보다 비워 두는 쪽이 낫다.
     */
    private Map<String, StockWithMarket> matchByTicker(List<StockMover> movers, Country country) {
        List<String> tickers = movers.stream().map(StockMover::ticker).toList();
        return stockListReader.readAllByTickers(tickers).stream()
                .filter(stock -> stock.country() == country)
                .collect(Collectors.groupingBy(StockWithMarket::ticker))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() == 1)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst()));
    }

    /**
     * 저장된 스냅샷으로 목록을 만든다. 외부 시세를 부르지 않는다 — 산업 흐름 갱신기가 이미
     * 받아 둔 값이다.
     */
    private List<StockMover> fromSnapshots(Country country, MoverCondition condition,
            IndustryCode industry) {
        List<StockSnapshot> snapshots = snapshotsOf(country, industry);
        Map<Long, StockWithMarket> stocks = stockListReader
                .readAll(snapshots.stream().map(StockSnapshot::stockId).toList()).stream()
                .filter(stock -> stock.country() == country)
                .collect(Collectors.toMap(StockWithMarket::stockId, Function.identity()));
        Map<Long, IndustryCode> industries =
                industryReader.readCodesByStockIds(List.copyOf(stocks.keySet()));
        return snapshots.stream()
                .filter(snapshot -> stocks.containsKey(snapshot.stockId()))
                .map(snapshot -> toMover(snapshot, stocks.get(snapshot.stockId()), industries))
                .filter(mover -> passes(mover, condition))
                .sorted(order(condition))
                .toList();
    }

    /** 산업이 지정되면 그 산업의 종목만, 아니면 저장된 전부. 국가는 뒤에서 종목으로 거른다. */
    private List<StockSnapshot> snapshotsOf(Country country, IndustryCode industry) {
        if (industry == null) {
            return snapshotReader.readAll();
        }
        return snapshotReader.read(
                industryReader.readStockIds(industryReader.read(industry).industryId(), country));
    }

    private static StockMover toMover(StockSnapshot snapshot, StockWithMarket stock,
            Map<Long, IndustryCode> industries) {
        return new StockMover(stock.stockId(), stock.ticker(), stock.name(),
                industries.get(stock.stockId()), snapshot.price(), snapshot.changeRate(),
                snapshot.volume(), snapshot.tradingValue(), snapshot.marketCap(),
                volumeRatio(snapshot));
    }

    private static BigDecimal volumeRatio(StockSnapshot snapshot) {
        if (snapshot.averageVolume20d() == null) {
            return null;
        }
        return VolumeRatio.of(snapshot.volume(), snapshot.averageVolume20d()).orElse(null);
    }

    private static boolean passes(StockMover mover, MoverCondition condition) {
        return switch (condition) {
            case ALL -> true;
            case RISING -> mover.changeRate().signum() > 0;
            case FALLING -> mover.changeRate().signum() < 0;
            // "2배 이상"으로 거르지 않는다. 장중에는 분자가 당일 누적이라 배수가 거의 언제나
            // 1보다 작아 목록이 빈다. 배수를 잰 종목을 큰 순으로 보여 준다.
            case VOLUME_SURGE -> VolumeRatio.measurable(mover.volumeRatio());
        };
    }

    /** 전체 종목은 시가총액 내림차순이다. 조건이 없으면 큰 종목부터 보여 주는 것이 자연스럽다. */
    private static Comparator<StockMover> order(MoverCondition condition) {
        return switch (condition) {
            case ALL -> nullsLast(StockMover::marketCap);
            case RISING -> Comparator.comparing(StockMover::changeRate).reversed();
            case FALLING -> Comparator.comparing(StockMover::changeRate);
            case VOLUME_SURGE -> nullsLast(StockMover::volumeRatio);
        };
    }

    private static Comparator<StockMover> nullsLast(
            Function<StockMover, BigDecimal> value) {
        return Comparator.comparing(value, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /**
     * 검색어로 좁힌다. 종목명·종목코드 중 하나에 들어 있으면 남긴다(RQ-0705).
     * 비어 있으면 거르지 않는다.
     */
    private static List<StockMover> matching(List<StockMover> movers, String query) {
        return Optional.ofNullable(query)
                .map(String::trim)
                .filter(trimmed -> !trimmed.isEmpty())
                .map(trimmed -> trimmed.toLowerCase(Locale.ROOT))
                .map(keyword -> movers.stream().filter(mover -> contains(mover, keyword)).toList())
                .orElse(movers);
    }

    private static boolean contains(StockMover mover, String keyword) {
        return mover.name().toLowerCase(Locale.ROOT).contains(keyword)
                || mover.ticker().toLowerCase(Locale.ROOT).contains(keyword);
    }
}
