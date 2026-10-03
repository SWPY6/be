# 명세: 시장 지표 카드 (market-summary)

시장 요약 화면 "시장 한눈에 보기"의 지표 카드 API 명세다. 국내·해외 탭을 받아, 그 탭에 속한 지표의 현재값·등락폭·등락률을 한 번에 준다.

기능 맵: `CAPABILITY-MAP-market.md`. 의존 대상: `market-indicator`, `market-quote`.

## 내가 세운 전제

1. 경로 접두어는 `/api/v1`이고 인증은 없다. 현재 `SecurityConfig`는 `permitAll`이다.
2. 프론트는 이 API를 주기적으로 폴링한다. 서버는 푸시하지 않는다.
3. **탭 단위로 요청한다.** `region` 쿼리 파라미터가 필수다. 기능 맵의 `?region=DOMESTIC|OVERSEAS` 결정을 따른다.
4. **전부 성공하거나 전부 실패한다.** 탭의 지표 중 하나라도 시세를 얻지 못하면 502 `P007`로 응답한다. 일부 카드만 비워 보내지 않는다. KIS 실패 시 폴백하지 않는 공통 결정과 같다.
5. 숫자는 JSON number, 시각은 오프셋을 포함한 ISO-8601 문자열이다. 부호, `%`, 단위 표기(`pt`, `원`)는 프론트가 한다.

## 목표

탭 전환(RQ-0301 "국내·해외시장 전환 시 지표가 해당 시장 기준으로 변경된다")과 카드 표시에 필요한 값을 **한 번의 호출**로 준다.
- 지표 구성과 순서는 서버(`MarketIndicator.in(region)`)가 정한다. 그래서 유가를 추가해도 프론트 코드를 바꾸지 않고 카드가 늘어난다.
- 값은 `market-quote` 캐시에서 나온다. 사용자 수와 무관하게 KIS 호출은 지표당 10초에 최대 1회다.

## 규칙

### 흐름

1. `region`을 `MarketRegion`으로 바인딩한다. 누락되거나 잘못된 값이면 400 `P001`.
2. `MarketIndicator.in(region)`으로 탭의 지표를 표시 순서대로 얻는다.
3. 지표마다 `IndicatorQuoteReader.read(indicator)`를 **순서대로** 호출한다.
   - 병렬로 호출하지 않는다. 캐시가 차 있으면 Redis 조회 3회라 빠르다.
   - 캐시가 비었을 때 KIS를 동시에 3번 부르면 호출 한도(`EGW00201`)에 걸리기 쉽다. 실측에서 모의 도메인은 3초 간격도 한도에 걸렸다.
4. 하나라도 예외(`P007`)가 나면 그대로 던진다. 이미 읽은 지표의 값은 버린다.
5. 응답을 만들며 반올림한다.

### 값 계산 (응답을 만들 때)

| 응답 필드 | 규칙 |
| --- | --- |
| `value` | `IndicatorQuote.value()`를 소수 둘째 자리로 반올림(HALF_UP) |
| `change` | `IndicatorQuote.change()`(원값으로 계산)를 소수 둘째 자리로 반올림 |
| `changeRate` | `IndicatorQuote.changeRate()` 그대로 (이미 소수 둘째 자리) |
| `valueAt` | `IndicatorQuote.valueAt()` 그대로 |
| `name`, `unit` | `MarketIndicator.displayName()`, `unit()` |

- 반올림한 값끼리 다시 계산하지 않는다. 환율 원값은 소수 넷째 자리(`1354.0000`)다. 등락폭은 원값끼리 빼고 나서 반올림한다.
- 반올림은 **표시 규칙**이라 응답 DTO(`MarketSummaryResponse`)가 한다. `IndicatorQuote`는 원값을 유지한다 (`SPEC-market-quote.md`).

### 설계

- `MarketSummaryService` (`market/summary/service`, `@Service`, `public`)
  - `MarketSummary read(MarketRegion region)`: 흐름 2~4.
  - 컨트롤러가 다른 패키지라 `public`이다. `StockQuoteDetailService`와 같은 이유다.
- `MarketSummary` (`market/summary/service`, record): `MarketRegion region`, `List<IndicatorQuote> quotes`. 서비스 결과 묶음이다. `StockQuoteDetail`과 같은 역할이다.
- `MarketSummaryController` (`market/summary/controller`, package-private)
  - `GET /api/v1/markets/summary` → `ApiResult<MarketSummaryResponse>`.
  - `MarketSummaryService` 하나만 의존한다. 컨트롤러는 자기 모듈의 `service`에만 의존한다.
  - `@Operation`, `@ApiResponses`에 성공·오류 예시를 단다. `StockQuoteController`와 같은 형식이다.
- `MarketSummaryResponse` (`market/summary/controller`, record, package-private)
  - `from(MarketSummary)`로 만들고, 중첩 record `IndicatorCard`를 둔다.
  - 변형이 하나뿐이라 `sealed interface` 없이 record로 둔다 (`StockQuoteResponse` 선례).
- 새 `ErrorCode`는 추가하지 않는다. `region` 바인딩 실패는 `GlobalExceptionHandler`가 이미 `P001`로 바꾼다.
  - 누락은 `MissingRequestValueException`, 타입 불일치는 `MethodArgumentTypeMismatchException`이다 (`SPEC-api-response.md`).

### 캐시가 빈 요청의 지연 (결정)

캐시가 비어 있으면 그 요청이 KIS를 불러 캐시를 채우고, TTL(10초) 동안 다른 요청은 캐시에서 받는다. `market-quote`의 cache-aside 흐름 그대로다.
- 같은 탭의 지표 3개는 함께 채워지므로 함께 만료된다. 그래서 10초마다 요청 1건이 KIS를 순서대로 3번 부른다. 약 0.3~1초 걸린다.
- 이 지연은 받아들인다 (사용자 결정). 10초에 1건이고, 나머지 요청은 캐시에서 바로 받는다.
- 문제가 되면 병렬 호출보다 `market-quote`의 refresh-ahead를 먼저 검토한다.

## API 계약

프론트엔드에 전달하는 계약이다.
- 봉투 규칙은 `SPEC-api-response.md`를 따른다.
- 구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다.
- 예시의 숫자는 2026-09-30 KIS 실측값이다.

### `GET /api/v1/markets/summary?region={region}`

| 파라미터 | 위치 | 필수 | 값 |
| --- | --- | --- | --- |
| `region` | query | O | `DOMESTIC` \| `OVERSEAS` (대문자, 대소문자 구분) |

권장 폴링 주기는 10초다. `market-quote` 캐시 TTL과 같다.

**200 성공: 국내 탭** (`GET /api/v1/markets/summary?region=DOMESTIC`)

```json
{
  "data": {
    "region": "DOMESTIC",
    "indicators": [
      {
        "indicator": "KOSPI",
        "name": "코스피",
        "unit": "POINT",
        "value": 6870.81,
        "change": -18.93,
        "changeRate": -0.27,
        "valueAt": "2026-09-30T14:31:05+09:00"
      },
      {
        "indicator": "KOSDAQ",
        "name": "코스닥",
        "unit": "POINT",
        "value": 849.80,
        "change": 3.22,
        "changeRate": 0.38,
        "valueAt": "2026-09-30T14:31:05+09:00"
      },
      {
        "indicator": "USD_KRW",
        "name": "원/달러 환율",
        "unit": "KRW",
        "value": 1354.00,
        "change": -5.90,
        "changeRate": -0.43,
        "valueAt": "2026-09-30T14:31:06+09:00"
      }
    ]
  }
}
```

**200 성공: 해외 탭** (`GET /api/v1/markets/summary?region=OVERSEAS`)

```json
{
  "data": {
    "region": "OVERSEAS",
    "indicators": [
      {
        "indicator": "NASDAQ",
        "name": "나스닥",
        "unit": "POINT",
        "value": 26817.30,
        "change": -3.08,
        "changeRate": -0.01,
        "valueAt": "2026-09-30T01:31:05-04:00"
      },
      {
        "indicator": "SP500",
        "name": "S&P 500",
        "unit": "POINT",
        "value": 7675.05,
        "change": -8.64,
        "changeRate": -0.11,
        "valueAt": "2026-09-30T01:31:05-04:00"
      },
      {
        "indicator": "USD_KRW",
        "name": "원/달러 환율",
        "unit": "KRW",
        "value": 1354.00,
        "change": -5.90,
        "changeRate": -0.43,
        "valueAt": "2026-09-30T14:31:06+09:00"
      }
    ]
  }
}
```

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `region` | `"DOMESTIC"` \| `"OVERSEAS"` | X | 요청한 탭 |
| `indicators` | array | X | 탭의 지표 카드. 표시 순서대로 온다. 프론트는 받은 순서 그대로 그린다 |
| `indicators[].indicator` | string | X | 지표 식별자. `KOSPI` \| `KOSDAQ` \| `NASDAQ` \| `SP500` \| `USD_KRW`. 차트 API 경로에 그대로 쓴다. **값이 늘어날 수 있다** (유가 추가 예정). 모르는 값도 `name`과 `unit`으로 그릴 수 있게 한다 |
| `indicators[].name` | string | X | 한글 표시명 |
| `indicators[].unit` | `"POINT"` \| `"KRW"` | X | `value`와 `change`의 단위. 지수는 포인트, 환율은 1달러당 원. **값이 늘어날 수 있다** (유가는 `USD`) |
| `indicators[].value` | number | X | 현재값. 소수 둘째 자리 |
| `indicators[].change` | number | X | 직전 거래일 종가 대비 등락폭. 소수 둘째 자리. 음수 가능 |
| `indicators[].changeRate` | number | X | 등락률 %. 소수 둘째 자리. 음수 가능 |
| `indicators[].valueAt` | string (ISO-8601, 오프셋 포함) | X | 값의 기준 시각 = 서버가 KIS에서 받은 시각. 국내 지수와 환율은 `+09:00`, 해외 지수는 뉴욕 시간(`-04:00`/`-05:00`). 최대 10초(캐시 TTL) 전 값일 수 있다 |

- 환율 카드는 두 탭에서 같은 값이다. 같은 캐시 키를 쓴다.
- 해외 지수의 `valueAt`은 미국 날짜다. 한국 시간 새벽에는 국내 카드와 날짜가 다르게 보인다.

**400 `region` 누락** (`GET /api/v1/markets/summary`)

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다."
  }
}
```

**400 `region` 값이 잘못됨** (`?region=KOREA`, `?region=domestic`)

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다."
  }
}
```

**502 시세 제공자 오류**: 탭의 지표 중 하나라도 시세를 얻지 못한 경우다. 원인은 KIS 실패(토큰, 응답 코드, HTTP 오류, 타임아웃), Redis 접근 불가, 첫 조회 대기(3초) 초과다.

```json
{
  "error": {
    "name": "MarketDataUnavailableException",
    "code": "P007",
    "message": "시세 정보를 불러올 수 없습니다."
  }
}
```

**500 서버 내부 오류**: 예상하지 못한 예외다. 원인은 서버 로그에만 남는다.

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
| `region` 누락 | 400 | `P001` |
| `region`이 `DOMESTIC`/`OVERSEAS`가 아님 (소문자 포함) | 400 | `P001` |
| 지표 하나라도 시세 조회 실패 | 502 | `P007` |
| 그 밖의 예외 | 500 | `P006` |

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*MarketSummary*'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/market/summary/service/    → MarketSummaryService, MarketSummary (신규)
src/main/java/com/swyp/ploutos/market/summary/controller/ → MarketSummaryController, MarketSummaryResponse (신규)
src/test/java/com/swyp/ploutos/market/summary/**          → 단위·슬라이스·E2E 테스트
```

- 모듈 루트에 도메인 클래스가 없다. 카드 모듈은 지표(`market` 루트)와 시세(`market.quote`)를 모아 보여 주기만 한다.

## 코드 스타일

- 응답 DTO는 `record`이고 `@Schema`로 설명과 예시를 단다.
- 반올림은 응답 DTO의 정적 팩토리에서 한다.
- `else` 없이 guard clause를 쓴다. `@Setter`는 쓰지 않는다. 파일 끝에 개행을 넣는다.

```java
// MarketSummaryService
public MarketSummary read(MarketRegion region) {
    List<IndicatorQuote> quotes = MarketIndicator.in(region).stream()
            .map(indicatorQuoteReader::read)
            .toList();
    return new MarketSummary(region, quotes);
}

// MarketSummaryResponse.IndicatorCard
static IndicatorCard from(IndicatorQuote quote) {
    MarketIndicator indicator = quote.indicator();
    return new IndicatorCard(
            indicator,
            indicator.displayName(),
            indicator.unit(),
            quote.value().setScale(SCALE, RoundingMode.HALF_UP),
            quote.change().setScale(SCALE, RoundingMode.HALF_UP),
            quote.changeRate(),
            quote.valueAt()
    );
}
```

- `.stream().map(...).toList()`는 순차 스트림이라 흐름 3의 "순서대로 호출"을 지킨다. `parallelStream`을 쓰지 않는다.

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **단위 (60%)**
  - `MarketSummaryServiceTest`: 가짜 `IndicatorQuoteReader`로 탭별 지표와 순서, 한 지표 실패 시 전체 예외, 호출 순서를 검증한다.
  - `MarketSummaryResponseTest`: 반올림(환율 넷째 자리 → 둘째 자리), 원값으로 계산한 등락폭, 표시명·단위 매핑.
- **통합 (30%)**
  - `MarketSummaryControllerTest`(`@WebMvcTest` + MockMvc): 200 JSON 본문(필드명, 순서, 숫자, 오프셋), 400(누락, 잘못된 값, 소문자), 502.
- **E2E (10%)**
  - `@SpringBootTest`(MySQL·Redis 컨테이너)에서 KIS를 스텁하고 `GET /api/v1/markets/summary?region=DOMESTIC` 전체 흐름 1건을 검증한다.
  - `market-quote`의 KIS 어댑터와 Redis 캐시를 함께 지난다.
- `ArchitectureTest`가 통과해야 한다. 새 규칙은 추가하지 않는다.
- 테스트에서 실제 KIS를 호출하지 않는다.

## 경계

- **항상:** 탭 구성과 순서는 `MarketIndicator.in(region)`에서만 얻는다. 응답 형식이 바뀌면 이 명세와 Swagger를 먼저 고친다. 커밋 전 `./gradlew test`.
- **먼저 묻기:**
  - 응답 필드의 추가나 이름 변경(프론트 계약).
  - 부분 실패 허용(일부 카드만 응답).
  - `region` 없이 두 탭을 한 번에 주는 방식으로 변경.
  - 지표 병렬 조회.
  - `region`의 대소문자 무시.
- **절대 안 함:** 컨트롤러가 `IndicatorQuoteReader`나 Redis·KIS에 직접 의존, 서버에 탭 구성을 하드코딩(enum 우회), 만료된 값으로 폴백, KIS 원본 필드명 노출.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 국내 탭은 KOSPI, KOSDAQ, USD_KRW 시세를 이 순서로 돌려준다. | `MarketSummaryServiceTest.국내_탭은_코스피_코스닥_환율_순서로_돌려준다` |
| 2 | 해외 탭은 NASDAQ, SP500, USD_KRW 시세를 이 순서로 돌려준다. | `해외_탭은_나스닥_SP500_환율_순서로_돌려준다` |
| 3 | 한 지표라도 시세 조회에 실패하면 `P007` 예외를 던진다. | `한_지표라도_실패하면_예외를_던진다` |
| 3-1 | 지표를 표시 순서대로 하나씩 읽는다(동시에 부르지 않는다). | `지표를_표시_순서대로_하나씩_읽는다` |
| 4 | 현재값과 등락폭을 소수 둘째 자리로 반올림한다 (환율 넷째 자리 원값 포함). | `MarketSummaryResponseTest.현재값과_등락폭을_소수_둘째자리로_반올림한다` |
| 5 | 등락폭은 원값으로 계산한 뒤 반올림한다. | `등락폭은_원값으로_계산한_뒤_반올림한다` |
| 6 | 카드에 표시명과 단위가 담긴다. | `카드에_표시명과_단위를_담는다` |
| 7 | 200 응답이 API 계약의 필드·순서·오프셋과 같다. | `MarketSummaryControllerTest.국내_탭을_요청하면_지표_카드를_순서대로_반환한다` |
| 8 | `region`이 없으면 400 / `P001`. | `탭이_없으면_400과_P001을_반환한다` |
| 9 | `region`이 잘못됐거나 소문자면 400 / `P001`. | `탭_값이_잘못되면_400과_P001을_반환한다`, `탭_값이_소문자면_400과_P001을_반환한다` |
| 10 | 시세 조회에 실패하면 502 / `P007`. | `시세_조회에_실패하면_502와_P007을_반환한다` |
| 10-1 | 해외 탭은 뉴욕 오프셋으로 응답한다. | `MarketSummaryControllerTest.해외_탭을_요청하면_뉴욕_시각으로_반환한다` |
| 11 | KIS 스텁에서 국내 탭 요청 전체 흐름이 200과 카드 3개를 돌려준다. | `MarketSummaryE2ETest.국내_탭_요청은_지표_카드_3개를_돌려준다` |
| 11-1 | 두 번째 요청은 캐시로 답하고, 응답 본문이 첫 요청과 같다. | `같은_탭을_다시_요청하면_캐시로_답해_외부를_호출하지_않는다` |

## 미해결 질문

- **해외 지수와 환율이 실시간인가, 지연인가?** 지연이라면 카드에 지연 표시(`priceTiming` 같은 필드)가 필요하다. 필드 추가는 프론트 계약 변경이라 먼저 묻는다. `SPEC-market-quote.md`의 미해결 질문과 같다.

## 추후 구현

1. 유가: `MarketIndicator.WTI`가 추가되면 이 모듈은 코드 변경 없이 카드가 4개가 된다. API 계약의 `indicator`와 `unit` 값 목록, 예시만 갱신한다.
