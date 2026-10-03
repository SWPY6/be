package com.swyp.ploutos.user.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.swyp.ploutos.common.config.SecurityConfig;
import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.user.service.LoginService;
import com.swyp.ploutos.user.service.LoginUser;

/**
 * 실제 SecurityConfig 필터 체인을 태워 세션 기반 로그인 흐름을 검증한다.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final LoginUser USER = new LoginUser(1L, "user", "유저");

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockitoBean
    private LoginService loginService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 로그인에_성공하면_사용자_정보를_반환한다() throws Exception {
        // given
        given(loginService.login("user")).willReturn(USER);

        // when & then
        mockMvc.perform(login("user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.loginId").value("user"))
                .andExpect(jsonPath("$.data.name").value("유저"));
    }

    @Test
    void 로그인한_세션으로_조회하면_같은_사용자가_나온다() throws Exception {
        // given
        given(loginService.login("user")).willReturn(USER);
        MockHttpSession session = loginSession("user");

        // when & then
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.loginId").value("user"));
    }

    @Test
    void 로그인할_수_없는_사용자면_401과_P008을_반환한다() throws Exception {
        // given
        given(loginService.login("nobody")).willThrow(new BusinessException(ErrorCode.LOGIN_FAILED));

        // when & then
        mockMvc.perform(login("nobody"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("P008"));
    }

    @Test
    void loginId가_비어_있으면_400과_P001을_반환한다() throws Exception {
        // given
        String loginId = " ";

        // when & then
        mockMvc.perform(login(loginId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"))
                .andExpect(jsonPath("$.error.errors[0].field").value("loginId"));
    }

    @Test
    void 로그인하지_않고_조회하면_401을_반환한다() throws Exception {
        // given
        String path = "/api/v1/auth/me";

        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃한_세션으로_조회하면_401을_반환한다() throws Exception {
        // given
        given(loginService.login("user")).willReturn(USER);
        MockHttpSession session = loginSession("user");

        // when
        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isNoContent());

        // then
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인하지_않아도_로그아웃은_204를_반환한다() throws Exception {
        // given
        String path = "/api/v1/auth/logout";

        // when & then
        mockMvc.perform(post(path))
                .andExpect(status().isNoContent());
    }

    @Test
    void 로그인하지_않아도_조회_경로는_인증을_요구하지_않는다() throws Exception {
        // given 이 슬라이스에 없는 컨트롤러 경로라 404가 나면 보안 필터를 통과한 것이다
        String path = "/api/v1/stocks/1/quote";

        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession loginSession(String loginId) throws Exception {
        return (MockHttpSession) mockMvc.perform(login(loginId))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private static RequestBuilder login(String loginId) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\": \"" + loginId + "\"}");
    }
}
