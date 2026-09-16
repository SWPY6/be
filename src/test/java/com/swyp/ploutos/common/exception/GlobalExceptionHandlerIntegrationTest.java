package com.swyp.ploutos.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(controllers = GlobalExceptionHandlerIntegrationTest.FixtureController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandlerIntegrationTest.FixtureController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void 비즈니스예외가_발생하면_에러코드_상태와_JSON_바디를_반환한다() {
        // given
        MockMvcRequestBuilder request = mvc.get().uri("/fixture/business");

        // when
        MvcTestResult result = request.exchange();

        // then
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("INVALID_INPUT_VALUE"))
                .hasPathSatisfying("$.message", message -> message.assertThat().isEqualTo("잘못된 입력값입니다."))
                .hasPathSatisfying("$.errors", errors -> errors.assertThat().asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty());
    }

    @Test
    void 필드검증에_실패하면_400과_필드에러목록을_반환한다() {
        // given
        MockMvcRequestBuilder request = mvc.post().uri("/fixture")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": ""}
                        """);

        // when
        MvcTestResult result = request.exchange();

        // then
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("INVALID_INPUT_VALUE"))
                .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isEqualTo("name"));
    }

    @Test
    void 본문이_잘못된_JSON이면_400을_반환한다() {
        // given
        MockMvcRequestBuilder request = mvc.post().uri("/fixture")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json");

        // when
        MvcTestResult result = request.exchange();

        // then
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("BAD_REQUEST"));
    }

    @Test
    void 없는_경로를_요청하면_404_JSON_바디를_반환한다() {
        // given
        MockMvcRequestBuilder request = mvc.get().uri("/nope");

        // when
        MvcTestResult result = request.exchange();

        // then
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("NOT_FOUND"));
    }

    @Test
    void 처리되지_않은_예외가_발생하면_500을_반환한다() {
        // given
        MockMvcRequestBuilder request = mvc.get().uri("/fixture/boom");

        // when
        MvcTestResult result = request.exchange();

        // then
        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("INTERNAL_SERVER_ERROR"));
    }

    @RestController
    static class FixtureController {

        record CreateRequest(@NotBlank String name) {
        }

        @GetMapping("/fixture/business")
        void business() {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        @GetMapping("/fixture/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/fixture")
        void create(@Valid @RequestBody CreateRequest request) {
        }
    }
}
