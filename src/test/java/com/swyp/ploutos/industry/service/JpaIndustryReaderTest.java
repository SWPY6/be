package com.swyp.ploutos.industry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.StockIndustries;
import com.swyp.ploutos.industry.repository.IndustryRepository;
import com.swyp.ploutos.industry.repository.StockIndustryRepository;

@ExtendWith(MockitoExtension.class)
class JpaIndustryReaderTest {

    @Mock
    private IndustryRepository industryRepository;

    @Mock
    private StockIndustryRepository stockIndustryRepository;

    @InjectMocks
    private JpaIndustryReader industryReader;

    @Test
    void 전체를_읽으면_가나다순으로_돌려준다() {
        // given
        given(industryRepository.findAll()).willReturn(List.of(
                new Industries(IndustryCode.CHEMICAL),
                new Industries(IndustryCode.AUTOMOBILE),
                new Industries(IndustryCode.TELECOM),
                new Industries(IndustryCode.CONSTRUCTION)));

        // when
        List<Industries> result = industryReader.readAll();

        // then
        assertThat(result).extracting(Industries::displayName)
                .containsExactly("건설", "자동차", "통신", "화학");
    }

    @Test
    void 코드로_읽으면_해당_산업을_돌려준다() {
        // given
        given(industryRepository.findByName(IndustryCode.STEEL))
                .willReturn(Optional.of(new Industries(IndustryCode.STEEL)));

        // when
        Industries result = industryReader.read(IndustryCode.STEEL);

        // then
        assertThat(result.name()).isEqualTo(IndustryCode.STEEL);
        assertThat(result.displayName()).isEqualTo("철강");
    }

    @Test
    void 시드가_없는_코드로_읽으면_예외를_던진다() {
        // given
        given(industryRepository.findByName(IndustryCode.ENERGY)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> industryReader.read(IndustryCode.ENERGY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENERGY")
                .hasMessageContaining("seed-industries.sql");
    }

    @Test
    void 산업에_매핑된_종목_식별자를_읽는다() {
        // given
        given(stockIndustryRepository.findStockIdsByIndustryId(1L)).willReturn(List.of(10L, 20L, 30L));

        // when
        List<Long> stockIds = industryReader.readStockIds(1L);

        // then
        assertThat(stockIds).containsExactly(10L, 20L, 30L);
    }

    @Test
    void 매핑이_없으면_빈_목록을_돌려준다() {
        // given
        given(stockIndustryRepository.findStockIdsByIndustryId(1L)).willReturn(List.of());

        // when
        List<Long> stockIds = industryReader.readStockIds(1L);

        // then
        assertThat(stockIds).isEmpty();
    }

    @Test
    void 종목의_산업을_읽으면_가나다순으로_돌려준다() {
        // given
        given(industryRepository.findByStockId(1L)).willReturn(List.of(
                new Industries(IndustryCode.STEEL),
                new Industries(IndustryCode.AUTOMOBILE),
                new Industries(IndustryCode.ENERGY)));

        // when
        List<Industries> result = industryReader.readByStockId(1L);

        // then
        assertThat(result).extracting(Industries::displayName)
                .containsExactly("에너지", "자동차", "철강");
    }

    @Test
    void 여러_종목의_산업을_한_번에_읽는다() {
        // given 종목 둘이 서로 다른 산업에 속한다
        given(industryRepository.findAll()).willReturn(List.of(
                new Industries(1L, IndustryCode.AUTOMOBILE),
                new Industries(2L, IndustryCode.STEEL)));
        given(stockIndustryRepository.findByStockIdIn(List.of(10L, 20L))).willReturn(List.of(
                link(10L, 1L), link(20L, 2L)));

        // when
        Map<Long, IndustryCode> codes = industryReader.readCodesByStockIds(List.of(10L, 20L));

        // then
        assertThat(codes).containsExactlyInAnyOrderEntriesOf(
                Map.of(10L, IndustryCode.AUTOMOBILE, 20L, IndustryCode.STEEL));
    }

    @Test
    void 종목에_산업이_여럿이면_가나다순_앞선_하나만_담는다() {
        // given 한 종목이 철강과 자동차에 모두 속한다
        given(industryRepository.findAll()).willReturn(List.of(
                new Industries(1L, IndustryCode.AUTOMOBILE),
                new Industries(2L, IndustryCode.STEEL)));
        given(stockIndustryRepository.findByStockIdIn(List.of(10L))).willReturn(List.of(
                link(10L, 2L), link(10L, 1L)));

        // when
        Map<Long, IndustryCode> codes = industryReader.readCodesByStockIds(List.of(10L));

        // then 목록의 산업 열은 한 칸이다 — "자동차"가 "철강"보다 앞선다
        assertThat(codes).containsExactly(Map.entry(10L, IndustryCode.AUTOMOBILE));
    }

    @Test
    void 산업에_연결되지_않은_종목은_결과에_없다() {
        // given 매핑이 하나도 없다
        given(industryRepository.findAll()).willReturn(List.of(
                new Industries(1L, IndustryCode.AUTOMOBILE)));
        given(stockIndustryRepository.findByStockIdIn(List.of(10L))).willReturn(List.of());

        // when
        Map<Long, IndustryCode> codes = industryReader.readCodesByStockIds(List.of(10L));

        // then 미분류 종목도 목록에 실리므로 그 자체가 정상이다
        assertThat(codes).isEmpty();
    }

    @Test
    void 식별자가_비어_있으면_조회하지_않는다() {
        // given

        // when
        Map<Long, IndustryCode> codes = industryReader.readCodesByStockIds(List.of());

        // then
        assertThat(codes).isEmpty();
        then(stockIndustryRepository).should(never()).findByStockIdIn(any());
    }

    /** 식별자는 DB가 정하므로 직접 만든 엔티티에는 없다. 조인 키라서 여기서 채워 준다. */
    private static StockIndustries link(Long stockId, Long industryId) {
        return new StockIndustries() {
            @Override
            public Long stockId() {
                return stockId;
            }

            @Override
            public Long industryId() {
                return industryId;
            }
        };
    }
}
