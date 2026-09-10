package com.swyp.ploutos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PloutosApplicationTests {

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

	@DynamicPropertySource
	static void configureDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
	}

	@Test
	void 테스트_DB가_준비되면_컨텍스트와_DB연결이_정상이다(@Autowired DataSource dataSource) throws SQLException {
		// given
		try (Connection connection = dataSource.getConnection()) {
			// when
			boolean valid = connection.isValid(5);

			// then
			assertThat(valid).isTrue();
		}
	}

}
