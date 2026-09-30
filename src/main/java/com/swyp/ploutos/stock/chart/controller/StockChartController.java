package com.swyp.ploutos.stock.chart.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.stock.chart.service.StockChartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "종목 차트", description = "종목 상세 화면의 가격 차트와 거래량 차트")
@RestController
@RequiredArgsConstructor
class StockChartController {

    private final StockChartService stockChartService;

    @Operation(
            summary = "구간·봉 단위별 차트 조회",
            description = "구간 전체의 OHLCV와 평균 거래량 기준선을 한 번에 준다. "
                    + "interval이 1D가 아니면 저장된 일봉을 주·월·분기·연 단위로 묶어 준다(외부 호출 없음). "
                    + "라인 차트는 candles[].close만, 캔들 차트는 OHLC를 쓰므로 차트 모양을 바꿔도 재요청이 필요 없다. "
                    + "줌 아웃하면 from을 뒤로 밀어, 줌 인하면 interval을 좁혀 다시 호출한다. "
                    + "장중 진행 중 봉을 갱신하려면 /quote와 같은 주기로 폴링해도 캐시를 공유하므로 외부 호출이 늘지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "P001 허용되지 않은 interval, 날짜 형식 오류, "
                    + "from이 to보다 뒤, 구간 5년 초과, stockId가 정수가 아님",
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
            @ApiResponse(responseCode = "502", description = "P007 일봉 동기화 또는 현재가 조회 중 KIS 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "MarketDataUnavailableException", "code": "P007", "message": "시세 정보를 불러올 수 없습니다."}}
                                    """)))
    })
    @GetMapping("/api/v1/stocks/{stockId}/chart")
    ApiResult<StockChartResponse> chart(
            @Parameter(description = "종목 ID", example = "1") @PathVariable Long stockId,
            @Parameter(description = "조회 시작일. 생략하면 to에서 2개월 전", example = "2026-07-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일. 생략하면 오늘. 구간은 5년을 넘을 수 없다", example = "2026-09-29")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "봉 단위. 생략하면 1D",
                    schema = @Schema(allowableValues = {"1D", "1W", "1M", "3M", "1Y"}))
            @RequestParam(required = false) String interval
    ) {
        return ApiResult.of(StockChartResponse.from(stockChartService.read(stockId, from, to, interval)));
    }
}
