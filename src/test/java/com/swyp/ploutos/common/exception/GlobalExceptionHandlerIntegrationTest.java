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
        // when & then
        assertThat(mvc.get().uri("/fixture/not-found"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("NOT_FOUND_STOCK"))
                .hasPathSatisfying("$.message", message -> message.assertThat().isEqualTo("종목을 찾을 수 없습니다."))
                .hasPathSatisfying("$.errors", errors -> errors.assertThat().asInstanceOf(InstanceOfAssertFactories.LIST).isEmpty());
    }

    @Test
    void 필드검증에_실패하면_400과_필드에러목록을_반환한다() {
        // when & then
        assertThat(mvc.post().uri("/fixture")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": ""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("INVALID_INPUT_VALUE"))
                .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isEqualTo("name"));
    }

    @Test
    void 본문이_잘못된_JSON이면_400을_반환한다() {
        // when & then
        assertThat(mvc.post().uri("/fixture")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("BAD_REQUEST"));
    }

    @Test
    void 없는_경로를_요청하면_404_JSON_바디를_반환한다() {
        // when & then
        assertThat(mvc.get().uri("/nope"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("NOT_FOUND"));
    }

    @Test
    void 처리되지_않은_예외가_발생하면_500을_반환한다() {
        // when & then
        assertThat(mvc.get().uri("/fixture/boom"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("INTERNAL_SERVER_ERROR"));
    }

    @RestController
    static class FixtureController {

        record CreateRequest(@NotBlank String name) {
        }

        @GetMapping("/fixture/not-found")
        void notFound() {
            throw new BusinessException(ErrorCode.NOT_FOUND_STOCK);
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
