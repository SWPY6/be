package com.swyp.ploutos.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.user.service.LoginService;
import com.swyp.ploutos.user.service.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증", description = "loginId 기반 로그인·로그아웃. 로그인 상태는 세션 쿠키(JSESSIONID)로 유지한다. "
        + "프론트는 요청마다 credentials: 'include'를 붙여야 한다.")
@RestController
@RequiredArgsConstructor
class AuthController {

    private final LoginService loginService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @Operation(
            summary = "로그인",
            description = "loginId만으로 로그인한다(비밀번호 없음, 임시 방식). 성공하면 세션 쿠키를 내려준다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공. Set-Cookie: JSESSIONID"),
            @ApiResponse(responseCode = "400", description = "P001 loginId가 비어 있음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "InvalidInputValueException", "code": "P001", "message": "잘못된 입력값입니다.", "errors": [{"field": "loginId"}]}}
                                    """))),
            @ApiResponse(responseCode = "401", description = "P008 없는 loginId 또는 비활성 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "LoginFailedException", "code": "P008", "message": "로그인에 실패했습니다."}}
                                    """)))
    })
    @PostMapping("/api/v1/auth/login")
    ApiResult<LoginUserResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        LoginUser user = loginService.login(loginRequest.loginId());

        // 로그인 전 세션을 버리고 새로 발급한다. 세션 고정 공격을 막는다.
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ApiResult.of(LoginUserResponse.from(user));
    }

    @Operation(summary = "로그아웃", description = "세션을 무효화한다. 로그인하지 않았어도 204를 준다.")
    @ApiResponse(responseCode = "204", description = "성공. 본문 없음")
    @PostMapping("/api/v1/auth/logout")
    ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @Parameter(hidden = true) Authentication authentication
    ) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "현재 로그인 사용자", description = "새로고침 뒤 로그인 상태를 확인할 때 쓴다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 상태"),
            @ApiResponse(responseCode = "401", description = "비로그인. 본문 없음", content = @Content)
    })
    @GetMapping("/api/v1/auth/me")
    ApiResult<LoginUserResponse> me(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser user) {
        return ApiResult.of(LoginUserResponse.from(user));
    }
}
