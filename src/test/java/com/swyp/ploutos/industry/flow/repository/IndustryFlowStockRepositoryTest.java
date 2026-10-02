package com.swyp.ploutos.industry.flow.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.industry.flow.IndustryFlowStocks;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class IndustryFlowStockRepositoryTest {

    private static final Long FLOW_ID = 1L;
    private static final Long OTHER_FLOW_ID = 2L;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private IndustryFlowStockRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 같은_산업의_같은_자리에_두_종목을_넣을_수_없다() {
        // given
        repository.saveAndFlush(stock(FLOW_ID, 10L, "005380", 0));

        // when & then 자리 수가 스키마에서 사라진 대신, 자리 충돌은 DB 가 막는다
        assertThatThrownBy(() -> repository.saveAndFlush(stock(FLOW_ID, 20L, "000270", 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_산업이면_같은_자리를_쓸_수_있다() {
        // given
        repository.saveAndFlush(stock(FLOW_ID, 10L, "005380", 0));

        // when
        repository.saveAndFlush(stock(OTHER_FLOW_ID, 20L, "000270", 0));

        // then
        assertThat(repository.findByIndustryFlowIdIn(List.of(FLOW_ID, OTHER_FLOW_ID))).hasSize(2);
    }

    @Test
    void 여러_산업의_종목을_한_번에_읽는다() {
        // given 산업마다 따로 조회하면 9개 산업에 조회가 9번 된다
        repository.saveAll(List.of(
                stock(FLOW_ID, 10L, "005380", 0),
                stock(FLOW_ID, 20L, "000270", 1),
                stock(OTHER_FLOW_ID, 30L, "004020", 0)));
        repository.flush();

        // when
        List<IndustryFlowStocks> found = repository.findByIndustryFlowIdIn(List.of(FLOW_ID, OTHER_FLOW_ID));

        // then
        assertThat(found).hasSize(3);
        assertThat(found).extracting(IndustryFlowStocks::industryFlowId)
                .containsExactlyInAnyOrder(FLOW_ID, FLOW_ID, OTHER_FLOW_ID);
    }

    @Test
    void 요청하지_않은_산업의_종목은_읽지_않는다() {
        // given
        repository.saveAll(List.of(
                stock(FLOW_ID, 10L, "005380", 0),
                stock(OTHER_FLOW_ID, 30L, "004020", 0)));
        repository.flush();

        // when
        List<IndustryFlowStocks> found = repository.findByIndustryFlowIdIn(List.of(FLOW_ID));

        // then
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().ticker()).isEqualTo("005380");
    }

    @Test
    void 한_산업의_종목만_지운다() {
        // given 갱신은 지우고 다시 넣는다
        repository.saveAll(List.of(
                stock(FLOW_ID, 10L, "005380", 0),
                stock(FLOW_ID, 20L, "000270", 1),
                stock(OTHER_FLOW_ID, 30L, "004020", 0)));
        repository.flush();

        // when
        repository.deleteByIndustryFlowId(FLOW_ID);
        repository.flush();

        // then 다른 산업의 행은 남는다
        assertThat(repository.findByIndustryFlowIdIn(List.of(FLOW_ID))).isEmpty();
        assertThat(repository.findByIndustryFlowIdIn(List.of(OTHER_FLOW_ID))).hasSize(1);
    }

    @Test
    void 지운_자리에_다시_넣을_수_있다() {
        // given 갱신 한 바퀴를 두 번 도는 상황이다
        repository.saveAndFlush(stock(FLOW_ID, 10L, "005380", 0));
        repository.deleteByIndustryFlowId(FLOW_ID);
        repository.flush();

        // when 같은 자리에 다른 종목이 들어온다
        repository.saveAndFlush(stock(FLOW_ID, 20L, "000270", 0));

        // then
        assertThat(repository.findByIndustryFlowIdIn(List.of(FLOW_ID))).hasSize(1);
    }

    @Test
    void 현재가는_소수_넷째_자리까지_담는다() {
        // given 해외 종목의 센트 단위
        repository.saveAndFlush(new IndustryFlowStocks(FLOW_ID, 10L, "AAPL", "Apple",
                new BigDecimal("241.1234"), new BigDecimal("1.23"), 0));

        // when
        IndustryFlowStocks found = repository.findByIndustryFlowIdIn(List.of(FLOW_ID)).getFirst();

        // then
        assertThat(found.price()).isEqualByComparingTo("241.1234");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 생성되는_컬럼명이_서버에_적용할_DDL과_같다() {
        // given 서버 스키마는 사람이 db/ddl-industry-trend.sql 을 손으로 실행해 맞춘다.
        // 이름이 하나라도 어긋나면 Hibernate 가 그 컬럼을 쓰지 않아 값이 조용히 비거나 기동이 깨진다
        List<String> expected = List.of(
                "industry_flow_stock_id", "industry_flow_id", "stock_id",
                "ticker", "name", "price", "change_rate", "display_order");

        // when
        List<String> actual = entityManager.createNativeQuery(
                        "select column_name from information_schema.columns"
                                + " where table_schema = database()"
                                + " and table_name = 'industry_flow_stocks'")
                .getResultList();

        // then
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static IndustryFlowStocks stock(Long flowId, Long stockId, String ticker, int order) {
        return new IndustryFlowStocks(flowId, stockId, ticker, "종목" + stockId,
                new BigDecimal("248000.0000"), new BigDecimal("3.24"), order);
    }
}
