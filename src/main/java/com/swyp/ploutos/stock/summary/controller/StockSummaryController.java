package com.swyp.ploutos.stock.summary.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.common.exception.Precondition;
import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.stock.summary.service.StockSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "종목 기본정보", description = "종목 상세 상단의 종목명·티커·로고·시장 정보")
@RestController
@RequiredArgsConstructor
class StockSummaryController {

    private final StockSummaryService stockSummaryService;

    @Operation(
            summary = "종목 기본정보 조회",
            description = "종목명, 티커, 로고, 시장, 통화, 시간대를 준다. 가격은 포함하지 않으므로 "
                    + "같은 stockId로 /api/v1/stocks/{stockId}/quote를 따로 조회해 조합한다. "
                    + "시세 장애와 무관하게 응답한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "P001 stockId가 양의 정수가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "InvalidInputValueException", "code": "P001", "message": "잘못된 입력값입니다."}}
                                    """))),
            @ApiResponse(responseCode = "404", description = "P002 없는 종목",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "StockNotFoundException", "code": "P002", "message": "주식을 찾을 수 없습니다."}}
                                    """)))
    })
    @GetMapping("/api/v1/stocks/{stockId}")
    ApiResult<StockSummaryResponse.Summary> summary(
            @Parameter(description = "종목 ID(양의 정수)", example = "1") @PathVariable Long stockId
    ) {
        Precondition.require(stockId > 0, ErrorCode.INVALID_INPUT_VALUE);
        return ApiResult.of(StockSummaryResponse.Summary.from(stockSummaryService.read(stockId)));
    }
}
