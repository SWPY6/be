package com.swyp.ploutos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.UserStatus;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.user.Users;
import com.swyp.ploutos.user.repository.UserRepository;

class LoginServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LoginService loginService = new LoginService(userRepository);

    @Test
    void 활성_사용자의_loginId면_로그인_사용자를_돌려준다() {
        // given
        given(userRepository.findByLoginId("user")).willReturn(Optional.of(user(UserStatus.ACTIVE)));

        // when
        LoginUser loginUser = loginService.login("user");

        // then
        assertThat(loginUser.loginId()).isEqualTo("user");
        assertThat(loginUser.name()).isEqualTo("유저");
    }

    @Test
    void 없는_loginId면_로그인에_실패한다() {
        // given
        given(userRepository.findByLoginId("nobody")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loginService.login("nobody"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 탈퇴한_사용자면_로그인에_실패한다() {
        // given
        given(userRepository.findByLoginId("user")).willReturn(Optional.of(user(UserStatus.WITHDRAWN)));

        // when & then
        assertThatThrownBy(() -> loginService.login("user"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    private static Users user(UserStatus status) {
        return new Users("user", "user@example.com", "password", "유저", status);
    }
}
