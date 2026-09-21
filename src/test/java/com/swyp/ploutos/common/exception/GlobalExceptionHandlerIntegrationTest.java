package com.swyp.ploutos.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import com.swyp.ploutos.common.response.ApiResponse;

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
import org.springframework.web.bind.annotation.RequestParam;
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
                .andExpect(jsonPath("$.error.code")
                        .value("P001"))
                .andExpect(jsonPath("$.error.message")
                        .value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.error.errors").doesNotExist());
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
                .andExpect(jsonPath("$.error.code")
                        .value("P003"))
                .andExpect(jsonPath("$.error.message")
                        .value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.errors").doesNotExist());
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
                .andExpect(jsonPath("$.error.code")
                        .value("P001"))
                .andExpect(jsonPath("$.error.message")
                        .value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.error.errors[0].field")
                        .value("name"))
                .andExpect(jsonPath("$.error.errors[0].reason")
                        .doesNotExist());
    }

    @Test
    void 잘못된_JSON을_요청하면_400과_P001을_반환한다() throws Exception {
        // given
        String requestBody = """
                {
                  "name": "삼성전자",
                }
                """;

        // when & then
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"))
                .andExpect(jsonPath("$.error.message").value("잘못된 입력값입니다."))
                .andExpect(jsonPath("$.error.errors").doesNotExist());
    }

    @Test
    void 필수_파라미터를_누락하면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/test/request-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"))
                .andExpect(jsonPath("$.error.message").value("잘못된 입력값입니다."));
    }

    @Test
    void 파라미터_타입이_일치하지_않으면_400과_P001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/test/request-parameter")
                        .param("count", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("P001"))
                .andExpect(jsonPath("$.error.message").value("잘못된 입력값입니다."));
    }

    @Test
    void 지원하지_않는_HTTP_메서드를_요청하면_405와_P004를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/test/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("P004"))
                .andExpect(jsonPath("$.error.message").value("지원하지 않는 HTTP 메서드입니다."));
    }

    @Test
    void 지원하지_않는_미디어_타입을_요청하면_415와_P005를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("삼성전자"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("P005"))
                .andExpect(jsonPath("$.error.message").value("지원하지 않는 미디어 타입입니다."));
    }
    
    @Test
    void 성공하면_종목정보를_data에_담아_반환한다() throws Exception {
        // given
        String path = "/test/success";
        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":{\"id\":10,\"name\":\"삼성전자\"}}"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 주식이_없으면_필수_오류코드와_이름을_반환한다() throws Exception {
        // given
        String path = "/test/stock-error";
        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.name").value("StockNotFoundException"))
                .andExpect(jsonPath("$.error.code").value("P002"))
                .andExpect(jsonPath("$.error.message").value("주식을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.errors").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void 예상하지_못한_예외가_발생하면_500과_P006를_반환한다() throws Exception {
        // given
        String path = "/test/unexpected-error";

        // when & then
        mockMvc.perform(get(path))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("P006"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/success")
        ApiResponse<Map<String, Object>> success() {
            return ApiResponse.of(Map.of("id", 10, "name", "삼성전자"));
        }

        @GetMapping("/test/stock-error")
        void stockError() {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        }

        @GetMapping("/test/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("내부 상세 오류");
        }

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

        @GetMapping("/test/request-parameter")
        void requestParameter(@RequestParam("count") Integer count) {
        }
    }
    
}
