package com.swyp.ploutos.industry.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.industry.Industries;

import jakarta.persistence.EntityManagerFactory;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class IndustryRepositoryTest {

    private static final Long STOCK_ID = 1L;
    private static final Long OTHER_STOCK_ID = 2L;

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private IndustryRepository industryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 같은_산업_연결이_중복되어도_한_번만_조회한다() {
        // given
        Industries automobile = industryRepository.saveAndFlush(new Industries(IndustryCode.AUTOMOBILE));
        link(STOCK_ID, automobile);
        link(STOCK_ID, automobile);

        // when
        List<Industries> industries = industryRepository.findByStockId(STOCK_ID);

        // then
        assertThat(industries).extracting(Industries::name).containsExactly(IndustryCode.AUTOMOBILE);
    }

    @Test
    void 다른_종목의_산업은_섞이지_않는다() {
        // given
        Industries steel = industryRepository.saveAndFlush(new Industries(IndustryCode.STEEL));
        Industries energy = industryRepository.saveAndFlush(new Industries(IndustryCode.ENERGY));
        link(STOCK_ID, steel);
        link(OTHER_STOCK_ID, energy);

        // when
        List<Industries> industries = industryRepository.findByStockId(STOCK_ID);

        // then
        assertThat(industries).extracting(Industries::name).containsExactly(IndustryCode.STEEL);
    }

    @Test
    void 연결된_산업이_없으면_빈_목록을_반환한다() {
        // given
        Industries steel = industryRepository.saveAndFlush(new Industries(IndustryCode.STEEL));
        link(OTHER_STOCK_ID, steel);

        // when
        List<Industries> industries = industryRepository.findByStockId(STOCK_ID);

        // then
        assertThat(industries).isEmpty();
    }

    @Test
    void 산업이_여러_개여도_쿼리_한_번으로_조회한다() {
        // given
        link(STOCK_ID, industryRepository.saveAndFlush(new Industries(IndustryCode.AUTOMOBILE)));
        link(STOCK_ID, industryRepository.saveAndFlush(new Industries(IndustryCode.STEEL)));
        link(STOCK_ID, industryRepository.saveAndFlush(new Industries(IndustryCode.ENERGY)));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // when
        List<Industries> industries = industryRepository.findByStockId(STOCK_ID);

        // then
        assertThat(industries).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    // StockIndustries 는 public 생성자가 없어 매핑 행은 SQL 로 넣는다.
    private void link(Long stockId, Industries industry) {
        jdbcTemplate.update("insert into stock_industries (stock_id, industry_id) values (?, ?)",
                stockId, industry.industryId());
    }
}
