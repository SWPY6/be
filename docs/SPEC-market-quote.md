# 명세: 시장 지표 현재값 (market-quote)

시장 지표 5종의 현재값, 직전 종가, 당일 시가·고가·저가를 KIS에서 받아 Redis에 캐시하고 다른 모듈에 노출하는 모듈이다.
HTTP API가 없다. `market-summary`가 카드를 만들 때, `market-chart`가 당일 진행 중인 봉을 만들 때 이 모듈을 쓴다.

기능 맵: `CAPABILITY-MAP-market.md`. 의존 대상: `market-indicator`, `kis-client`.

## 내가 세운 전제

1. 지표는 `MarketIndicator` enum으로 식별한다 (`SPEC-market-indicator.md`). KIS 파라미터는 지표의 `kind()`와 `symbol()`로 만든다.
2. 캐시는 `stock-quote`가 이미 쓰는 Redis를 그대로 쓴다. 새 의존성이나 인프라는 없다.
3. 지표가 5개로 고정이라 `stock-quote`의 refresh-ahead(활성 종목 추적 + `QuoteRefresher`)는 두지 않는다. 캐시가 비었을 때만 KIS를 부른다 (cache-aside). 기능 맵의 "스케줄러 두지 않는다" 결정과 같다.
4. 숫자는 KIS 원값을 보존한다. 표시용 반올림은 응답을 만드는 `market-summary`가 한다.
5. 거래량과 실시간 여부(`priceTiming`)는 다루지 않는다. 거래량은 기능 맵에서 제외했고, 해외 지수의 실시간 여부는 확인되지 않았다 (미해결 질문).

## 목표

카드와 차트가 쓸 지표 시세를 **한 곳에서, KIS 호출을 억제하며** 제공한다.
- 사용자가 몇 명이든 KIS 호출은 지표당 TTL(10초)마다 최대 1회다. 지표 5개라 초당 0.5회가 상한이다.
- 등락률 공식은 `stock-quote`의 `Quote`와 같게 해서 주식과 지표의 기준을 통일한다.

## 규칙

### 값 계산

| 값 | 규칙 |
| --- | --- |
| `value` | KIS 현재값 그대로 |
| `previousClose` | 직전 거래일 종가. 국내 지수는 KIS가 종가를 따로 주지 않아 `현재값 − 전일 대비`로 구한다 (아래 표) |
| `open`, `high`, `low` | KIS 당일 값 그대로 |
| `change()` | `value − previousClose` |
| `changeRate()` | `(value − previousClose) / previousClose × 100`, 소수 둘째 자리 반올림(HALF_UP). KIS의 등락률 필드(`bstp_nmix_prdy_ctrt`, `prdy_ctrt`)는 쓰지 않는다. `previousClose`가 0이면 `0.00` |
| `valueAt` | 서버가 KIS 응답을 받은 시각. 지표 타임존(`MarketIndicator.zoneId()`)의 오프셋을 붙인다. 캐시에서 응답할 때도 바꾸지 않는다 |

- `change()`와 `changeRate()` 공식은 `stock.quote.Quote`와 같다. 5줄짜리 중복이지만 지금은 공통화하지 않는다. `Quote`에는 시가총액·거래량 같은 주식 전용 필드가 있어서, 합치면 지표에 주식 개념이 섞인다.
- 봉 집계 도메인을 공용 위치로 옮기는 `SPEC-market-chart.md`에서 다시 검토한다.

### KIS 필드 대응

2026-09-30 실측 응답 기준이다 (`CAPABILITY-MAP-market.md` "KIS 실측 결과").

| 값 | `DOMESTIC_INDEX`: `FHPUP02100000` `output` | `OVERSEAS_INDEX`, `EXCHANGE_RATE`: `FHKST03030100` `output1` |
| --- | --- | --- |
| `value` | `bstp_nmix_prpr` | `ovrs_nmix_prpr` |
| `previousClose` | `bstp_nmix_prpr − bstp_nmix_prdy_vrss` | `ovrs_nmix_prdy_clpr` |
| `open` | `bstp_nmix_oprc` | `ovrs_prod_oprc` |
| `high` | `bstp_nmix_hgpr` | `ovrs_prod_hgpr` |
| `low` | `bstp_nmix_lwpr` | `ovrs_prod_lwpr` |

- 국내 지수의 `bstp_nmix_prdy_vrss`에는 부호가 붙어 온다.
  - 실측값: `6870.81 − (−18.93) = 6889.74`
  - 이 값은 일봉 TR(`FHKUP03500100`) `output1`의 전일 지수 `prdy_nmix`(`6889.74`)와 같다. 그래서 `prdy_vrss_sign`은 쓰지 않는다.
  - KOSDAQ도 같다: `849.80 − 3.22 = 846.58` = `prdy_nmix`.
- 빈 문자열은 0으로 읽는다. 이 규칙을 담은 `KisNumbers`를 재사용한다.

| `IndicatorKind` | API | 파라미터 |
| --- | --- | --- |
| `DOMESTIC_INDEX` | `GET /uapi/domestic-stock/v1/quotations/inquire-index-price`, `FHPUP02100000` | `FID_COND_MRKT_DIV_CODE=U`, `FID_INPUT_ISCD={symbol}` |
| `OVERSEAS_INDEX` | `GET /uapi/overseas-price/v1/quotations/inquire-daily-chartprice`, `FHKST03030100` | `FID_COND_MRKT_DIV_CODE=N`, `FID_INPUT_ISCD={symbol}`, `FID_INPUT_DATE_1`·`FID_INPUT_DATE_2`=지표 타임존의 오늘(`yyyyMMdd`), `FID_PERIOD_DIV_CODE=D` |
| `EXCHANGE_RATE` | 위와 같음 | 위와 같되 `FID_COND_MRKT_DIV_CODE=X` |

- 해외 TR은 일봉 API지만 `output1`에 현재값이 함께 온다. 이 모듈은 `output1`만 쓰고 `output2`(일봉 목록)는 버린다. 기간을 오늘 하루로 주는 것은 응답 크기를 줄이기 위해서다.
- 기간을 오늘 하루로 줘도 `output1`이 채워진다. NASDAQ과 환율 모두 2026-09-30에 실측했고, `output2`는 0건이었다.
- 어댑터는 `IndicatorKind`를 `switch`로 분기하고 `default`를 두지 않는다. 유가를 넣을 때 `OVERSEAS_FUTURES`를 추가하면 컴파일러가 누락된 분기를 알려 준다.
- TR ID와 시장구분 코드(`U`, `N`, `X`)는 어댑터에만 둔다 (`SPEC-market-indicator.md`).

### 캐시 (Redis, cache-aside)

| 키 | 타입 | 값 | TTL |
| --- | --- | --- | --- |
| `market-quote:{indicator}` | string | `IndicatorQuote`의 JSON(Jackson). 예: `market-quote:KOSPI` | `ploutos.market-quote.cache-ttl-seconds`, 기본 10초 |
| `market-quote:lock:{indicator}` | string | 캐시 미스용 single-flight 락 | 3초 |

**조회 흐름** (`StockQuoteService`와 같다. 활성 표시만 없다)

1. 캐시에 값이 있으면 그대로 반환한다. `valueAt`은 바꾸지 않는다.
2. 없으면 `SET market-quote:lock:{indicator} 1 NX EX 3`으로 락을 시도한다.
   - 락을 잡은 요청만 KIS를 호출하고, 결과를 저장한 뒤 반환한다. 락은 호출 성공·실패와 무관하게 해제한다.
   - 락을 못 잡은 요청은 최대 3초(락 TTL) 동안 50ms 간격으로 캐시를 다시 읽는다. 채워지면 그 값을 반환한다. 3초가 지나도 비어 있으면 KIS를 호출하지 않고 `P007`을 던진다.

**실패 규칙**

| 상황 | 동작 |
| --- | --- |
| Redis 접근 불가 | KIS를 직접 호출하지 않고 `P007`. 장애 중 요청이 모두 KIS로 몰리는 것을 막는다 |
| KIS 호출 실패 | 만료된 캐시로 폴백하지 않고 `P007`. 오래된 값을 현재값처럼 보이지 않게 한다 |
| 락 해제 실패 | 로그만 남긴다. 락은 TTL로 풀린다 |
| 저장된 JSON을 읽지 못함 | 캐시 미스로 본다 |

**refresh-ahead를 두지 않는 이유**
- `stock-quote`는 종목 수가 열려 있다. 그래서 "최근 조회된 종목"을 추적하고 서버가 미리 채운다.
- 지표는 5개로 닫혀 있다. 캐시가 빌 때마다 요청 1건이 KIS를 부르는 비용이 작다.
- 대신 TTL이 지난 직후 첫 요청은 KIS 응답 시간만큼 느려진다. 스케줄러가 없으니 장 마감 후나 주말에 아무도 보지 않으면 KIS를 부르지 않는다.

**`RedisQuoteCache`와 공통화하지 않는 이유**
- 키 타입이 다르다(`Long` 대 `MarketIndicator`).
- 활성 목록과 갱신 리더 락이 이쪽에는 없다.
- 공통 부분은 find, put, lock, unlock 네 메서드다. 제네릭 캐시를 만들면 두 모듈이 한 추상화에 묶이는 비용이 더 크다.

### 설정

| 프로퍼티 | 설명 |
| --- | --- |
| `ploutos.market-quote.cache-ttl-seconds` | 캐시 값 TTL(초), 기본 10. 1 이상이어야 한다 |

- Redis 접속 설정(`spring.data.redis.*`)은 `stock-quote`의 것을 그대로 쓴다.

### 노출 인터페이스

```java
public interface IndicatorQuoteReader {

    /** 지표의 현재값. 캐시에 없으면 KIS에서 받아 캐시한다. 실패하면 P007. */
    IndicatorQuote read(MarketIndicator indicator);
}
```

- 여러 지표를 한 번에 읽는 메서드는 두지 않는다. 카드 목록에서 한 지표만 실패할 때의 처리는 `market-summary`가 정하므로, 그쪽이 지표마다 `read`를 호출한다.

```java
public record IndicatorQuote(
        MarketIndicator indicator,
        BigDecimal value,
        BigDecimal previousClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime valueAt
) {
    public BigDecimal change() { ... }
    public BigDecimal changeRate() { ... }
}
```

- `valueAt`의 `@JsonFormat`은 `Quote.priceAt`과 같은 이유로 붙인다. 붙이지 않으면 Jackson이 캐시에서 읽을 때 오프셋을 UTC로 바꾼다. 그러면 캐시 히트 응답만 지표 타임존과 다른 값이 나간다.
- 당일 봉에 필요한 판단(예: 개장 전인지)은 `SPEC-market-chart.md`에서 필요해질 때 메서드로 추가한다.

### 설계

- `IndicatorQuoteProvider`(포트): `IndicatorQuote fetch(MarketIndicator indicator)`.
  - 구현 `KisIndicatorQuoteProvider`가 `kind()`로 API를 고르고 필드를 매핑한다.
  - 시각은 `Clock`으로 얻는다.
- `IndicatorQuoteCache`(포트): `Optional<IndicatorQuote> find(MarketIndicator)`, `void put(MarketIndicator, IndicatorQuote)`, `boolean tryLock(MarketIndicator)`, `void unlock(MarketIndicator)`.
  - 구현 `RedisIndicatorQuoteCache`가 `StringRedisTemplate`과 `JsonMapper`를 쓴다.
  - Redis 접근 실패(`DataAccessException`)는 `BusinessException(MARKET_DATA_UNAVAILABLE)`로 바꿔 던진다.
- `IndicatorQuoteService`: `IndicatorQuoteReader` 구현체. 캐시 → 락 → provider → 저장 → 해제.
- `KisNumbers` 이동: 지금은 `stock.quote.kis`에 package-private로 있다.
  - 두 번째 사용처가 생기므로 `external.kis`로 옮겨 `public`으로 만든다.
  - `external`은 `common`에만 의존하므로 `ArchitectureTest`의 규칙 1을 지킨다.
  - `stock.quote.kis`의 `KisDomesticPriceResponse`와 `KisOverseasPriceResponse`는 import만 바뀐다.

## API 응답 (프론트 전달용)

이 모듈에는 HTTP API가 없다. 지표 시세는 카드 API `GET /api/v1/markets/summary`로 프론트에 나간다. 성공·실패 응답 계약은 `SPEC-market-summary.md`의 "API 계약"을 본다.

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*IndicatorQuote*'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/market/quote/IndicatorQuote.java             → 값 객체 (신규)
src/main/java/com/swyp/ploutos/market/quote/service/                       → IndicatorQuoteReader, IndicatorQuoteProvider,
                                                                             IndicatorQuoteCache, IndicatorQuoteService (신규)
src/main/java/com/swyp/ploutos/market/quote/kis/                           → KisIndicatorQuoteProvider, KisDomesticIndexPriceResponse,
                                                                             KisOverseasChartPriceResponse (신규)
src/main/java/com/swyp/ploutos/market/quote/redis/                         → RedisIndicatorQuoteCache, IndicatorQuoteCacheProperties (신규)
src/main/java/com/swyp/ploutos/external/kis/KisNumbers.java               → stock.quote.kis에서 이동
src/main/java/com/swyp/ploutos/stock/quote/kis/Kis*PriceResponse.java     → import 변경
src/main/resources/application.properties                                → ploutos.market-quote.cache-ttl-seconds=10
src/test/java/com/swyp/ploutos/market/quote/                               → 단위·통합 테스트
```

- `IndicatorQuoteReader`와 `IndicatorQuote`만 `public`이다.
- 서비스, 어댑터, 캐시 구현, KIS 응답 DTO, 설정 record는 package-private다.
- `IndicatorQuoteProvider`와 `IndicatorQuoteCache`는 다른 패키지(`kis`, `redis`)가 구현하므로 `public`이다. `stock-quote`의 `QuoteProvider`, `QuoteCache`와 같다.

## 코드 스타일

- KIS 응답 DTO는 `record`이고 `toQuote(...)`로 값 객체를 만든다. `KisDomesticPriceResponse.toQuote`와 같은 방식이다.
- 값 객체는 불변이다. 계산은 메서드로 노출한다.
- `else` 없이 guard clause를 쓴다. `@Setter`는 쓰지 않는다. 파일 끝에 개행을 넣는다.

```java
// KisIndicatorQuoteProvider
@Override
public IndicatorQuote fetch(MarketIndicator indicator) {
    return switch (indicator.kind()) {
        case DOMESTIC_INDEX -> fetchDomesticIndex(indicator);
        case OVERSEAS_INDEX -> fetchOverseas(indicator, "N");
        case EXCHANGE_RATE -> fetchOverseas(indicator, "X");
    };
}
```

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **단위 (60%)**
  - `IndicatorQuoteTest`: 등락률 계산, 전일 종가 0 처리, 원값 보존.
  - `IndicatorQuoteServiceTest`: 가짜 `IndicatorQuoteCache`·`IndicatorQuoteProvider`로 캐시 히트, 락을 잡은 쪽만 호출, 대기 후 반환, 대기 초과, Redis 장애, KIS 실패 시 락 해제를 검증한다.
- **통합 (30%)**
  - `KisIndicatorQuoteProviderTest`: `MockRestServiceServer`로 세 종류의 파라미터와 필드 매핑, 국내 전일 종가 역산, `valueAt` 타임존을 검증한다. 응답 본문은 이 대화의 실측 응답을 그대로 쓴다.
  - `RedisIndicatorQuoteCacheTest`: `PloutosApplicationTests`의 Testcontainers Redis로 JSON 왕복(오프셋 보존), TTL 만료, NX 락을 검증한다.
- **E2E**: 없다. HTTP API가 없는 모듈이다. `market-summary`의 E2E가 이 모듈을 함께 지난다.
- `ArchitectureTest`가 통과해야 한다. `KisNumbers` 이동 뒤에도 규칙 1(`external`은 도메인에 의존하지 않음)이 지켜진다.
- 테스트에서 실제 KIS를 호출하지 않는다.

## 경계

- **항상:** 등락률은 서버가 계산한다. 캐시 응답도 `valueAt`을 유지한다. 커밋 전 `./gradlew test`.
- **먼저 묻기:** TTL 기본값 변경, refresh-ahead(스케줄러) 도입, Redis 장애 시 폴백 규칙 변경, 만료 캐시 폴백 허용, `IndicatorQuote`에 필드 추가(카드·차트 계약에 영향), `Quote`와 계산 로직 공통화.
- **절대 안 함:** 현재값을 DB에 저장, KIS 원본 필드명을 노출, Redis에 Java 직렬화로 저장, TR ID를 `market-indicator`에 두기, 테스트에서 실제 KIS 호출.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 현재값과 전일 종가로 등락폭과 등락률(소수 둘째 자리)을 계산한다. | `IndicatorQuoteTest.현재값과_전일종가로_등락률을_소수_둘째자리로_계산한다` |
| 2 | 전일 종가가 0이면 등락률은 0이다. | `전일종가가_0이면_등락률은_0이다` |
| 3 | KIS 원값의 소수 자릿수를 보존한다 (환율 넷째 자리). | `환율은_소수_넷째자리까지_보존한다` |
| 4 | 국내 지수 응답을 매핑하고, 전일 종가는 현재값에서 전일 대비를 빼서 구한다. | `KisIndicatorQuoteProviderTest.국내_지수_응답을_매핑하고_전일종가를_역산한다` |
| 5 | 해외 지수는 시장구분 `N`, 환율은 `X`로 조회하고 `output1`을 매핑한다. | `해외_지수는_N으로_조회해_매핑한다`, `환율은_X로_조회해_매핑한다` |
| 6 | `valueAt`은 응답을 받은 시각이고 지표 타임존 오프셋을 가진다. | `기준_시각은_받은_시각이고_지표_타임존을_따른다` |
| 7 | 캐시가 있으면 KIS를 호출하지 않고 같은 값을 반환한다. | `IndicatorQuoteServiceTest.캐시가_있으면_외부를_호출하지_않고_같은_값을_반환한다` |
| 8 | 캐시 미스 시 락을 잡은 요청만 KIS를 호출하고 결과를 저장한다. | `캐시_미스_시_락을_잡은_요청만_외부를_호출하고_저장한다` |
| 9 | 락을 못 잡은 요청은 캐시가 채워지면 그 값을 반환한다. | `락을_못_잡은_요청은_캐시가_채워지면_그_값을_반환한다` |
| 10 | 락 대기가 끝나도 캐시가 비어 있으면 KIS를 호출하지 않고 `P007`을 던진다. | `락_대기가_끝나도_캐시가_비어_있으면_외부를_호출하지_않고_예외를_던진다` |
| 11 | Redis에 접근할 수 없으면 KIS를 호출하지 않고 `P007`을 던진다. | `캐시_저장소_장애면_외부를_호출하지_않고_예외를_던진다` |
| 12 | KIS가 실패하면 예외를 던지고 락을 해제한다. | `외부_호출이_실패하면_예외를_던지고_락을_해제한다` |
| 13 | 저장한 시세를 같은 값으로 읽고 오프셋이 유지된다. | `RedisIndicatorQuoteCacheTest.저장한_시세를_같은_값과_오프셋으로_읽는다` |
| 14 | TTL이 지나면 값이 사라진다. | `TTL이_지나면_값이_사라진다` |
| 15 | 같은 지표의 락은 한 요청만 잡는다. | `같은_지표의_락은_한_요청만_잡는다` |
| 16 | Redis에 접근할 수 없으면 `P007`을 던진다. | `Redis에_접근할_수_없으면_예외를_던진다` |

## 미해결 질문

- **해외 지수와 환율은 실시간인가, 지연인가?** KIS의 해외지수(`N`)와 환율(`X`) 시세가 지연 시세인지 확인해야 한다. 지연이라면 카드에 지연 여부를 표시할지 `market-summary`에서 정한다.
- **모의 도메인 호출 한도:** 캐시가 빈 상태에서 탭 하나를 열면 KIS를 3번 순차 호출한다. 실측에서 모의 도메인은 1초 간격 호출도 `EGW00201`에 걸렸다. 운영이 실전 도메인(초당 20건)인지는 기능 맵의 미해결 질문과 같다.
- **해외 TR의 기간 파라미터:** 평일에는 기간을 오늘 하루로 줘도 `output1`이 채워진다(실측). 해외 휴장일에도 그런지는 구현 중에 확인한다. 비어 있으면 기간을 최근 며칠로 넓힌다.
- **KIS 호출 한도 공유:** `stock-quote`의 `QuoteRefresher`와 한도를 나눠 쓴다. 이 모듈은 초당 0.5회가 상한이라 지금은 여유가 있다.

## 추후 구현

1. 유가: `OVERSEAS_FUTURES` 분기와 해외선물 응답 DTO를 추가한다 (`CAPABILITY-MAP-market.md` "유가를 넣을 때 할 일").
2. 응답 지연이 문제가 되면 refresh-ahead(5개 고정 지표를 주기적으로 갱신)를 도입한다. 도입하려면 먼저 물어야 한다.
