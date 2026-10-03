package com.swyp.ploutos.user.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
record LoginRequest(
        @Schema(description = "로그인 아이디", example = "user", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String loginId
) {
}
