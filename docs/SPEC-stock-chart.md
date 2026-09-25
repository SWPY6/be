# 명세: 종목 차트 (stock-chart)

종목 상세 화면의 가격 차트와 거래량 차트(RQ-1002 ~ RQ-1007)에 필요한 데이터를 제공하는 API의 명세다. 저장된 확정 일봉에 당일 진행 중 봉을 붙이고, 20거래일 평균 거래량 기준선을 함께 준다.

기능 맵: `CAPABILITY-MAP.md`. 의존 대상: `stock-daily-price`, `stock-quote`.

## 내가 세운 전제

1. 종목 식별자는 `stockId`(Long) 경로 변수, 경로 접두어는 `/api/v1`, 인증 없음 (`SPEC-stock-quote.md`와 같다).
2. 라인/캔들 전환(RQ-1003), 마우스 오버 표시(RQ-1004), 휠·버튼 확대·축소와 기간 초기화(RQ-1005), 범례(RQ-1007)는 **프론트엔드 렌더링 책임**이다. 백엔드는 기간 전체의 OHLCV를 한 번에 주고, 프론트는 같은 데이터로 라인(종가)과 캔들을 그린다. 그래서 차트 모양을 바꿔도 재요청이 없고 기간이 유지된다.
3. 당일 진행 중 봉은 `stock-quote`의 현재가 캐시로 만든다. 추가 KIS 호출은 없다.
4. "오늘"과 거래일은 시장 현지일 기준이다 — 국내 KST, 미국 America/New_York.

## 목표

기간을 고르면(1개월·3개월·6개월·1년) 그 기간의 거래일별 시가·고가·저가·종가·거래량과 20거래일 평균 거래량이 한 응답으로 온다. 장중에는 마지막 봉이 현재가를 반영하는 진행 중 봉이다.

## 규칙

### 기간

| `period` | 구간 |
| --- | --- |
| `1M` | 오늘 − 1개월 ~ |
| `3M` | 오늘 − 3개월 ~ |
| `6M` | 오늘 − 6개월 ~ |
| `1Y` | 오늘 − 1년 ~ |

- `period`를 생략하면 `1M`이다. 허용되지 않은 값이면 400 `P001`.
- `from` = 시장 현지 오늘 − period(달력 기준). 확정 봉은 `stock-daily-price`의 `DailyPriceReader.findBetween(stockId, from, 어제)`로 읽는다. 거래일만 포함하고 오름차순이다.

### 당일 진행 중 봉

- `QuoteReader.read(stockId)`로 얻은 `Quote`에서 `open`, `high`, `low`, `close = price`, `volume = 당일 누적 거래량`을 구성해 배열 끝에 붙인다. `closed: false`.
- `Quote`의 시가가 0이면(장 시작 전, 휴장일) 붙이지 않는다. 그때 마지막 봉은 `closed: true`인 확정 봉이고 `asOf`는 `null`이다.
- 당일 봉은 저장하지 않는다. 캐시 TTL 안에서는 `/quote` 응답과 같은 값이 나온다.
- 당일 봉의 `tradeAt`이 이미 확정 봉으로 존재하면(장 마감 후 동기화가 끝난 뒤) 붙이지 않는다.
- **미국 종목의 당일 봉 `open`은 장중에 부정확할 수 있다.** KIS가 "장중 당일 시가는 상이할 수 있으며 익일 정정"이라고 명시한다. 확정 일봉은 정정된 값이므로 영향이 없다. 보정하지 않고 받은 값을 그대로 쓴다.

### 20거래일 평균 거래량

- `DailyPriceReader.averageVolume20d(stockId)` 값을 `averageVolume20d`로 준다. 확정 봉 기준이며 당일 봉은 제외다.
- 비어 있으면 `null`.
- 기준선 1개(스칼라)다. 프론트는 거래량 차트에 수평 점선으로 그린다.

### 응답 구간

- `from`·`to`는 실제 포함된 첫·마지막 봉의 거래일이다. 당일 봉이 있으면 `to`는 오늘이다.
- `asOf`는 당일 봉의 기준 시각(`Quote.priceAt`)이다. 당일 봉이 없으면 `null`.
- 봉이 하나도 없으면(신규 상장 직후 등) `candles: []`, `from`·`to`·`asOf`·`averageVolume20d`는 `null`.

### 오류

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| `period`가 허용되지 않은 값 | 400 | `P001` |
| `stockId`가 정수가 아님 | 400 | `P001` |
| 종목 없음 | 404 | `P002` |
| 동기화·현재가 조회 중 KIS 실패 | 502 | `P007` |

### 설계

- `ChartPeriod`(enum): `ONE_MONTH("1M")` … `ONE_YEAR("1Y")`, `from(LocalDate today)`, 기본값 `ONE_MONTH`.
- `Chart`(값 객체): 확정 봉 목록 + `Optional<Quote>` + 평균 거래량을 받아 `candles`, `from`, `to`, `asOf`를 만든다. 당일 봉 결합 규칙을 여기 둔다.
- `StockChartService`: 종목 조회(없으면 `STOCK_NOT_FOUND`) → `DailyPriceReader` → `QuoteReader` → `Chart`.
- `StockChartController`: `GET /api/v1/stocks/{stockId}/chart` → `ApiResponse<StockChartResponse>`.

## API 계약

프론트엔드에 전달하는 계약이다. 봉투 규칙은 `SPEC-api-response.md`를 따른다. 구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다.

### `GET /api/v1/stocks/{stockId}/chart?period=3M`

요청: 경로 변수 `stockId`(정수). 쿼리 `period` ∈ `1M` | `3M` | `6M` | `1Y`, 생략 시 `1M`. 라인/캔들 전환·확대·축소·기간 초기화는 이 응답 하나로 프론트가 처리한다 — 기간이 바뀔 때만 재요청한다. 장중에 당일 봉을 갱신하려면 `/quote`와 같은 주기로 폴링해도 된다(캐시를 공유하므로 KIS 호출이 늘지 않는다).

**200 성공** (장중, 당일 봉 포함)

```json
{
  "data": {
    "stockId": 1,
    "period": "3M",
    "currency": "KRW",
    "from": "2026-05-12",
    "to": "2026-08-12",
    "asOf": "2026-08-12T14:31:05+09:00",
    "averageVolume20d": 84210,
    "candles": [
      { "tradeAt": "2026-05-12", "open": 244280, "high": 251224, "low": 241056, "close": 248000, "volume": 245000, "closed": true },
      { "tradeAt": "2026-05-13", "open": 248500, "high": 249900, "low": 246100, "close": 247300, "volume": 198000, "closed": true },
      { "tradeAt": "2026-08-12", "open": 244280, "high": 251224, "low": 241056, "close": 248000, "volume": 245000, "closed": false }
    ]
  }
}
```

**200 성공** (장 시작 전·휴장일, 당일 봉 없음)

```json
{
  "data": {
    "stockId": 1,
    "period": "1M",
    "currency": "KRW",
    "from": "2026-07-13",
    "to": "2026-08-11",
    "asOf": null,
    "averageVolume20d": 84210,
    "candles": [
      { "tradeAt": "2026-07-13", "open": 238000, "high": 241500, "low": 237200, "close": 240100, "volume": 176000, "closed": true },
      { "tradeAt": "2026-08-11", "open": 239800, "high": 241000, "low": 238900, "close": 240217, "volume": 201000, "closed": true }
    ]
  }
}
```

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `stockId` | integer | X | 종목 ID |
| `period` | string | X | 적용된 기간. 생략 요청이면 `"1M"` — 선택 상태 표시용 (RQ-1002) |
| `currency` | `"KRW"` \| `"USD"` | X | 가격 통화 |
| `from` | string(date) | O | 첫 봉의 거래일. 봉이 없으면 `null` |
| `to` | string(date) | O | 마지막 봉의 거래일. 당일 봉이 있으면 오늘. 봉이 없으면 `null` |
| `asOf` | string(ISO-8601, 오프셋 포함) | O | 당일 봉의 기준 시각. 당일 봉이 없으면 `null` |
| `averageVolume20d` | integer | O | 최근 20거래일 평균 거래량(확정 봉 기준) — 거래량 차트 기준선 1개 (RQ-1006·1007). 20일 미만이면 `null` |
| `candles[]` | array | X | 거래일 오름차순. 거래일이 아닌 날은 없다. 빈 배열 가능 |
| `candles[].tradeAt` | string(date) | X | 거래일 |
| `candles[].open` | number | X | 시가 |
| `candles[].high` | number | X | 고가 |
| `candles[].low` | number | X | 저가 |
| `candles[].close` | number | X | 종가. 라인 차트는 이 값만 쓴다 (RQ-1003·1004). 당일 봉은 현재가 |
| `candles[].volume` | integer | X | 거래량(주) — 거래량 막대 (RQ-1006). 당일 봉은 누적 거래량 |
| `candles[].closed` | boolean | X | `true` 확정 봉, `false` 당일 진행 중 봉(폴링하면 값이 바뀐다) |

**400 잘못된 기간** (`?period=2W`)

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다."
  }
}
```

**404 없는 종목**

```json
{
  "error": {
    "name": "StockNotFoundException",
    "code": "P002",
    "message": "주식을 찾을 수 없습니다."
  }
}
```

**502 시세 제공자 오류** (일봉 동기화 또는 현재가 조회 중 KIS 실패)

```json
{
  "error": {
    "name": "MarketDataUnavailableException",
    "code": "P007",
    "message": "시세 정보를 불러올 수 없습니다."
  }
}
```

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*ChartTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/stock/chart/            → ChartPeriod, Chart, ChartCandle
src/main/java/com/swyp/ploutos/stock/chart/service/    → StockChartService
src/main/java/com/swyp/ploutos/stock/chart/controller/ → StockChartController, StockChartResponse
src/test/java/com/swyp/ploutos/stock/chart/**          → 대상과 같은 패키지에 테스트
```

## 코드 스타일

- 응답 DTO는 `record`. `ChartCandle`은 확정 봉과 당일 봉이 같은 형태이므로 `closed` 필드 하나로 구분한다.
- `ChartPeriod`는 요청 문자열 → enum 변환을 스스로 한다. 허용되지 않은 값은 `INVALID_INPUT_VALUE`.
- `else` 없이 guard clause. `@Getter`/`@Setter` 금지.
- 오늘 날짜는 `Clock`과 시장 타임존으로 구한다.

```java
// Chart — 당일 봉 결합
private List<ChartCandle> withToday(List<ChartCandle> closed, Optional<Quote> quote) {
    if (quote.isEmpty() || quote.get().notOpenedToday()) {
        return closed;
    }
    ChartCandle today = ChartCandle.inProgress(quote.get());
    if (closed.stream().anyMatch(c -> c.isSameDay(today))) {
        return closed;
    }
    return Stream.concat(closed.stream(), Stream.of(today)).toList();
}
```

## 테스트 전략

- JUnit 6, BDD, 한글 `조건_결과`.
- 단위(60%): `ChartPeriod`(문자열 변환, 기본값, `from` 계산), `Chart`(당일 봉 결합, `from`/`to`/`asOf` 계산, 빈 봉 처리) — POJO. `StockChartService`는 가짜 `DailyPriceReader`·`QuoteReader`로.
- 통합(30%): `@WebMvcTest(StockChartController)` + MockMvc로 JSON 본문·상태 코드·`period` 기본값·400.
- E2E(10%): `@SpringBootTest`에서 KIS를 스텁하고 `GET /api/v1/stocks/{id}/chart?period=1M` 전체 흐름 1건.

## 경계

- **항상:** 확정 봉은 `DailyPriceReader`로만 읽는다(직접 리포지토리 접근 금지). 당일 봉은 `QuoteReader`로만 만든다. 커밋 전 `./gradlew test`. 응답 형식이 바뀌면 이 명세와 Swagger를 먼저 고친다.
- **먼저 묻기:** 기간 옵션 추가(예: `YTD`, `5Y`), 응답 필드 추가·이름 변경(프론트 계약), 이동평균 시계열 제공, 분봉 지원.
- **절대 안 함:** 차트 API에서 KIS를 직접 호출, 당일 봉 저장, 확정 봉과 당일 봉을 다른 배열로 분리(프론트 계약 위반).

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | `period`를 생략하면 1개월로 조회한다. | `StockChartControllerTest.기간을_생략하면_1개월로_조회한다` (RQ-1002) |
| 2 | 기간을 지정하면 해당 기간의 거래일 봉만 오름차순으로 반환한다. | `ChartTest.기간을_지정하면_해당_기간의_거래일_봉만_오름차순으로_반환한다` (RQ-1002) |
| 3 | 봉마다 시가·고가·저가·종가·거래량이 있다. | `봉마다_시가_고가_저가_종가_거래량을_포함한다` (RQ-1003·1004·1006) |
| 4 | 장중이면 당일 진행 중 봉을 마지막에 붙이고 `closed`는 `false`다. | `장중이면_당일_진행중_봉을_마지막에_붙인다` |
| 5 | 당일 시가가 0이면 당일 봉을 붙이지 않고 `asOf`는 `null`이다. | `당일_시가가_없으면_당일_봉을_붙이지_않는다` |
| 6 | 당일 거래일이 이미 확정 봉으로 있으면 당일 봉을 붙이지 않는다. | `당일_봉이_이미_확정되어_있으면_붙이지_않는다` |
| 7 | 20거래일 평균은 당일 봉을 제외한 확정 봉 기준이다. | `StockChartServiceTest.20거래일_평균은_당일_봉을_제외하고_계산한다` (RQ-1006·1007) |
| 8 | `from`·`to`는 실제 포함된 첫·마지막 봉의 거래일이다. | `ChartTest.시작일과_종료일은_실제_포함된_봉의_거래일이다` |
| 9 | 봉이 없으면 빈 배열과 `null` 요약값을 반환한다. | `봉이_없으면_빈_배열을_반환한다` |
| 10 | 허용되지 않은 `period`면 400 / `P001`. | `StockChartControllerTest.잘못된_기간을_요청하면_400과_P001을_반환한다` |
| 11 | 없는 종목이면 404 / `P002`. | `없는_종목이면_404와_P002를_반환한다` |
| 12 | KIS 실패면 502 / `P007`. | `시세_조회에_실패하면_502와_P007을_반환한다` |
| 13 | 차트 모양을 바꿔도 재요청 없이 같은 응답으로 라인·캔들을 그릴 수 있다(응답에 종가와 OHLC가 모두 있다). | 3번 테스트로 증명. 프론트 검증 항목 (RQ-1003) |

## 미해결 질문

- 개장 직후 KIS가 당일 `low`를 0으로 주는 등 이상값이 있는지 — 구현 중 실측 후 "붙이지 않는 조건"을 보강한다.
- 미국 종목의 애프터마켓 시세가 현재가에 반영되는 경우 당일 봉을 정규장 기준으로 자를지 — 현재는 KIS 현재가를 그대로 쓴다.
