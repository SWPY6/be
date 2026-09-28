package com.swyp.ploutos.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class IndustryCodeTest {

    @Test
    void 모든_산업이_한글_표시명을_가진다() {
        // given
        Map<IndustryCode, String> expected = Map.of(
                IndustryCode.AUTOMOBILE, "자동차",
                IndustryCode.CONSTRUCTION, "건설",
                IndustryCode.TRANSPORT, "운송",
                IndustryCode.RETAIL, "유통",
                IndustryCode.FOOD_BEVERAGE, "음식료",
                IndustryCode.TELECOM, "통신",
                IndustryCode.STEEL, "철강",
                IndustryCode.ENERGY, "에너지",
                IndustryCode.CHEMICAL, "화학");

        // when
        Map<IndustryCode, String> actual = Arrays.stream(IndustryCode.values())
                .collect(Collectors.toMap(code -> code, IndustryCode::displayName));

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void 표시명으로_정렬하면_가나다순이_된다() {
        // given
        List<IndustryCode> codes = Arrays.stream(IndustryCode.values()).collect(Collectors.toList());

        // when
        codes.sort(Comparator.comparing(IndustryCode::displayName));

        // then
        assertThat(codes).extracting(IndustryCode::displayName)
                .containsExactly("건설", "에너지", "운송", "유통", "음식료", "자동차", "철강", "통신", "화학");
    }

    @Test
    void 표시명은_비어있지_않고_서로_다르다() {
        // given
        IndustryCode[] codes = IndustryCode.values();

        // when
        List<String> displayNames = Arrays.stream(codes).map(IndustryCode::displayName).toList();

        // then
        assertThat(displayNames).doesNotContainNull().noneMatch(String::isBlank);
        assertThat(displayNames).doesNotHaveDuplicates();
    }

}
