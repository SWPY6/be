package com.swyp.ploutos;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트가 함께 쓰는 MySQL·Redis 컨테이너.
 *
 * <p>{@code @Testcontainers}와 {@code @Container} 대신 정적 초기화로 한 번만 띄운다.
 * 전자는 테스트 클래스마다 컨테이너를 내렸다 올려, 캐시된 스프링 컨텍스트가
 * 이미 사라진 포트를 가리키게 된다. 컨테이너는 JVM이 끝날 때 Ryuk이 정리한다.
 */
public abstract class IntegrationTestContainers {

    private static final int REDIS_PORT = 6379;

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(REDIS_PORT);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }
}
