package com.swyp.ploutos.market.chart.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.market.MarketIndicator;
import com.swyp.ploutos.market.chart.service.MarketChartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "시장 지표 차트", description = "시장 요약 화면의 지표 캔들 차트")
@RestController
@RequiredArgsConstructor
class MarketChartController {

    private final MarketChartService marketChartService;

    @Operation(
            summary = "구간·봉 단위별 지표 차트 조회",
            description = "구간 전체의 OHLC를 한 번에 준다. 지표에는 거래량이 없어 volume을 내보내지 않는다. "
                    + "interval이 1D가 아니면 저장된 일봉을 주·월·분기·연 단위로 묶어 준다(외부 호출 없음). "
                    + "라인 차트는 candles[].close만, 캔들 차트는 OHLC를 쓰므로 차트 모양을 바꿔도 재요청이 필요 없다. "
                    + "줌 아웃하면 from을 뒤로 밀어, 줌 인하면 interval을 좁혀 다시 호출한다. "
                    + "마지막 봉이 진행 중이면(closed false) close가 현재값이고 asOf가 그 기준 시각이다. "
                    + "장중 갱신은 카드와 같은 10초 주기로 폴링하면 현재값 캐시를 공유해 외부 호출이 늘지 않는다. "
                    + "장 시작 전처럼 현재값이 이미 확정된 거래일의 것이면 진행 중인 봉을 붙이지 않고 asOf가 null이다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "P001 허용되지 않은 지표(소문자 포함), "
                    + "허용되지 않은 interval, 날짜 형식 오류, from이 to보다 뒤, 구간 5년 초과",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "InvalidInputValueException", "code": "P001", "message": "잘못된 입력값입니다."}}
                                    """))),
            @ApiResponse(responseCode = "502",
                    description = "P007 시세 제공자 오류(일봉 동기화 또는 현재값 조회 중 KIS 실패, "
                            + "캐시 저장소 접근 불가, 첫 조회 대기 초과)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "MarketDataUnavailableException", "code": "P007", "message": "시세 정보를 불러올 수 없습니다."}}
                                    """)))
    })
    @GetMapping("/api/v1/markets/indicators/{indicator}/chart")
    ApiResult<MarketChartResponse> chart(
            @Parameter(description = "지표 식별자. 카드 API의 indicators[].indicator를 그대로 쓴다", example = "KOSPI")
            @PathVariable MarketIndicator indicator,
            @Parameter(description = "조회 시작일. 생략하면 to에서 2개월 전", example = "2026-09-28")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일. 생략하면 지표 타임존의 오늘. 구간은 5년을 넘을 수 없다",
                    example = "2026-09-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "봉 단위. 생략하면 1D",
                    schema = @Schema(allowableValues = {"1D", "1W", "1M", "3M", "1Y"}))
            @RequestParam(required = false) String interval
    ) {
        return ApiResult.of(MarketChartResponse.from(marketChartService.read(indicator, from, to, interval)));
    }
}
