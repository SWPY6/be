package com.swyp.ploutos.news.controller;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.common.response.ErrorResponse;
import com.swyp.ploutos.news.service.StockNewsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "종목 뉴스", description = "종목 상세 화면의 관련 뉴스")
@RestController
@RequiredArgsConstructor
class StockNewsController {

    private final StockNewsService stockNewsService;

    @Operation(
            summary = "종목 관련 뉴스 조회",
            description = "네이버 뉴스 검색에서 제목·요약에 종목명이 들어간 기사를 최신순 최대 20건 준다. "
                    + "검색 결과는 종목별로 최대 10분 캐시한다. "
                    + "publishedAt은 네이버 제공 시각이므로 화면에 '최초 발표'로 표기하지 않는다. "
                    + "가격 변동의 원인으로 단정하지 않는다는 안내를 함께 표시한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공. 관련 기사가 없으면 items=[], total=0"),
            @ApiResponse(responseCode = "400",
                    description = "P001 stockId가 정수가 아님, from·to 중 하나만 있음, 오프셋 없는 시각, "
                            + "from ≥ to, 미래 to, 7일 초과 기간",
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
                    description = "P008 뉴스 공급자 오류(네이버 실패·인증 오류·쓸 수 없는 응답)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "NewsUnavailableException", "code": "P008", "message": "뉴스를 불러올 수 없습니다."}}
                                    """))),
            @ApiResponse(responseCode = "503",
                    description = "P009 뉴스 조회 제한(일일 호출 한도 소진, 캐시 저장소 접근 불가). 즉시 재시도하지 않는다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"error": {"name": "NewsQuotaExceededException", "code": "P009", "message": "뉴스 조회가 일시적으로 제한되었습니다."}}
                                    """)))
    })
    @GetMapping("/api/v1/stocks/{stockId}/news")
    ApiResult<StockNewsResponse> news(
            @Parameter(description = "종목 ID", example = "1") @PathVariable Long stockId,
            // 예시 값을 두면 Swagger "Try it out"이 칸을 미리 채워 기본 기간(최근 7일)으로 조회할 수 없다.
            @Parameter(description = "기간 시작(제외). 예: 2026-09-23T14:00:00+09:00. 오프셋 필수, URL에서 +는 %2B로 인코딩. "
                    + "to와 함께 주거나 둘 다 생략(최근 7일)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "기간 끝(포함). 예: 2026-09-30T14:00:00+09:00. 오프셋 필수, 미래 불가, from과 최대 7일")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ApiResult.of(StockNewsResponse.from(stockNewsService.read(stockId, from, to)));
    }
}
