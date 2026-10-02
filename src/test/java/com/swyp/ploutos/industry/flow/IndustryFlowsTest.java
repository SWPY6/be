package com.swyp.ploutos.industry.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.Country;

class IndustryFlowsTest {

    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 8, 12, 14, 31);

    @Test
    void 상한만큼_주면_대표_종목이_모두_저장된다() {
        // given 상한을 올리면서 저장 자리를 늘리지 않으면 뒤쪽 종목이 조용히 사라진다
        IndustryFlowSnapshot snapshot = snapshotWith(IndustryFlowSnapshot.MAJOR_STOCK_LIMIT);

        // when
        IndustryFlows flow = new IndustryFlows(1L, Country.KR, snapshot, CALCULATED_AT);

        // then
        assertThat(flow.majorStocks()).isEqualTo(snapshot.majorStocks());
    }

    @Test
    void 상한을_넘으면_거부한다() {
        // given
        IndustryFlowSnapshot snapshot = snapshotWith(IndustryFlowSnapshot.MAJOR_STOCK_LIMIT + 1);

        // when & then
        assertThatThrownBy(() -> new IndustryFlows(1L, Country.KR, snapshot, CALCULATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static IndustryFlowSnapshot snapshotWith(int majorStockCount) {
        List<MajorStock> majorStocks = IntStream.range(0, majorStockCount)
                .mapToObj(index -> new MajorStock(
                        (long) index, "00000" + index, "종목" + index, new BigDecimal("1.0" + index)))
                .toList();
        return new IndustryFlowSnapshot(new BigDecimal("1.23"), majorStockCount, majorStockCount, 0,
                null, majorStocks);
    }
}
