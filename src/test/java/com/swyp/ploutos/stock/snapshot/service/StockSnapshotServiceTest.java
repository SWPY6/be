package com.swyp.ploutos.stock.snapshot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.swyp.ploutos.stock.snapshot.StockSnapshot;
import com.swyp.ploutos.stock.snapshot.StockSnapshots;
import com.swyp.ploutos.stock.snapshot.repository.StockSnapshotRepository;

@ExtendWith(MockitoExtension.class)
class StockSnapshotServiceTest {

    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 10, 9, 10, 0);

    @Mock
    private StockSnapshotRepository repository;

    @InjectMocks
    private StockSnapshotService service;

    @Captor
    private ArgumentCaptor<List<StockSnapshots>> saved;

    @Test
    void 처음_보는_종목은_새로_만든다() {
        // given 저장된 행이 없다
        given(repository.findByStockIdIn(List.of(10L))).willReturn(List.of());

        // when
        service.save(List.of(snapshot(10L, "1000")));

        // then
        then(repository).should().saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(StockSnapshots::stockId).containsExactly(10L);
        assertThat(saved.getValue().getFirst().price()).isEqualByComparingTo("1000");
    }

    @Test
    void 이미_있는_종목은_같은_행을_덮어쓴다() {
        // given 같은 종목의 옛 행이 있다
        StockSnapshots stored = new StockSnapshots(snapshot(10L, "1000"));
        given(repository.findByStockIdIn(List.of(10L))).willReturn(List.of(stored));

        // when 새 값으로 저장한다
        service.save(List.of(snapshot(10L, "2000")));

        // then 종목당 한 행만 남는다 — 이력을 쌓지 않는다
        then(repository).should().saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().getFirst()).isSameAs(stored);
        assertThat(stored.price()).isEqualByComparingTo("2000");
    }

    @Test
    void 기존_행을_종목마다_따로_읽지_않는다() {
        // given 종목 셋
        given(repository.findByStockIdIn(any())).willReturn(List.of());

        // when
        service.save(List.of(snapshot(10L, "1000"), snapshot(20L, "2000"), snapshot(30L, "3000")));

        // then 한 바퀴에 종목 수만큼 쿼리가 나가면 안 된다
        then(repository).should(org.mockito.Mockito.times(1)).findByStockIdIn(any());
    }

    @Test
    void 저장할_것이_없으면_조회도_하지_않는다() {
        // given

        // when
        service.save(List.of());

        // then
        then(repository).should(never()).findByStockIdIn(any());
        then(repository).should(never()).saveAll(any());
    }

    @Test
    void 저장된_값을_다시_읽는다() {
        // given
        given(repository.findByStockIdIn(List.of(10L)))
                .willReturn(List.of(new StockSnapshots(snapshot(10L, "1000"))));

        // when
        List<StockSnapshot> result = service.read(List.of(10L));

        // then
        assertThat(result).extracting(StockSnapshot::stockId).containsExactly(10L);
        assertThat(result.getFirst().price()).isEqualByComparingTo("1000");
    }

    @Test
    void 읽을_식별자가_비어_있으면_조회하지_않는다() {
        // given

        // when
        List<StockSnapshot> result = service.read(List.of());

        // then
        assertThat(result).isEmpty();
        then(repository).should(never()).findByStockIdIn(any());
    }

    private static StockSnapshot snapshot(Long stockId, String price) {
        return new StockSnapshot(stockId, new BigDecimal(price), new BigDecimal("1.00"),
                100L, null, null, null, CALCULATED_AT);
    }
}
