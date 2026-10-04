package com.swyp.ploutos.disclosure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecFormLabelsTest {

    @Test
    void 주요_Form은_한글_라벨이_있다() {
        // when & then
        assertThat(SecFormLabels.labelOf("10-K")).isEqualTo("연간보고서");
        assertThat(SecFormLabels.labelOf("10-Q")).isEqualTo("분기보고서");
        assertThat(SecFormLabels.labelOf("8-K")).isEqualTo("수시공시(주요사항)");
        assertThat(SecFormLabels.labelOf("4")).isEqualTo("내부자 지분 변동");
        assertThat(SecFormLabels.labelOf("SCHEDULE 13G")).isEqualTo("5% 이상 지분 보고(단순 투자)");
    }

    @Test
    void 정정본은_기본_Form_라벨에_정정을_붙인다() {
        // when & then
        assertThat(SecFormLabels.labelOf("8-K/A")).isEqualTo("수시공시(주요사항)(정정)");
        assertThat(SecFormLabels.labelOf("SC 13G/A")).isEqualTo("5% 이상 지분 보고(단순 투자)(정정)");
    }

    @Test
    void 사전에_없는_Form은_라벨이_없다() {
        // when & then
        assertThat(SecFormLabels.labelOf("PX14A6G")).isNull();
        assertThat(SecFormLabels.labelOf("UPLOAD/A")).isNull();
        assertThat(SecFormLabels.labelOf(null)).isNull();
    }
}
