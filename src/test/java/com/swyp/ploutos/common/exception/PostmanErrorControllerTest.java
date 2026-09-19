package com.swyp.ploutos.common.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostmanErrorController.class)
@ActiveProfiles("postman")
class PostmanErrorControllerTest {

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void 성공하면_200과_data를_반환한다() throws Exception {
        // given
        String url = "/local-test/errors/success";
        // when
        var result = mockMvc.perform(get(url));
        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("삼성전자"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @WithMockUser
    @ParameterizedTest
    @CsvSource({"p001,400,P001", "p002,404,P002", "p003,404,P003", "p004,500,P004"})
    void 인증하면_각_오류응답을_반환한다(String path, int httpStatus, String code) throws Exception {
        // given
        String url = "/local-test/errors/" + path;
        // when
        var result = mockMvc.perform(get(url));
        // then
        result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    @Test
    void 인증하지_않으면_401을_반환한다() throws Exception {
        // given
        String url = "/local-test/errors/p001";
        // when
        var result = mockMvc.perform(get(url).header("Accept", "application/json"));
        // then
        result.andExpect(status().isUnauthorized());
    }
}
