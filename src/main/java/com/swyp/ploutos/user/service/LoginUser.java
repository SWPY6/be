package com.swyp.ploutos.user.service;

import java.io.Serializable;

import com.swyp.ploutos.user.Users;

/**
 * 세션에 저장하는 로그인 사용자 정보. 세션 저장소를 Redis로 옮겨도 직렬화되도록 Serializable이다.
 */
public record LoginUser(Long userId, String loginId, String name) implements Serializable {

    static LoginUser from(Users user) {
        return new LoginUser(user.userId(), user.loginId(), user.name());
    }
}
