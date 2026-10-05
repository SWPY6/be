package com.swyp.ploutos.disclosure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Exchange;

class IssuerCodeCollectorTest {

    @Test
    void 거래소와_티커를_매핑_키로_맞춰_모은다() {
        // given
        IssuerCodeCollector collector = new IssuerCodeCollector();

        // when
        collector.put(Exchange.KRX, "005930", "00126380");
        collector.put(Exchange.KRX, " 000660 ", "00164779");

        // then
        assertThat(collector.codes()).containsExactlyInAnyOrderEntriesOf(
                Map.of("KRX:005930", "00126380", "KRX:000660", "00164779")
        );
        assertThat(collector.conflictedCount()).isZero();
    }

    @Test
    void 같은_키에_같은_법인_ID가_다시_오면_그대로_둔다() {
        // given
        IssuerCodeCollector collector = new IssuerCodeCollector();

        // when
        collector.put(Exchange.KRX, "005930", "00126380");
        collector.put(Exchange.KRX, "005930", "00126380");

        // then
        assertThat(collector.codes()).containsExactlyEntriesOf(Map.of("KRX:005930", "00126380"));
        assertThat(collector.conflictedCount()).isZero();
    }

    @Test
    void 같은_키에_다른_법인_ID가_오면_그_키를_빼고_이후에도_넣지_않는다() {
        // given
        IssuerCodeCollector collector = new IssuerCodeCollector();

        // when
        collector.put(Exchange.KRX, "005930", "00126380");
        collector.put(Exchange.KRX, "005930", "09999999");
        collector.put(Exchange.KRX, "005930", "00126380");
        collector.put(Exchange.KRX, "000660", "00164779");

        // then
        assertThat(collector.codes()).containsExactlyEntriesOf(Map.of("KRX:000660", "00164779"));
        assertThat(collector.conflictedCount()).isEqualTo(1);
    }
}
