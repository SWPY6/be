package com.swyp.ploutos.stock.movers.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swyp.ploutos.common.enums.Country;
import com.swyp.ploutos.common.enums.IndustryCode;
import com.swyp.ploutos.common.response.ApiResult;
import com.swyp.ploutos.stock.movers.MoverCondition;
import com.swyp.ploutos.stock.movers.service.StockMoverService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
class StockMoverController {

    private final StockMoverService stockMoverService;

    @Operation(
            summary = "주요 변동 종목 목록",
            description = """
                    조건 하나와 선택적인 산업·검색어로 종목 목록을 한 번에 돌려준다.
                    **정렬과 페이지 나누기는 프론트가 한다** — 서버는 조건에 맞는 전부를 보낸다.
                    `stocks` 의 순서가 곧 순위이고, 순위 번호는 따로 내려보내지 않는다.

                    **건수 상한이 국가마다 다르다.** 국내는 60건, 해외는 100건이다. 국내 상한이
                    낮은 것은 KIS 순위 API 가 한 번에 30건까지만 주기 때문이며, 코스피와 코스닥을
                    나눠 불러 60건을 만든다.

                    **`stockId` 가 null 인 줄이 있다.** 외부 순위에는 있지만 우리 종목 정보에 없는
                    종목(ETF·우선주·신규 상장)이며, 목록에서 빼지 않고 그대로 싣는다. 그 줄은
                    종목 상세로 이동할 수 없고 `industry` 도 비어 있다.

                    **조건에 따라 비는 열이 있다.** 상승·하락에서는 거래대금과 시가총액이 null 이다.
                    순위 API 응답에 그 값이 없기 때문이며, 임의의 수치로 채우지 않는다.

                    **거래량 배수는 응답에 없다.** 당일 누적 거래량을 하루 전체 평균과 견주는 값이라
                    장중에는 1보다 작게 나와 숫자로 보이면 오해를 부른다. 거래량 급증 조건의
                    **정렬 기준으로만** 쓴다.

                    **`industry` 를 주면 그 산업 소속 종목 안에서 다시 센다.** 외부 순위 결과를
                    산업으로 거르는 것이 아니다 — 그러면 결과가 거의 비기 때문이다. 대신 모집단이
                    우리가 시세를 모아 둔 종목으로 좁아진다.

                    값은 서버가 주기적으로 모아 둔 것이거나 순위 API 가 준 것이며, 이 요청이
                    종목마다 외부 시세를 부르지는 않는다.
                    """)
    @GetMapping("/api/v1/stocks/movers")
    ApiResult<StockMoverResponse> readMovers(

            @Parameter(description = "국내(KR) 또는 해외(US). 화면의 시장 토글", example = "KR")
            @RequestParam(defaultValue = "KR") Country country,

            @Parameter(description = """
                    ALL 전체 종목 · RISING 상승 TOP · FALLING 하락 TOP · VOLUME_SURGE 거래량 급증.
                    조건이 모집단과 정렬을 함께 정하므로 목록·순위·결과 수가 모두 바뀐다.""",
                    example = "RISING")
            @RequestParam(defaultValue = "ALL") MoverCondition condition,

            @Parameter(description = "산업으로 좁힌다. 생략하면 전체 산업", example = "AUTOMOBILE")
            @RequestParam(required = false) IndustryCode industry,

            @Parameter(description = "종목명 또는 종목 코드 부분 일치. 산업과 함께 주면 둘 다 만족하는 종목만",
                    example = "현대")
            @RequestParam(required = false) String query) {

        return ApiResult.of(StockMoverResponse.from(
                stockMoverService.read(country, condition, industry, query)));
    }
}
