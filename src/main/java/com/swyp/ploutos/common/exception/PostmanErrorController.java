package com.swyp.ploutos.common.exception;

import com.swyp.ploutos.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("postman")
@RestController
@RequestMapping("/local-test/errors")
public class PostmanErrorController {

    @Operation(summary = "로컬 테스트: 성공 응답 샘플")
    @GetMapping("/success")
    public ApiResponse<Map<String, Object>> success() {
        return ApiResponse.of(Map.of("id", 10, "name", "삼성전자"));
    }

    @Operation(summary = "로컬 테스트: P001 입력값 오류")
    @GetMapping("/p001")
    public void invalidInput() {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Operation(summary = "로컬 테스트: P002 주식 미존재")
    @GetMapping("/p002")
    public void stockNotFound() {
        throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
    }

    @Operation(summary = "로컬 테스트: P004 예상하지 못한 오류")
    @GetMapping("/p004")
    public void unexpectedError() {
        throw new IllegalStateException("Postman 오류 응답 확인용 예외");
    }
}
