package com.swyp.ploutos.market.quote.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class IndicatorQuoteCachePropertiesTest {

    @Test
    void TTL이_0이하면_설정을_만들_수_없다() {
        // given 갱신 스케줄러가 없으므로 TTL이 0이면 매 요청이 KIS를 부른다

        // when & then
        assertThatThrownBy(() -> new IndicatorQuoteCacheProperties(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IndicatorQuoteCacheProperties(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void TTL을_초_단위로_읽는다() {
        // given
        IndicatorQuoteCacheProperties properties = new IndicatorQuoteCacheProperties(10);

        // when
        Duration ttl = properties.cacheTtl();

        // then
        assertThat(ttl).isEqualTo(Duration.ofSeconds(10));
    }
}
