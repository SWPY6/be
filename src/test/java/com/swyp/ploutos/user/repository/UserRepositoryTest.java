package com.swyp.ploutos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swyp.ploutos.common.enums.UserStatus;
import com.swyp.ploutos.user.Users;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void loginId로_사용자를_찾는다() {
        // given
        userRepository.saveAndFlush(new Users("user", "user@example.com", "password", "유저", UserStatus.ACTIVE));
        userRepository.saveAndFlush(new Users("other", "other@example.com", "password", "다른사람", UserStatus.ACTIVE));

        // when
        Optional<Users> found = userRepository.findByLoginId("user");

        // then
        assertThat(found).get().extracting(Users::name).isEqualTo("유저");
    }

    @Test
    void 없는_loginId면_비어_있다() {
        // given
        userRepository.saveAndFlush(new Users("user", "user@example.com", "password", "유저", UserStatus.ACTIVE));

        // when
        Optional<Users> found = userRepository.findByLoginId("nobody");

        // then
        assertThat(found).isEmpty();
    }
}
