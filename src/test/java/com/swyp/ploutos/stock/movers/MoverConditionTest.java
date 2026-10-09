package com.swyp.ploutos.stock.movers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;

class MoverConditionTest {

    @Test
    void 순위_조건의_국내_상한은_60이다() {
        // given KIS 순위는 한 번에 30건이고 코스피·코스닥을 나눠 부른다

        // when, then
        assertThat(MoverCondition.RISING.limitIn(Country.KR)).isEqualTo(60);
        assertThat(MoverCondition.FALLING.limitIn(Country.KR)).isEqualTo(60);
        assertThat(MoverCondition.VOLUME_SURGE.limitIn(Country.KR)).isEqualTo(60);
    }

    @Test
    void 순위_조건의_해외_상한은_100이다() {
        // given KIS 가 한 번에 100건을 준다

        // when, then
        assertThat(MoverCondition.RISING.limitIn(Country.US)).isEqualTo(100);
        assertThat(MoverCondition.FALLING.limitIn(Country.US)).isEqualTo(100);
        assertThat(MoverCondition.VOLUME_SURGE.limitIn(Country.US)).isEqualTo(100);
    }

    @Test
    void 전체_종목의_상한은_국가와_무관하게_500이다() {
        // given 화면이 한 페이지 100개씩 최대 5페이지를 보여준다(RQ-0708)

        // when, then 외부 API 사정이 아니라 화면 사정에서 나온 값이라 국가를 가리지 않는다
        assertThat(MoverCondition.ALL.limitIn(Country.KR)).isEqualTo(500);
        assertThat(MoverCondition.ALL.limitIn(Country.US)).isEqualTo(500);
    }

    @Test
    void 전체_종목만_외부_순위로_답할_수_없다() {
        // given

        // when, then
        assertThat(MoverCondition.ALL.isRanking()).isFalse();
        assertThat(MoverCondition.RISING.isRanking()).isTrue();
        assertThat(MoverCondition.FALLING.isRanking()).isTrue();
        assertThat(MoverCondition.VOLUME_SURGE.isRanking()).isTrue();
    }
}
