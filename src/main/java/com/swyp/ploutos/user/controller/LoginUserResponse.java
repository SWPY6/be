package com.swyp.ploutos.user.controller;

import com.swyp.ploutos.user.service.LoginUser;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인한 사용자")
record LoginUserResponse(
        @Schema(description = "사용자 ID", example = "1") Long userId,
        @Schema(description = "로그인 아이디", example = "user") String loginId,
        @Schema(description = "이름", example = "유저") String name
) {

    static LoginUserResponse from(LoginUser user) {
        return new LoginUserResponse(user.userId(), user.loginId(), user.name());
    }
}
