package com.swyp.ploutos.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.swyp.ploutos.common.enums.UserStatus;

class UsersTest {

    @Test
    void 활성_사용자는_로그인할_수_있다() {
        // given
        Users user = user(UserStatus.ACTIVE);

        // when
        boolean canLogin = user.canLogin();

        // then
        assertThat(canLogin).isTrue();
    }

    @Test
    void 비활성_사용자는_로그인할_수_없다() {
        // given
        Users user = user(UserStatus.INACTIVE);

        // when
        boolean canLogin = user.canLogin();

        // then
        assertThat(canLogin).isFalse();
    }

    @Test
    void 탈퇴한_사용자는_로그인할_수_없다() {
        // given
        Users user = user(UserStatus.WITHDRAWN);

        // when
        boolean canLogin = user.canLogin();

        // then
        assertThat(canLogin).isFalse();
    }

    private static Users user(UserStatus status) {
        return new Users("user", "user@example.com", "password", "유저", status);
    }
}
