# 명세: 시장 지표 차트 (market-chart)

시장 요약 화면의 지표 차트 API 명세다. 지표 하나의 확정 일봉을 요청한 봉 단위로 집계하고, 당일 진행 중인 봉을 붙여 준다.
구간·봉 단위 규칙과 집계는 `stock-chart`와 같고, 봉 집계 코드(`stock.chart`)를 재사용한다.

기능 맵: `CAPABILITY-MAP-market.md`. 의존 대상: `market-daily-price`, `market-quote`, `market-indicator`, `stock-chart`(루트의 봉 집계).

## 내가 세운 전제

1. 지표는 경로 변수 `{indicator}`로 받는다. 값은 `MarketIndicator` enum 이름이고, 카드 API 응답의 `indicators[].indicator`를 그대로 쓴다.
2. 경로 접두어는 `/api/v1`이고 인증은 없다.
3. 구간(`from`·`to`)과 봉 단위(`interval`)의 규칙은 `SPEC-stock-chart.md`와 **같다**.
   - 기본 구간 2개월, 최대 5년, 봉 단위 `1D|1W|1M|3M|1Y`, 불완전 버킷 포함, 확대·축소는 재요청.
   - 기능 맵의 "차트 규칙은 `stock-chart`와 같다" 결정이다.
4. **거래량은 응답에 없다.** 봉의 `volume`과 `averageVolume`을 내보내지 않는다. 기능 맵의 "거래량 제외" 결정이다. 집계 코드가 계산하는 거래량(0)은 응답 DTO에서 버린다.
5. 진행 중인 봉은 `market-quote` 캐시로 만든다. 추가 KIS 호출은 없다.
6. "오늘"은 지표 타임존(`MarketIndicator.zoneId()`) 기준이다.

## 목표

지표 카드를 누르면 보이는 차트에 필요한 봉을 한 응답으로 준다. 장중에는 마지막 봉이 현재값을 반영하는 진행 중인 봉이다.
주식 차트와 규칙과 코드를 공유해서, 두 차트가 같은 구간·봉 단위 조합에 같은 방식으로 응답하게 한다.

## 규칙

### 구간과 봉 단위

`SPEC-stock-chart.md`의 "구간과 봉 단위", "집계", "응답 구간"을 그대로 따른다. `ChartInterval`, `ChartRange`, `ChartCandle`을 재사용한다.

| 파라미터 | 타입 | 생략 시 | 설명 |
| --- | --- | --- | --- |
| `from` | `LocalDate` (ISO `yyyy-MM-dd`) | `to` − 2개월 | 조회 시작일 |
| `to` | `LocalDate` (ISO `yyyy-MM-dd`) | 지표 타임존의 오늘 | 조회 종료일 |
| `interval` | `1D` \| `1W` \| `1M` \| `3M` \| `1Y` | `1D` | 봉 단위: 일·주·월·분기·연 |

- 확정 봉은 `IndicatorDailyPriceReader.findBetween(indicator, from, to)`로 읽는다.
- 기본 구간 2개월이면 거래일이 약 42개다. 국내 지수의 KIS 페이지(50건) 한 번으로 끝난다.
- 5년 구간을 처음 채우면 국내 지수는 KIS를 약 25번 부른다 (`SPEC-market-daily-price.md`). 주식 차트와 같은 5년 상한을 쓴다.

### 진행 중인 봉

`IndicatorQuoteReader.read(indicator)`로 얻은 `IndicatorQuote`로 만든다. 당일자 봉(`tradeAt`=지표 타임존의 오늘, `open`·`high`·`low`, `close = value`, 거래량 0)을 만들어 확정 봉 끝에 붙인 뒤 통째로 집계한다. `stock-chart`의 "마지막 버킷에 합치기" 방식과 같다.

**다음 중 하나라도 해당하면 붙이지 않는다.**

| # | 조건 | 이유 |
| --- | --- | --- |
| 1 | 시가가 0이다 | 장 시작 전. `stock-chart`와 같은 규칙 |
| 2 | **`previousClose`가 마지막 확정 봉의 종가와 다르다** | 시세가 이미 확정 봉으로 저장된 거래일의 것이다. 개장 전 가짜 봉을 막는 규칙이다. 아래 설명 참고 |
| 3 | 구간의 `to`가 오늘보다 이전이다 | 과거 구간을 조회할 때 오늘 봉이 끼어들지 않게 한다 |
| 4 | 오늘 날짜의 확정 봉이 이미 있다 | `stock-chart`와 같은 규칙. `Chart`가 처리한다 |

**조건 2가 필요한 이유 (실측, 모의 도메인)**
- 한국 시간 2026-09-30 05:21, 국내 장 시작 전에 KOSPI 현재값 API를 불렀다.
  - 응답: 시가 `6844.41`, 현재값 `6870.81`, 전일 대비 `−18.93` → 전일 종가 `6889.74`.
  - 모두 **9/29 거래일**의 값이다. 시가가 0이 아니라서 조건 1로는 걸러지지 않는다.
- 그대로 붙이면 9/30 날짜에 9/29와 똑같은 가짜 봉이 생긴다.
- 전일 종가를 확정 봉과 비교하면 걸러진다.
  - 이 시세의 전일 종가 `6889.74`는 9/28 종가다. 마지막 확정 봉(9/29)의 종가 `6870.81`과 다르므로 이미 확정된 9/29의 시세다 → 붙이지 않는다.
  - 9/30 장중에는 전일 종가가 9/29 종가 `6870.81`이 되어 마지막 확정 봉 종가와 같다 → 9/30 진행 중인 봉으로 붙인다.
- 날짜를 쓰지 않고 값의 연속성으로 판단하므로 한국·미국·환율에 같은 규칙이 통한다.
  - 해외 지수 예: 한국 낮(뉴욕 새벽)에는 뉴욕의 "오늘"이 바뀌었지만 시세는 전날 것이다.
- 비교는 `BigDecimal.compareTo`로 한다. 저장 정밀도(소수 넷째 자리)와 KIS 값의 자릿수가 달라도 같은 값으로 본다.
- 구간 안에 확정 봉이 하나도 없으면 비교할 수 없으므로 붙이지 않는다.

**`stock-chart`와 다른 점**
- 조건 2와 3은 이 모듈에만 있다. 주식 차트에도 같은 문제가 있을 수 있지만(아래 "알려진 한계") 이 명세의 범위가 아니다.
- 진행 중인 봉을 만드는 규칙은 `market/chart` 루트의 `IndicatorLiveCandle`에 둔다. 스프링 없이 검증할 수 있는 순수 계산이다.

### 봉 집계 재사용: `Chart`가 `Quote`에 묶이지 않게 바꾼다

지금 `Chart.of(DailyPrices, Optional<Quote>, ChartInterval, LocalDate today)`는 주식 시세 `Quote`를 직접 받는다. `Quote`에서 쓰는 것은 세 가지다: 시가 0 판정(`notOpenedToday`), OHLC·현재가·거래량, 기준 시각(`priceAt`).

**바꾸는 것 (`stock.chart`)**
- `LiveCandle` record를 새로 둔다: `DailyPrice price`, `OffsetDateTime asOf`. 당일자 일봉과 그 기준 시각이다.
- `Chart.of(DailyPrices closed, Optional<LiveCandle> live, ChartInterval interval)`로 바꾼다.
  - `today`는 `live.price().tradeAt()`에서 얻는다.
  - "오늘 날짜의 확정 봉이 있으면 붙이지 않는다" 규칙은 `Chart`에 남는다.
  - `Chart`는 더 이상 `stock.quote`를 import하지 않는다.
- `Quote`에서 `LiveCandle`로 바꾸는 일과 시가 0 판정은 `StockChartService`가 한다. 주식 차트의 동작은 바뀌지 않는다.
  - `ChartTest`는 `Quote` 대신 `LiveCandle`을 넘기도록 고친다.
  - 기존 주식 차트 테스트는 **전부 이전과 같은 결과로 통과해야 한다.**

**옮기지 않는 것**
- 기능 맵은 봉 집계를 "공용 위치로 옮긴다"고 했다. 하지만 옮길 필요가 없다.
  - `market.chart`가 `stock.chart` 루트에 의존하는 것은 CLAUDE.md 패키지 규칙상 허용된다. 모듈 루트끼리의 의존이기 때문이다.
  - `market.price`도 이미 `stock.price` 루트의 `DailyPrice`를 쓴다.
- 파일을 옮기면 주식 모듈의 import가 전부 바뀐다. 그 비용에 비해 얻는 게 없다.
- 기능 맵의 해당 결정은 이 명세로 대체한다.

### 응답 값

| 응답 필드 | 규칙 |
| --- | --- |
| `candles[].open/high/low/close` | 소수 둘째 자리로 반올림(HALF_UP). 환율은 소수 넷째 자리로 저장되어 있다 |
| `candles[].volume`, `averageVolume` | **내보내지 않는다** |
| `from`, `to` | 실제 포함된 첫·마지막 **봉의** `tradeAt` (`stock-chart`와 같음) |
| `asOf` | 진행 중인 봉의 기준 시각(`IndicatorQuote.valueAt`). 없으면 `null` |
| `indicator`, `name`, `unit` | `MarketIndicator`에서. 카드 API와 같은 값 |

### 설계

- `LiveCandle` (`stock/chart`, record, `public`): 위 "바꾸는 것".
- `IndicatorLiveCandle` (`market/chart`, 루트, `final` 클래스)
  - `static Optional<LiveCandle> of(IndicatorQuote quote, DailyPrices closed, ChartRange range, LocalDate today)`.
  - "붙이지 않는다" 조건 1~3을 판정한다. 조건 4는 `Chart`가 한다.
- `IndicatorQuote.notOpenedToday()` (`market/quote`): 시가가 0이면 true. `SPEC-market-quote.md`에 "차트가 필요해질 때 추가한다"고 적어 둔 메서드다.
- `DailyPrices.lastClose()` (`stock/price`): 마지막 확정 봉의 종가 `Optional<BigDecimal>`. 조건 2에 쓴다.
- `MarketChartService` (`market/chart/service`, `@Service`, `public`)
  - `MarketChartDetail read(MarketIndicator indicator, LocalDate from, LocalDate to, String intervalCode)`.
  - 흐름: `interval` 검증 → 오늘(지표 타임존) → `ChartRange` → `IndicatorDailyPriceReader` → `IndicatorQuoteReader` → `IndicatorLiveCandle` → `Chart`.
  - `interval` 검증을 가장 먼저 해서 잘못된 요청이 DB나 KIS에 닿지 않게 한다 (`StockChartService`와 같음).
- `MarketChartDetail` (`market/chart/service`, record): `indicator`, `interval`, `chart`.
- `MarketChartController` (`market/chart/controller`, package-private): `GET /api/v1/markets/indicators/{indicator}/chart` → `ApiResult<MarketChartResponse>`.
- `MarketChartResponse` (`market/chart/controller`, record, package-private): 중첩 `Candle`. 반올림은 여기서 한다.
- 새 `ErrorCode`는 없다. `{indicator}` 바인딩 실패는 `MethodArgumentTypeMismatchException` → `P001`이다.

### 알려진 한계

- **환율의 전날 행 지연 (모의 도메인, 새벽 실측)**
  - 한국 시간 9/30 05시, 환율 확정 봉은 9/28까지였다. 현재값의 전일 종가는 9/28 종가(1359.9)였다.
  - 조건 2를 통과해 진행 중인 봉이 **9/30 날짜로** 붙는다. 실제로는 9/29 거래일의 값이다.
  - 날짜 하나가 어긋나지만 값의 흐름은 끊기지 않는다. 한국 낮에 환율 행이 언제 들어오는지 실측한 뒤 보정 여부를 정한다 (미해결 질문).
- **주식 차트의 같은 문제 가능성**
  - `stock-chart`는 시가 0으로만 개장 전을 판정한다. 과거 구간을 조회할 때도 오늘 봉을 붙인다.
  - KIS 주식 현재가도 개장 전에 전날 값을 주면 조건 2·3과 같은 문제가 생긴다. 이 명세는 주식 차트 동작을 바꾸지 않는다. 필요하면 `SPEC-stock-chart.md`에서 따로 다룬다.

## API 계약

프론트엔드에 전달하는 계약이다.
- 봉투 규칙은 `SPEC-api-response.md`를 따른다.
- 구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다.

### `GET /api/v1/markets/indicators/{indicator}/chart`

| 파라미터 | 위치 | 필수 | 값 |
| --- | --- | --- | --- |
| `indicator` | path | O | `KOSPI` \| `KOSDAQ` \| `NASDAQ` \| `SP500` \| `USD_KRW` (대문자). 카드 API의 `indicators[].indicator` |
| `from` | query | X | `yyyy-MM-dd`. 생략하면 `to` − 2개월 |
| `to` | query | X | `yyyy-MM-dd`. 생략하면 지표 타임존의 오늘 |
| `interval` | query | X | `1D` \| `1W` \| `1M` \| `3M` \| `1Y`. 생략하면 `1D` |

화면 동작과의 대응은 `SPEC-stock-chart.md`와 같다. 확대·축소는 재요청이고, 라인↔캔들 전환은 재요청하지 않는다. 장중 갱신은 카드와 같은 10초 주기로 폴링한다.

**200 성공: KOSPI 일봉, 장중** (`GET /api/v1/markets/indicators/KOSPI/chart?from=2026-09-28`, 한국 시간 2026-09-30 10:15)

9/28·9/29는 실측값이다. 9/30 진행 중인 봉의 값은 예시다.

```json
{
  "data": {
    "indicator": "KOSPI",
    "name": "코스피",
    "unit": "POINT",
    "interval": "1D",
    "from": "2026-09-28",
    "to": "2026-09-30",
    "asOf": "2026-09-30T10:15:03+09:00",
    "candles": [
      { "tradeAt": "2026-09-28", "open": 7057.86, "high": 7065.90, "low": 6889.68, "close": 6889.74, "closed": true },
      { "tradeAt": "2026-09-29", "open": 6844.41, "high": 6898.36, "low": 6782.99, "close": 6870.81, "closed": true },
      { "tradeAt": "2026-09-30", "open": 6875.20, "high": 6910.45, "low": 6861.02, "close": 6902.33, "closed": false }
    ]
  }
}
```

마지막 봉이 진행 중이다(`closed: false`). `close`는 현재값이고, 폴링하면 값이 바뀐다.

**200 성공: NASDAQ 일봉, 진행 중인 봉 없음** (`GET /api/v1/markets/indicators/NASDAQ/chart?from=2026-09-28`, 한국 시간 2026-09-30 14:00 = 뉴욕 9/30 01:00)

모두 실측값이다.

```json
{
  "data": {
    "indicator": "NASDAQ",
    "name": "나스닥",
    "unit": "POINT",
    "interval": "1D",
    "from": "2026-09-28",
    "to": "2026-09-29",
    "asOf": null,
    "candles": [
      { "tradeAt": "2026-09-28", "open": 26935.76, "high": 26990.02, "low": 26709.69, "close": 26820.38, "closed": true },
      { "tradeAt": "2026-09-29", "open": 26908.76, "high": 26919.01, "low": 26717.95, "close": 26797.54, "closed": true }
    ]
  }
}
```

뉴욕은 이미 9/30이지만 현재값은 9/29 장의 것이다. 현재값의 전일 종가(26820.38, 9/28 종가)가 마지막 확정 봉(9/29) 종가와 다르므로 진행 중인 봉을 붙이지 않는다(조건 2). `asOf`는 `null`이다.

**200 성공: 원/달러 월봉** (`GET /api/v1/markets/indicators/USD_KRW/chart?from=2026-07-01&interval=1M`)

값은 예시다. 환율은 소수 넷째 자리로 저장되지만 둘째 자리로 반올림해 내보낸다.

```json
{
  "data": {
    "indicator": "USD_KRW",
    "name": "원/달러 환율",
    "unit": "KRW",
    "interval": "1M",
    "from": "2026-07-01",
    "to": "2026-09-01",
    "asOf": "2026-09-30T10:15:04+09:00",
    "candles": [
      { "tradeAt": "2026-07-01", "open": 1382.50, "high": 1401.20, "low": 1371.30, "close": 1389.10, "closed": true },
      { "tradeAt": "2026-08-03", "open": 1388.00, "high": 1395.40, "low": 1360.80, "close": 1366.70, "closed": true },
      { "tradeAt": "2026-09-01", "open": 1367.20, "high": 1380.00, "low": 1348.60, "close": 1354.00, "closed": false }
    ]
  }
}
```

**봉이 없을 때** (예: 5년보다 오래된 구간을 KIS가 주지 않는 경우): `candles: []`이고 `from`, `to`, `asOf`는 `null`이다.

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `indicator` | string | X | 지표 식별자. 요청 경로의 값 |
| `name` | string | X | 한글 표시명 |
| `unit` | `"POINT"` \| `"KRW"` | X | 가격 단위. **값이 늘어날 수 있다** (유가는 `USD`) |
| `interval` | `"1D"` \| `"1W"` \| `"1M"` \| `"3M"` \| `"1Y"` | X | 적용된 봉 단위. 생략 요청이면 `"1D"` |
| `from` | string (date) | O | 첫 봉의 거래일. 봉이 없으면 `null` |
| `to` | string (date) | O | 마지막 봉의 거래일. 봉이 없으면 `null` |
| `asOf` | string (ISO-8601, 오프셋 포함) | O | 진행 중인 봉의 기준 시각. 진행 중인 봉이 없으면 `null` |
| `candles[]` | array | X | 거래일 오름차순. 빈 배열 가능 |
| `candles[].tradeAt` | string (date) | X | 봉에 포함된 첫 거래일 |
| `candles[].open` | number | X | 시가. 소수 둘째 자리 |
| `candles[].high` | number | X | 고가. 소수 둘째 자리 |
| `candles[].low` | number | X | 저가. 소수 둘째 자리 |
| `candles[].close` | number | X | 종가. 진행 중인 봉은 현재값. 라인 차트는 이 값만 쓴다. 소수 둘째 자리 |
| `candles[].closed` | boolean | X | `true`는 확정 봉, `false`는 진행 중인 봉 |

주식 차트 응답과의 차이: `stockId`, `currency`, `averageVolume`, `candles[].volume`이 없다. 대신 `indicator`, `name`, `unit`이 있다.

**400 잘못된 지표** (`/api/v1/markets/indicators/DOW/chart`, `/api/v1/markets/indicators/kospi/chart`)

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다."
  }
}
```

**400 잘못된 봉 단위·날짜·구간**: 같은 응답이다. 예: `?interval=2W`, `?from=notadate`, `?from=2026-09-29&to=2026-09-01`(역전), `?from=2015-01-01`(5년 초과).

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다."
  }
}
```

**502 시세 제공자 오류**: 일봉 동기화나 현재값 조회 중 KIS 실패, Redis 접근 불가, 첫 조회 대기 초과.

```json
{
  "error": {
    "name": "MarketDataUnavailableException",
    "code": "P007",
    "message": "시세 정보를 불러올 수 없습니다."
  }
}
```

**500 서버 내부 오류**: 예상하지 못한 예외다.

```json
{
  "error": {
    "name": "InternalServerErrorException",
    "code": "P006",
    "message": "서버 내부 오류가 발생했습니다."
  }
}
```

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| `indicator`가 허용된 값이 아님 (소문자 포함) | 400 | `P001` |
| `interval`이 허용된 값이 아님 | 400 | `P001` |
| `from`·`to` 형식 오류 | 400 | `P001` |
| `from`이 `to`보다 뒤 | 400 | `P001` |
| 구간이 5년 초과 | 400 | `P001` |
| 일봉·현재값 조회 실패 | 502 | `P007` |
| 그 밖의 예외 | 500 | `P006` |

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*MarketChart*'
Stock chart 회귀: ./gradlew test --tests 'com.swyp.ploutos.stock.chart.*'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/stock/chart/LiveCandle.java               → 신규
src/main/java/com/swyp/ploutos/stock/chart/Chart.java                    → of(...) 시그니처 변경, Quote 의존 제거
src/main/java/com/swyp/ploutos/stock/chart/service/StockChartService.java → Quote → LiveCandle 변환
src/main/java/com/swyp/ploutos/stock/price/DailyPrices.java              → lastClose() 추가
src/main/java/com/swyp/ploutos/market/quote/IndicatorQuote.java          → notOpenedToday() 추가
src/main/java/com/swyp/ploutos/market/chart/IndicatorLiveCandle.java     → 신규
src/main/java/com/swyp/ploutos/market/chart/service/                     → MarketChartService, MarketChartDetail (신규)
src/main/java/com/swyp/ploutos/market/chart/controller/                  → MarketChartController, MarketChartResponse (신규)
src/test/java/com/swyp/ploutos/stock/chart/ChartTest.java                → LiveCandle로 수정
src/test/java/com/swyp/ploutos/market/chart/**                           → 신규 테스트
```

## 코드 스타일

- 응답 DTO는 `record`이고 `@Schema`로 설명과 예시를 단다. `asOf`에는 `StockChartResponse`처럼 `@JsonFormat(without = ADJUST_DATES_TO_CONTEXT_TIME_ZONE)`을 붙인다.
- `else` 없이 guard clause를 쓴다. `@Setter`는 쓰지 않는다. 파일 끝에 개행을 넣는다.
- 오늘 날짜는 `Clock`과 `MarketIndicator.zoneId()`로 구한다.

```java
// IndicatorLiveCandle
public static Optional<LiveCandle> of(IndicatorQuote quote, DailyPrices closed, ChartRange range, LocalDate today) {
    if (quote.notOpenedToday()) {
        return Optional.empty();
    }
    if (range.to().isBefore(today)) {
        return Optional.empty();
    }
    boolean continuesLastClose = closed.lastClose()
            .filter(close -> close.compareTo(quote.previousClose()) == 0)
            .isPresent();
    if (!continuesLastClose) {
        return Optional.empty();
    }
    DailyPrice price = new DailyPrice(today, quote.open(), quote.high(), quote.low(), quote.value(), 0L);
    return Optional.of(new LiveCandle(price, quote.valueAt()));
}
```

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **단위 (60%)**
  - `IndicatorLiveCandleTest`: 조건 1~3과 붙는 경우. 실측값(KOSPI 9/30 새벽, NASDAQ 뉴욕 새벽)을 입력으로 쓴다.
  - `ChartTest`: `LiveCandle`로 바꾼 뒤에도 기존 인수 기준이 모두 통과해야 한다.
  - `MarketChartServiceTest`: 가짜 `IndicatorDailyPriceReader`·`IndicatorQuoteReader`와 고정 `Clock`으로 검증한다. 기본 구간, 지표 타임존의 오늘, `interval` 검증이 조회보다 먼저인지.
  - `MarketChartResponseTest`: 반올림, 거래량 필드 부재.
- **통합 (30%)**
  - `MarketChartControllerTest`(`@WebMvcTest` + MockMvc): 200 JSON(필드, 순서, `asOf` 오프셋, `volume` 부재), 400(지표, 소문자, `interval`, 날짜, 역전, 5년), 502.
  - `StockChartServiceTest`, `StockChartControllerTest`: 기존 테스트가 그대로 통과해야 한다(회귀).
- **E2E (10%)**
  - `@SpringBootTest`(MySQL·Redis 컨테이너)에서 KIS를 스텁하고 `GET /api/v1/markets/indicators/KOSPI/chart` 전체 흐름 1건을 검증한다. 일봉 동기화, 캐시, 진행 중인 봉까지 지난다.
- `ArchitectureTest`가 통과해야 한다. `market.chart` 루트가 `stock.chart` 루트에 의존하는 것은 규칙 4(도메인 → 계층 금지)에 걸리지 않는다.
- 테스트에서 실제 KIS를 호출하지 않는다.

## 경계

- **항상:** 확정 봉은 `IndicatorDailyPriceReader`로만, 진행 중인 봉은 `IndicatorQuoteReader`로만 얻는다. 집계는 `stock.chart`의 `Chart`로만 한다. `Chart`를 바꾸면 주식 차트 테스트를 함께 돌린다. 커밋 전 `./gradlew test`.
- **먼저 묻기:**
  - 응답 필드의 추가나 이름 변경(프론트 계약). 거래량을 되살리는 것도 포함한다.
  - 봉 단위 추가(분봉 등).
  - 주식 차트에 조건 2·3을 적용하기(주식 차트 동작 변경).
  - 봉 집계 코드를 공용 패키지로 옮기기.
- **절대 안 함:** 차트 API에서 KIS를 직접 호출, KIS 주봉·월봉 API 사용, 진행 중인 봉 저장, 주식 차트 응답이나 동작 변경.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 장중이면 진행 중인 봉을 오늘 날짜로 붙인다 (전일 종가 = 마지막 확정 봉 종가). | `IndicatorLiveCandleTest.전일종가가_마지막_확정봉_종가와_같으면_오늘_봉을_붙인다` |
| 2 | 전일 종가가 마지막 확정 봉 종가와 다르면 붙이지 않는다 (KOSPI 9/30 새벽 실측값). | `전일종가가_마지막_확정봉_종가와_다르면_붙이지_않는다` |
| 3 | 확정 봉이 없으면 붙이지 않는다. | `확정봉이_없으면_붙이지_않는다` |
| 4 | 시가가 0이면 붙이지 않는다. | `시가가_0이면_붙이지_않는다` |
| 5 | 구간의 끝이 오늘보다 이전이면 붙이지 않는다. | `구간의_끝이_오늘보다_이전이면_붙이지_않는다` |
| 6 | 자릿수가 달라도 같은 값이면 같은 종가로 본다 (`1359.9000` = `1359.90`). | `자릿수가_달라도_같은_값이면_이어진_것으로_본다` |
| 7 | `Chart`가 `LiveCandle`을 받아도 기존 주식 차트 인수 기준이 모두 통과한다. | `ChartTest` 전체, `StockChartServiceTest` 전체, `StockChartControllerTest` 전체 |
| 8 | 오늘은 지표 타임존으로 계산한다 (NASDAQ은 뉴욕). | `MarketChartServiceTest.오늘은_지표_타임존으로_계산한다` |
| 9 | 구간을 생략하면 지표 타임존의 오늘까지 최근 2개월이다. | `구간을_생략하면_오늘까지_최근_2개월을_조회한다` |
| 10 | 잘못된 봉 단위면 일봉·현재값을 조회하지 않고 예외를 던진다. | `잘못된_봉_단위면_조회하지_않고_예외를_던진다` |
| 11 | OHLC를 소수 둘째 자리로 반올림하고 거래량 필드는 없다. | `MarketChartResponseTest.가격을_소수_둘째자리로_반올림한다`, `거래량_필드가_없다` |
| 12 | 200 응답이 API 계약의 필드, 순서, 오프셋과 같다. | `MarketChartControllerTest.지표_차트를_요청하면_봉을_오름차순으로_반환한다` |
| 13 | 잘못된 지표(소문자 포함)면 400 / `P001`. | `잘못된_지표면_400과_P001을_반환한다`, `소문자_지표면_400과_P001을_반환한다` |
| 14 | 잘못된 봉 단위, 날짜 형식, 역전, 5년 초과면 400 / `P001`. | `잘못된_봉_단위면_400과_P001을_반환한다`, `잘못된_날짜_형식이면_400과_P001을_반환한다`, `시작일이_종료일보다_뒤면_400과_P001을_반환한다`, `구간이_5년을_넘으면_400과_P001을_반환한다` |
| 15 | 시세 조회에 실패하면 502 / `P007`. | `시세_조회에_실패하면_502와_P007을_반환한다` |
| 16 | KIS 스텁에서 KOSPI 차트 요청 전체 흐름이 확정 봉과 진행 중인 봉을 돌려준다. | `MarketChartE2ETest.코스피_차트는_확정봉과_진행중인_봉을_돌려준다` |

## 미해결 질문

- **장중에 지수 일봉이 오늘 날짜의 행을 주는가?** 준다면 `market-daily-price`가 저장 전에 버리므로 차트는 영향이 없다. 한국 장중 실측(`kis-probe-delay.sh`)으로 확인한다.
- **환율 전날 행이 언제 들어오는가?** 새벽 5시에는 9/29 행이 없어서 9/29 값이 9/30 날짜의 진행 중인 봉으로 붙는다("알려진 한계"). 한국 낮에 들어와 있다면 새벽 몇 시간만의 문제라 보정하지 않는다. 계속 늦다면 진행 중인 봉 날짜를 보정할지 정한다.
- **조건 2의 예외:** KIS가 전일 종가를 정정해서 저장된 종가와 달라지면 장중에도 진행 중인 봉이 빠진다. 실측에서 본 적은 없다. 운영 중에 발견하면 허용 오차를 둘지 정한다.

## 추후 구현

1. 유가: `MarketIndicator.WTI`가 추가되면 코드 변경 없이 `/api/v1/markets/indicators/WTI/chart`가 동작한다. 선물 월물이 바뀌는 날 종가가 이어지지 않으면 조건 2가 진행 중인 봉을 빼므로, 월물 규칙을 정할 때 함께 본다.
2. 주식 차트에 조건 2·3 적용 여부 검토 (`SPEC-stock-chart.md`).
