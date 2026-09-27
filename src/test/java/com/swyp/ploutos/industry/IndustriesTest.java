package com.swyp.ploutos.industry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.IndustryCode;

class IndustriesTest {

    @Test
    void 엔티티는_표시명을_위임한다() {
        // given
        Industries industry = new Industries(IndustryCode.AUTOMOBILE);

        // when
        String displayName = industry.displayName();

        // then
        assertThat(displayName).isEqualTo("자동차");
    }

}
