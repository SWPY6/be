# 명세: 종목 현재가·주요 지표 (stock-quote)

종목 상세 화면의 현재가(RQ-1001)와 주요 지표(RQ-1008)를 제공하는 API의 명세다. 종목당 KIS 현재가 API를 1회 호출한 결과를 TTL 동안 캐시해, 사용자 수와 무관하게 KIS 호출 수를 억제한다.

기능 맵: `CAPABILITY-MAP.md`. 의존 대상: `kis-client`, `stock-daily-price`.

## 내가 세운 전제

1. 종목 식별자는 `stockId`(Long) 경로 변수다. `Stocks.ticker`와 `Stocks.exchange`로 KIS 파라미터를 만든다. 통화·타임존은 `Markets`에서 안다.
2. 경로 접두어는 `/api/v1`이다. 기존 컨트롤러가 없어 이 명세가 첫 선례다.
3. 인증은 없다(현재 `SecurityConfig`는 permitAll).
4. 프론트는 이 API를 주기적으로 폴링한다. 서버는 푸시하지 않는다.
5. 캐시는 Redis에 둔다. 인스턴스가 늘어도 캐시가 공유되어 KIS 호출 수가 인스턴스 수에 비례하지 않는다. Redis가 죽으면 캐시 없이 KIS를 직접 호출한다(가용성 우선).
6. 국내·미국 모두 실시간 시세다. KIS 공식 문서 기준 **미국은 0분 지연 무료 실시간**이고 별도 신청이 필요 없다(15분 지연은 홍콩·베트남·중국·일본에 해당). 대상 시장이 국내와 미국뿐이므로 `priceTiming`은 항상 `REALTIME`이다.
7. 숫자는 JSON number, 시각은 오프셋을 포함한 ISO-8601 문자열이다. 금액 표기("86.6조원", "$185.70")는 프론트가 한다.

## 목표

화면 상단의 현재가·등락률·기준 시각·실시간 여부와 우측 지표 8종을 **한 번의 호출**로 준다. 모두 같은 KIS 응답에서 나오므로 "화면의 종목과 기준 시각에 맞는 데이터"(RQ-1008)가 보장된다. 사용자 N명이 같은 종목을 폴링해도 KIS 호출은 종목당 TTL마다 1회다.

## 규칙

### 값 계산

| 응답 필드 | 규칙 |
| --- | --- |
| `price` | KIS 현재가 그대로 |
| `change` | `price − previousClose` |
| `changeRate` | `(price − previousClose) / previousClose × 100`, 소수 둘째 자리 반올림(HALF_UP). KIS의 `prdy_ctrt`/`rate`를 쓰지 않고 서버가 계산한다 — 국내·해외 기준을 통일하고 테스트로 고정하기 위해서다. `previousClose`가 0이면 `0.00` |
| `priceAt` | 서버가 KIS 응답을 받은 시각. 시장 타임존 오프셋 포함. KIS 현재가 응답에는 시각 필드가 없다. 캐시에서 응답할 때도 갱신하지 않으므로 클라이언트는 데이터가 몇 초 전 것인지 안다 |
| `priceTiming` | `REALTIME` / `DELAYED`. 국내·미국 모두 `REALTIME`(전제 6). "실시간"은 제공자가 지연 시세를 주지 않는다는 뜻이고, 캐시 TTL만큼의 지연은 `priceAt`으로 드러난다. 15분 지연 시장(홍콩·중국·일본·베트남)을 지원하게 되면 그때 `DELAYED`를 쓴다 |
| `indicators.marketCap` | 국내 `hts_avls`는 **억원** 단위 → `× 100,000,000`으로 원 환산. 해외 `tomv`는 달러 그대로 |
| `indicators.tradingValue` | 국내 `acml_tr_pbmn`(원), 해외 `tamt`(달러) 그대로 |
| `indicators.volumeRatio20d` | `volume ÷ averageVolume20d`, 소수 둘째 자리 반올림. `stock-daily-price`의 `DailyPriceReader.averageVolume20d`가 비어 있으면 `null` |

### KIS 필드 대응

| 응답 필드 | 국내 `FHKST01010100` (`output`) | 해외 `HHDFS76200200` (`output`) |
| --- | --- | --- |
| `price` | `stck_prpr` | `last` |
| `indicators.previousClose` | `stck_sdpr` | `base` |
| `indicators.open` | `stck_oprc` | `open` |
| `indicators.high` | `stck_hgpr` | `high` |
| `indicators.low` | `stck_lwpr` | `low` |
| `indicators.volume` | `acml_vol` | `tvol` |
| `indicators.tradingValue` | `acml_tr_pbmn` | `tamt` |
| `indicators.marketCap` | `hts_avls` (억원 → 원) | `tomv` |

| `Stocks.exchange` | API | 파라미터 |
| --- | --- | --- |
| KRX | 국내 현재가 `FHKST01010100` | `FID_COND_MRKT_DIV_CODE=J`, `FID_INPUT_ISCD=ticker` |
| NASDAQ | 해외 현재가상세 `HHDFS76200200` | `AUTH=""`, `EXCD=NAS`, `SYMB=ticker` |
| NYSE | 해외 현재가상세 `HHDFS76200200` | `AUTH=""`, `EXCD=NYS`, `SYMB=ticker` |

### 캐시 (Redis)

- 종목별 `Quote`를 Redis에 TTL 동안 보관한다. 키 `quote:{stockId}`, 값은 `Quote`의 JSON(Jackson). `SET quote:{stockId} {json} EX {ttl}`. TTL은 `ploutos.quote.cache-ttl-seconds`, 기본 10초. Java 직렬화는 쓰지 않는다.
- cache-aside: 캐시 미스 또는 만료 시에만 KIS를 호출하고 결과를 저장한다.
- single-flight(best effort): 미스가 나면 `SET quote:lock:{stockId} 1 NX EX 3`으로 락을 시도한다. 락을 잡은 요청만 KIS를 호출·저장하고 락을 지운다. 못 잡은 요청은 최대 2초 동안 50ms 간격으로 캐시를 다시 읽고, 채워지면 그 값을 반환한다. 2초가 지나도 없으면 KIS를 직접 호출한다. 드물게 중복 호출을 허용한다 — 분산 락으로 완전히 보장하는 비용이 이득보다 크다.
- KIS 호출이 실패하면 만료된 캐시 값으로 폴백하지 않는다. `MARKET_DATA_UNAVAILABLE`을 그대로 던진다(오래된 가격을 현재가로 보이지 않기 위해).
- Redis에 접근할 수 없으면(연결 실패, 타임아웃) 캐시를 건너뛰고 KIS를 직접 호출해 응답한다. 오류는 로그로 남긴다. 장애가 길어지면 KIS 초당 20건 한도에 닿을 수 있다 — 알려진 위험.
- 효과: KIS 호출 상한 = 활성 종목 수 ÷ TTL. 활성 종목 200개, TTL 10초까지 초당 20건 한도 안이다. 인스턴스 수와 무관하다.

### 설정

| 프로퍼티 | 환경변수 | 설명 |
| --- | --- | --- |
| `spring.data.redis.host` | `REDIS_HOST` | Redis 호스트 (docker-compose에서는 `redis`) |
| `spring.data.redis.port` | `REDIS_PORT` | Redis 포트 (기본 6379) |
| `ploutos.quote.cache-ttl-seconds` | — | 캐시 TTL, 기본 10 |

- 의존성: `spring-boot-starter-data-redis` (Lettuce). `build.gradle`에 추가한다.
- `docker-compose.yml`에 `redis:7-alpine` 서비스를 추가하고 `app`이 이에 의존한다. `.env`에 `REDIS_HOST`, `REDIS_PORT`를 추가한다.
- Redis는 메모리 상한 128MB에 `allkeys-lru` 축출 정책으로 띄운다. 상한이 없으면 메모리가 무한정 늘어 서버가 위험해진다. 축출된 키는 캐시 미스와 같으므로 KIS를 한 번 더 호출할 뿐 기능은 정상이다. 스냅샷(RDB)은 끈다 — 재시작하면 비어도 되는 캐시다.
- 테스트는 `PloutosApplicationTests`가 MySQL과 같은 방식으로 `GenericContainer("redis:7-alpine")`를 띄우고 `@DynamicPropertySource`로 접속 정보를 주입한다. 별도 Testcontainers 모듈은 필요 없다.

### 노출 인터페이스

`stock-chart`가 당일 봉을 만들 때 이 캐시를 그대로 쓴다(추가 KIS 호출 없음).

```java
public interface QuoteReader {
    Quote read(Long stockId);
}
```

`Quote`는 값 객체다. 등락률·거래량 배수·단위 환산은 `Quote`의 메서드로 두어 스프링 없이 검증한다.

### 알려진 한계

**미국 종목의 당일 시가(`indicators.open`)는 장중에 부정확할 수 있다.** KIS 문서에 "미국의 경우 0분지연시세로 제공되나, 장중 당일 시가는 상이할 수 있으며, 익일 정정 표시됩니다"라고 명시돼 있다. 확정 일봉은 정정된 값을 받으므로 영향이 없고 당일 값만 해당된다. 보정하지 않고 받은 값을 그대로 전달한다.

### 오류

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| `stockId`가 정수가 아님 | 400 | `P001` |
| 종목 없음 | 404 | `P002` |
| KIS 실패(토큰·응답 코드·HTTP·타임아웃) | 502 | `P007` |

### 설계

- `QuoteProvider`(포트): `Quote fetch(StockWithMarket stock)`. 구현 `KisQuoteProvider`가 `Stocks.exchange`로 국내/해외 API를 고르고 필드를 매핑한다.
- `QuoteCache`(포트): `Optional<Quote> find(Long stockId)`, `void put(Long stockId, Quote quote)`, `boolean tryLock(Long stockId)`, `void unlock(Long stockId)`. 구현 `RedisQuoteCache`가 `StringRedisTemplate`로 키·TTL·NX 락을 다룬다. Redis 접근 실패는 `find`가 empty, `put`·`unlock`이 무시, `tryLock`이 `true`를 돌려주는 식으로 감싸 서비스가 Redis의 존재를 모르게 한다.
- `StockQuoteService`: 종목 조회(없으면 `STOCK_NOT_FOUND`) → 캐시 → 미스 시 락 → provider → 저장 → 락 해제. `QuoteReader` 구현체.
- `StockQuoteController`: `GET /api/v1/stocks/{stockId}/quote` → `ApiResponse<StockQuoteResponse>`. `averageVolume20d`를 `DailyPriceReader`에서 읽어 배수를 채운다.

## API 계약

프론트엔드에 전달하는 계약이다. 봉투 규칙은 `SPEC-api-response.md`를 따른다. 구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다.

### `GET /api/v1/stocks/{stockId}/quote`

요청: 경로 변수 `stockId`(정수). 쿼리 없음. 권장 폴링 주기 10초(캐시 TTL과 동일).

**200 성공**

```json
{
  "data": {
    "stockId": 1,
    "ticker": "005380",
    "name": "현대차",
    "currency": "KRW",
    "price": 248000,
    "change": 7783,
    "changeRate": 3.24,
    "priceAt": "2026-08-12T14:31:05+09:00",
    "priceTiming": "REALTIME",
    "indicators": {
      "previousClose": 240217,
      "open": 244280,
      "high": 251224,
      "low": 241056,
      "volume": 245000,
      "volumeRatio20d": 0.95,
      "marketCap": 86600000000000,
      "tradingValue": 60800000000
    }
  }
}
```

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `stockId` | integer | X | 종목 ID |
| `ticker` | string | X | 종목 코드 (`005380`, `AAPL`) |
| `name` | string | X | 종목명 |
| `currency` | `"KRW"` \| `"USD"` | X | 아래 모든 금액의 통화. 표기(원/달러, 조·억 축약)는 프론트 |
| `price` | number | X | 현재가 (RQ-1001) |
| `change` | number | X | 직전 정규장 종가 대비 등락폭. 음수 가능 |
| `changeRate` | number | X | 등락률 %, 소수 둘째 자리 (RQ-1001) |
| `priceAt` | string(ISO-8601, 오프셋 포함) | X | 가격 기준 시각 = 서버가 시세를 받은 시각. 캐시 응답이면 최대 TTL만큼 과거 (RQ-1001) |
| `priceTiming` | `"REALTIME"` \| `"DELAYED"` | X | 실시간·지연 여부 (RQ-1001) |
| `indicators.previousClose` | number | X | 전일 종가 (RQ-1008) |
| `indicators.open` | number | X | 당일 시가 |
| `indicators.high` | number | X | 당일 고가 |
| `indicators.low` | number | X | 당일 저가 |
| `indicators.volume` | integer | X | 당일 누적 거래량(주) |
| `indicators.volumeRatio20d` | number | O | 당일 거래량 ÷ 최근 20거래일 평균 거래량, 소수 둘째 자리. 거래일 20일 미만이면 `null` ("평소 거래량 대비 0.95배") |
| `indicators.marketCap` | number | X | 시가총액, `currency` 기본 단위(원·달러) |
| `indicators.tradingValue` | number | X | 당일 누적 거래대금, `currency` 기본 단위 |

**400 `stockId`가 정수가 아님** (`GET /api/v1/stocks/abc/quote`)

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

**502 시세 제공자 오류** (KIS 토큰 실패·응답 코드 오류·HTTP 오류·타임아웃)

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
Single test: ./gradlew test --tests '*QuoteTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/stock/quote/            → Quote, PriceTiming
src/main/java/com/swyp/ploutos/stock/quote/service/    → QuoteReader, QuoteProvider, QuoteCache, StockQuoteService
src/main/java/com/swyp/ploutos/stock/quote/kis/        → KisQuoteProvider, KIS 응답 DTO
src/main/java/com/swyp/ploutos/stock/quote/redis/      → RedisQuoteCache
src/main/java/com/swyp/ploutos/stock/quote/controller/ → StockQuoteController, StockQuoteResponse
src/main/resources/application.properties           → spring.data.redis.*, ploutos.quote.cache-ttl-seconds
build.gradle                                        → spring-boot-starter-data-redis
docker-compose.yml                                  → redis 서비스
src/test/java/com/swyp/ploutos/PloutosApplicationTests.java → redis 컨테이너 추가
src/test/java/com/swyp/ploutos/stock/quote/         → 단위·슬라이스 테스트
```

## 코드 스타일

- 응답 DTO는 `record`. `StockQuoteResponse`와 중첩 `Indicators`를 `sealed interface` 없이 record로 둔다(변형이 하나뿐이다).
- `Quote`는 불변 값 객체. 계산은 메서드로 노출하고 필드는 꺼내지 않는다.
- `else` 없이 guard clause. `@Getter`/`@Setter` 금지.
- 시각은 `Clock`으로 얻는다.

```java
// Quote
public BigDecimal changeRate() {
    if (previousClose.signum() == 0) {
        return BigDecimal.ZERO.setScale(2);
    }
    return price.subtract(previousClose)
        .divide(previousClose, 6, RoundingMode.HALF_UP)
        .movePointRight(2)
        .setScale(2, RoundingMode.HALF_UP);
}
```

## 테스트 전략

- JUnit 6, BDD, 한글 `조건_결과`.
- 단위(60%): `Quote`(등락률, 배수, 억원 환산), `StockQuoteService`(캐시 히트/미스, 락을 잡은 쪽만 호출, 락을 못 잡은 쪽은 채워진 값 반환, 캐시 장애 시 직접 호출 — 가짜 `QuoteCache`·`QuoteProvider`와 고정 `Clock`).
- 통합(30%): `@WebMvcTest(StockQuoteController)` + MockMvc로 JSON 본문·상태 코드. `KisQuoteProvider`는 `MockRestServiceServer`로 국내/해외 필드 매핑. `RedisQuoteCache`는 Testcontainers Redis로 TTL 만료·JSON 왕복·NX 락을 검증.
- E2E(10%): `@SpringBootTest`(MySQL·Redis 컨테이너)에서 KIS를 스텁하고 `GET /api/v1/stocks/{id}/quote` 전체 흐름 1건.

## 경계

- **항상:** 등락률은 서버가 계산한다. 캐시 응답도 `priceAt`을 유지한다. 커밋 전 `./gradlew test`. 응답 형식이 바뀌면 이 명세와 Swagger를 먼저 고친다.
- **먼저 묻기:** 응답 필드 추가·이름 변경(프론트 계약), TTL 기본값 변경, Redis 장애 시 폴백 규칙 변경(직접 호출 → 502 등), KIS 토큰 캐시를 Redis로 이전, 푸시(WebSocket/SSE) 도입, 만료 캐시 폴백 허용.
- **절대 안 함:** 현재가를 DB에 저장, KIS 원본 필드명을 응답에 노출, 만료된 캐시 값을 성공 응답으로 반환, Redis에 Java 직렬화로 저장, 테스트에서 실제 KIS 호출.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 현재가와 전일 종가로 등락률을 소수 둘째 자리로 계산한다. | `QuoteTest.현재가와_전일종가를_받으면_등락률을_소수_둘째자리로_계산한다` |
| 2 | 전일 종가와 같으면 등락률은 0이다. | `전일종가와_같으면_등락률은_0이다` |
| 3 | 국내 시가총액은 억원을 원으로 환산한다. | `국내_시가총액은_억원을_원으로_환산한다` |
| 4 | 당일 거래량을 20거래일 평균으로 나눠 배수를 소수 둘째 자리로 계산한다. | `당일_거래량을_20거래일_평균으로_나눠_배수를_계산한다` |
| 5 | 20거래일 평균이 없으면 배수는 `null`이다. | `평균_거래량이_없으면_배수는_null이다` |
| 6 | 가격 기준 시각은 KIS 응답을 받은 시각이다. | `StockQuoteServiceTest.가격_기준_시각은_시세를_받은_시각이다` |
| 7 | 캐시가 유효하면 KIS를 호출하지 않고 같은 값을 반환한다. | `캐시가_유효하면_외부를_호출하지_않고_같은_값을_반환한다` |
| 8 | 캐시가 만료되면 KIS를 다시 호출한다. | `캐시가_만료되면_외부를_다시_호출한다` |
| 9 | 캐시 미스 시 락을 잡은 요청만 KIS를 호출한다. | `캐시_미스_시_락을_잡은_요청만_외부를_호출한다` |
| 10 | 락을 못 잡은 요청은 캐시가 채워지면 그 값을 반환하고 KIS를 호출하지 않는다. | `락을_못_잡은_요청은_캐시가_채워지면_그_값을_반환한다` |
| 11 | 락을 못 잡은 요청이 2초를 기다려도 캐시가 비어 있으면 KIS를 직접 호출한다. | `락_대기가_끝나도_캐시가_비어_있으면_외부를_직접_호출한다` |
| 12 | Redis에 접근할 수 없으면 캐시를 건너뛰고 KIS를 직접 호출해 정상 응답한다. | `캐시_저장소_장애면_외부를_직접_호출한다` |
| 13 | KIS 실패 시 만료 캐시로 폴백하지 않고 예외를 던진다. | `외부_호출이_실패하면_만료된_캐시로_대신하지_않는다` |
| 14 | 국내·미국 종목 모두 `REALTIME`으로 표시한다(미국은 KIS 기준 0분 지연). | `국내_종목은_실시간으로_표시한다`, `미국_종목은_실시간으로_표시한다` |
| 15 | 응답에 주요 지표 8종이 모두 담긴다. | `StockQuoteControllerTest.주요_지표_8종을_한_응답에_담는다` |
| 16 | 없는 종목이면 404 / `P002`. | `없는_종목이면_404와_P002를_반환한다` |
| 17 | KIS 실패면 502 / `P007`. | `시세_조회에_실패하면_502와_P007을_반환한다` |
| 18 | 국내·해외 KIS 응답 필드가 `Quote`로 매핑된다. | `KisQuoteProviderTest.국내_응답을_시세로_매핑한다`, `해외_응답을_시세로_매핑한다` |
| 19 | 저장한 시세를 같은 값으로 읽는다(JSON 왕복). | `RedisQuoteCacheTest.저장한_시세를_같은_값으로_읽는다` |
| 20 | TTL이 지나면 캐시 값이 사라진다. | `TTL이_지나면_값이_사라진다` |
| 21 | 같은 종목의 락은 한 요청만 잡을 수 있고 3초 뒤 자동 해제된다. | `같은_종목의_락은_한_요청만_잡는다`, `락은_3초_뒤_자동_해제된다` |

## 미해결 질문

- 캐시 TTL 10초 vs 20초. 기본 10초로 시작해 운영하며 조정한다.
- 개장 직후 KIS가 `low`를 0으로 주는 등 이상값이 있는지 — 구현 중 실측 후 규칙화한다.

## 추후 구현

1. 푸시(WebSocket/SSE)가 필요해지면 캐시 갱신 시점에 브로드캐스트한다.
2. 인스턴스가 2대 이상이 되면 `kis-client`의 토큰 캐시도 Redis로 옮겨 토큰 발급 1분 1회 제한을 안전하게 지킨다.
