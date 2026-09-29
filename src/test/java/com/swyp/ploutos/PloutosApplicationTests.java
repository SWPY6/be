package com.swyp.ploutos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PloutosApplicationTests extends IntegrationTestContainers {

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
