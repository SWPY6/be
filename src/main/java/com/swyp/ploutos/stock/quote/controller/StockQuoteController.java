package com.swyp.ploutos.stock.quote.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.stock.quote.service.StockQuoteDetailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "종목 현재가", description = "종목 상세 화면의 현재가와 주요 지표")
@RestController
@RequiredArgsConstructor
class StockQuoteController {

    private final StockQuoteDetailService stockQuoteDetailService;

    @Operation(
            summary = "현재가·주요 지표 조회",
            description = "현재가, 등락률, 가격 기준 시각, 실시간 여부와 주요 지표 8종을 한 번에 준다. "
                    + "서버가 10초마다 갱신하므로 폴링 주기는 10초를 권장한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "P001 stockId가 정수가 아님",
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
                                    """))),
            @ApiResponse(responseCode = "502",
                    description = "P007 시세 제공자 오류(KIS 실패, 캐시 저장소 접근 불가, 첫 조회 대기 초과)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "MarketDataUnavailableException", "code": "P007", "message": "시세 정보를 불러올 수 없습니다."}}
                                    """)))
    })
    @GetMapping("/api/v1/stocks/{stockId}/quote")
    ApiResult<StockQuoteResponse> quote(
            @Parameter(description = "종목 ID", example = "1") @PathVariable Long stockId
    ) {
        return ApiResult.of(StockQuoteResponse.from(stockQuoteDetailService.read(stockId)));
    }
}
