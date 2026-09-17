package com.swyp.ploutos.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        GlobalExceptionHandlerIntegrationTest.TestController.class
})
class GlobalExceptionHandlerIntegrationTest {

    @MockitoBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void 비즈니스예외가_발생하면_상태코드와_JSON을_반환한다() throws Exception {
        // given
        String path = "/test/business-error";

        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                		.value("INVALID_INPUT_VALUE"))               
                .andExpect(jsonPath("$.message")
                        .value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
    
    @Test
    void 없는_경로를_요청하면_404와_JSON을_반환한다() throws Exception {
        // given
        String path = "/not-exist";

        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
    
    @Test
    void 필드검증에_실패하면_400과_필드오류를_반환한다() throws Exception {
        // given
        String requestBody = """
                {
                  "name": ""
                }
                """;
        // when & then
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
        
        		.andExpect(status().isBadRequest())
        		
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message")
                        .value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("name"))
                .andExpect(jsonPath("$.errors[0].reason")
                        .exists());
    }
    
    @RestController
    static class TestController {

        record CreateRequest(
                @NotBlank String name
        ) {
        }

        @GetMapping("/test/business-error")
        void businessError() {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        @PostMapping("/test/validation")
        void validation(
                @Valid @RequestBody CreateRequest request
        ) {
        }
    }
    
}
