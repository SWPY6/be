package com.swyp.ploutos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
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

	private static final int REDIS_PORT = 6379;

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
			.withExposedPorts(REDIS_PORT);

	@DynamicPropertySource
	static void configureDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
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

	@Test
	void 테스트_Redis가_준비되면_PING에_응답한다(@Autowired StringRedisTemplate redisTemplate) {
		// given
		try (RedisConnection connection = redisTemplate.getRequiredConnectionFactory().getConnection()) {
			// when
			String reply = connection.ping();

			// then
			assertThat(reply).isEqualTo("PONG");
		}
	}

}
