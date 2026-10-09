package com.swyp.ploutos.industry.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.repository.IndustryRepository;
import com.swyp.ploutos.industry.repository.StockIndustryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class JpaIndustryReader implements IndustryReader {

    private final IndustryRepository industryRepository;
    private final StockIndustryRepository stockIndustryRepository;

    @Override
    public List<Industries> readAll() {
        // 정렬을 SQL로 하지 않는 이유: industries.name 은 MySQL 네이티브 ENUM 이라
        // ORDER BY 가 선언 순서(정수)를 따르고, 문자열로 비교해도 영문 코드 순서다.
        // 둘 다 한글 표시명 순서와 무관하다. 9행이라 여기서 정렬해도 비용이 없다.
        return industryRepository.findAll().stream()
                .sorted(Comparator.comparing(Industries::displayName))
                .toList();
    }

    @Override
    public Industries read(IndustryCode code) {
        // 코드는 enum 이라 호출자가 틀릴 수 없다. 못 찾으면 시드를 안 넣은 것이므로 크게 실패한다.
        return industryRepository.findByName(code)
                .orElseThrow(() -> new IllegalStateException(
                        "산업 " + code + " 이 존재하지 않습니다. db/seed-industries.sql 을 실행했는지 확인하세요"));
    }

    @Override
    public List<Long> readStockIds(Long industryId) {
        return stockIndustryRepository.findStockIdsByIndustryId(industryId);
    }

    @Override
    public List<Long> readStockIds(Long industryId, Country country) {
        return stockIndustryRepository.findStockIdsByIndustryIdAndCountry(industryId, country);
    }

    @Override
    public List<Industries> readByStockId(Long stockId) {
        // readAll() 과 같은 이유로 정렬은 Java 에서 한다.
        return industryRepository.findByStockId(stockId).stream()
                .sorted(Comparator.comparing(Industries::displayName))
                .toList();
    }

    /**
     * 산업 9행을 먼저 읽어 매핑에 붙인다. 조인 대신 이렇게 하는 이유는 산업이 9행뿐이라
     * 두 번째 조회가 사실상 공짜이고, 표시명 정렬을 Java 에서 해야 하기 때문이다(위와 같은 이유).
     */
    @Override
    public Map<Long, IndustryCode> readCodesByStockIds(List<Long> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Industries> byId = readAll().stream()
                .collect(Collectors.toMap(Industries::industryId, Function.identity()));
        Map<Long, IndustryCode> codes = new HashMap<>();
        stockIndustryRepository.findByStockIdIn(stockIds).stream()
                .filter(link -> byId.containsKey(link.industryId()))
                .sorted(Comparator.comparing(link -> byId.get(link.industryId()).displayName()))
                .forEach(link -> codes.putIfAbsent(link.stockId(), byId.get(link.industryId()).name()));
        return codes;
    }
}
