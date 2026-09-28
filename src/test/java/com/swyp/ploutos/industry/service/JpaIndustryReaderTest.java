package com.swyp.ploutos.industry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;
import com.swyp.ploutos.industry.repository.IndustryRepository;

@ExtendWith(MockitoExtension.class)
class JpaIndustryReaderTest {

    @Mock
    private IndustryRepository industryRepository;

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

}
