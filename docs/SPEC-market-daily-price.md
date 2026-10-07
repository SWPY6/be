# 명세: 시장 지표 일봉 저장소 (market-daily-price)

시장 지표 5종의 확정 일봉(시가·고가·저가·종가)을 저장하는 모듈이다. 요청 구간을 DB가 덮지 못하면 KIS에서 받아 채운다(read-through).
HTTP API가 없다. `market-chart`가 인터페이스로 읽는다.

기능 맵: `CAPABILITY-MAP-market.md`. 의존 대상: `market-indicator`, `kis-client`. 값 타입은 `stock-daily-price`의 루트(`DailyPrice`, `DailyPrices`)를 재사용한다.

## 내가 세운 전제

1. 저장소는 MySQL이다. 지표당 연 250행 규모라 `(indicator, trade_at)` 유니크 인덱스로 충분하다.
2. **기존 `market_daily_prices` 테이블을 고쳐서 쓴다** (사용자 결정).
   - 기존 엔티티 `MarketDailyPrices`는 `marketId`, `closeValue`, `changeRate`만 있고 쓰는 곳이 없다.
   - `marketId`를 `indicator`로 바꾸고, 시가·고가·저가를 추가하고, `changeRate`를 뺀다.
3. **값 타입은 `stock.price.DailyPrice`/`DailyPrices`를 재사용하고, `volume`은 0으로 채운다** (사용자 결정).
   - `market-chart`가 기존 봉 집계(`Chart.of`)를 거의 그대로 쓸 수 있게 하려는 것이다.
   - 다른 모듈 루트에 의존하는 것은 CLAUDE.md 패키지 규칙상 허용된다.
   - 거래량은 DB에 저장하지 않는다. 기능 맵의 "거래량 제외" 결정 때문이다.
4. 저장 대상은 **확정된 거래일의 일봉**뿐이다. 당일 진행 중인 봉은 저장하지 않는다 (`stock-daily-price`와 같음).
5. 스케줄러는 없다. 조회 요청이 들어올 때 필요한 만큼만 동기화한다.
6. "오늘"은 지표 타임존(`MarketIndicator.zoneId()`) 기준이다.

## 목표

차트가 필요로 하는 지표 일봉을 한 곳에서 관리해, 같은 지표·같은 구간에 대한 KIS 호출이 반복되지 않게 한다. 지표는 5개로 고정이고 모든 사용자가 같은 차트를 보므로, 한 번 채운 구간은 모든 요청이 공유한다.

성공 기준은 **"요청 구간을 DB가 덮으면 KIS를 부르지 않고, 덮지 못하면 빈 쪽만 받아 저장한 뒤 거래일 오름차순으로 돌려준다"**다.

## 규칙

### 엔티티

`MarketDailyPrices` (`market_daily_prices`)를 고친다. 엔티티명은 기존 이름을 유지한다.

| 필드 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `marketDailyPriceId` | Long | PK, IDENTITY | 기존 유지 |
| `tradeAt` | LocalDate | not null | 거래일. 기존 유지. `(indicator, tradeAt)` 유니크 |
| `openValue` | BigDecimal(20,4) | not null | 시가. **신규** |
| `highValue` | BigDecimal(20,4) | not null | 고가. **신규** |
| `lowValue` | BigDecimal(20,4) | not null | 저가. **신규** |
| `closeValue` | BigDecimal(20,4) | not null | 종가. 기존 유지 |
| `indicator` | `MarketIndicator` | not null, `VARCHAR(20)` | 지표. **`marketId`를 대체** |
| ~~`marketId`~~ | | | **삭제.** 환율에는 `Markets` 행이 없다 |
| ~~`changeRate`~~ | | | **삭제.** 파생값이다. 등락률은 `market-quote`가 계산하고, 일봉은 `stock-daily-price`처럼 원값만 저장한다 |

- 필드 순서는 CLAUDE.md의 엔티티 규칙(숫자 → Enum)을 따른다.
- 정밀도 (20,4)는 환율의 소수 넷째 자리(`1359.9000`)를 그대로 담는다.
- **`indicator`는 MySQL 네이티브 `ENUM`이 아니라 `VARCHAR(20)`로 매핑한다.**
  - Hibernate 6.2+는 `@Enumerated(STRING)`을 MySQL 네이티브 `ENUM`으로 만든다 (`SPEC-industry.md`).
  - 그러면 지표(유가)를 추가할 때 `ALTER TABLE`이 필요해진다.
  - `@JdbcTypeCode(SqlTypes.VARCHAR)`를 함께 붙여 막는다. 이렇게 하면 기능 맵의 "유가는 스키마 변경 없이 행만 늘어난다" 대비가 지켜진다.
  - **테스트 스키마에는 `check` 제약이 함께 붙는다.** Hibernate가 `varchar`로 만들면서 enum 값 목록을 `check (indicator in ('KOSPI',…))`로 덧붙인다(구현 중 생성된 DDL로 확인).
    - 테스트는 엔티티에서 스키마를 만들므로 지표를 추가하면 제약도 따라 바뀐다.
    - **운영 SQL(`db/alter-market-daily-prices.sql`)에는 이 제약을 넣지 않는다.** 넣으면 지표를 추가할 때 제약을 고쳐야 해서 위 대비가 깨진다.
    - 그래서 운영과 테스트 스키마가 이 제약 하나만큼 다르다. 앱은 enum 값만 저장하므로 동작 차이는 없다.

```java
@Entity
@Table(
        name = "market_daily_prices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"indicator", "trade_at"})
)
public class MarketDailyPrices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long marketDailyPriceId;

    @Column(nullable = false)
    private LocalDate tradeAt;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal openValue;

    // highValue, lowValue, closeValue 같은 형태

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MarketIndicator indicator;

    protected MarketDailyPrices() {
    }

    public MarketDailyPrices(MarketIndicator indicator, DailyPrice price) { ... }

    /** 지표에는 거래량이 없으므로 volume은 0이다. */
    public DailyPrice toDailyPrice() {
        return new DailyPrice(tradeAt, openValue, highValue, lowValue, closeValue, 0L);
    }
}
```

### 운영 DB 변경

운영은 `ddl-auto=none`이라 Hibernate가 스키마를 바꾸지 않는다. `db/alter-market-daily-prices.sql`을 저장소에 두고 운영 DB에 직접 실행한다. 테스트는 `ddl-auto=create`라 엔티티에서 새로 만들어진다.

```sql
-- 전제: market_daily_prices가 비어 있다 (쓰는 코드가 없었다). 실행 전 확인한다.
SELECT COUNT(*) FROM market_daily_prices;

ALTER TABLE market_daily_prices
    DROP COLUMN market_id,
    DROP COLUMN change_rate,
    ADD COLUMN indicator VARCHAR(20) NOT NULL,
    ADD COLUMN open_value DECIMAL(20,4) NOT NULL,
    ADD COLUMN high_value DECIMAL(20,4) NOT NULL,
    ADD COLUMN low_value DECIMAL(20,4) NOT NULL,
    ADD CONSTRAINT uk_market_daily_prices_indicator_trade_at UNIQUE (indicator, trade_at);
```

- 행이 있으면 `NOT NULL` 컬럼 추가가 실패한다. 그때는 실행을 멈추고 데이터의 출처를 먼저 확인한다.
- **운영 DB 확인 결과 (2026-09-30):** 테이블이 있고 행은 0건이다.
  - 컬럼: `change_rate decimal(10,4)`, `close_value decimal(20,4)`, `trade_at date`, `market_daily_price_id bigint AUTO_INCREMENT PK`, `market_id bigint`. 모두 `NOT NULL`이다.
  - 유니크 제약은 없다.
  - 위 `ALTER`를 그대로 쓴다. 적용 직전에 `SELECT COUNT(*)`를 한 번 더 확인한다.

### 노출 인터페이스

```java
public interface IndicatorDailyPriceReader {

    /** [from, to] 구간의 확정 일봉을 거래일 오름차순으로. 저장된 봉이 부족하면 먼저 KIS에서 채운다. volume은 0이다. */
    DailyPrices findBetween(MarketIndicator indicator, LocalDate from, LocalDate to);
}
```

- 호출자는 동기화의 존재를 모른다.
- 20거래일 평균 거래량(`averageVolume20d`)은 두지 않는다. 지표에는 거래량이 없다.

### read-through 동기화

요청 구간 `[from, to]`를 DB가 **덮으면** KIS를 부르지 않는다. 덮는다는 것은 다음 둘을 모두 만족한다는 뜻이다.

- (a) `min(tradeAt) ≤ from`
- (b) `max(tradeAt) ≥ 마지막 거래일 추정치`. 추정치는 지표 타임존의 오늘 − 1일이고, 토·일이면 직전 금요일이다.

`stock-daily-price`와의 차이:
- 상장일 조건이 없다. 지표는 상장일이 없고, 5년(차트 최대 구간)보다 오래됐다.
- 20거래일 평균을 위한 30일 여유를 두지 않는다. 받는 구간은 비어 있는 쪽만이다.
  - 앞이 비었으면 `from`부터 어제까지 받는다.
  - 앞은 있고 끝만 낡았으면 `max(tradeAt)`부터 어제까지 받는다. 겹치는 하루는 중복 저장하지 않고 건너뛴다.

저장 규칙:
- `(indicator, tradeAt)`이 이미 있으면 건너뛴다. 갱신하지 않는다.
- **당일 행은 저장하지 않는다.** 받은 행 중 지표 타임존의 오늘 날짜인 행을 버린다.
  - 장중에 지수·환율 일봉이 진행 중인 날의 행을 주는지는 아직 확인하지 못했다. 실측이 한국 시간 새벽(장 시작 전)이었다 (미해결 질문).
  - 주식 일봉 API는 진행 중인 날의 행을 준다. 그래서 같은 규칙을 적용한다. 주지 않는다면 이 규칙은 아무것도 버리지 않으므로 무해하다.
- KIS가 실패하면 `MARKET_DATA_UNAVAILABLE`이 그대로 위로 올라간다. 이미 저장된 행은 남긴다. 다음 요청에서 이어서 채운다.

동기화 시도 제한 (`stock-daily-price`와 같은 규칙):
- 같은 지표는 하루 1회만 시도한다. 메모리에 `indicator → (마지막 시도일, 그날 시도한 가장 이른 from)`을 기록한다.
- 단 그날 시도한 것보다 이른 `from`을 요구하면 한 번 더 허용한다.
- 공휴일에는 (b)가 거짓이 되어 하루 한 번 헛호출하지만 새 행이 없어 무해하다.
- **시도 기록은 원자적으로 한다** (`ConcurrentHashMap.compute`).
  - 지표는 5개뿐이라 첫 요청들이 동시에 몰리기 쉽다.
  - 두 요청이 동시에 동기화하면 같은 행을 저장하다가 유니크 제약에 걸린다.
  - 시도권을 얻지 못한 요청은 기다리지 않고, 그 시점에 DB에 있는 행만 돌려준다.

환율에서 관찰한 것 (모의 도메인, 2026-09-30 한국 시간 새벽 실측):
- 한국 추석(9/24·25)과 미국 노동절(9/7)이 모두 행으로 있다. **휴일 없는 평일 시계열**이라 (b)의 "토·일만 빼는" 추정이 정확히 맞는다.
- 최신 행이 9/28이었다. 전날(9/29) 행이 새벽 5시에 아직 없었다.
  - 늦게라도 들어온다면 (b)가 그 전까지 거짓이 되어 하루 1회 헛호출이 생긴다. 제한 규칙 때문에 비용은 하루 1회다.
  - 언제 들어오는지는 미해결 질문이다.

### KIS 파라미터와 페이지

| `IndicatorKind` | API | 파라미터 | 한 번에 오는 최대 건수 (실측, 참고용) |
| --- | --- | --- | --- |
| `DOMESTIC_INDEX` | `GET /uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice`, `FHKUP03500100` | `FID_COND_MRKT_DIV_CODE=U`, `FID_INPUT_ISCD={symbol}`, `FID_INPUT_DATE_1={from}`, `FID_INPUT_DATE_2={end}`, `FID_PERIOD_DIV_CODE=D` | **50** |
| `OVERSEAS_INDEX` | `GET /uapi/overseas-price/v1/quotations/inquire-daily-chartprice`, `FHKST03030100` | `FID_COND_MRKT_DIV_CODE=N`, 나머지는 위와 같은 이름 | **100** |
| `EXCHANGE_RATE` | 위와 같음 | `FID_COND_MRKT_DIV_CODE=X` | **100** |

- 두 API 모두 `output2`가 최신순으로 온다. 구간을 거슬러 올라가며 반복한다.
  1. `end`를 `to`로 시작한다.
  2. 받은 페이지에서 `[from, end]` 안의 행만 남긴다. 남은 행이 없으면 멈춘다.
  3. 남은 행의 가장 오래된 거래일이 `from` 이하이면 멈춘다. 아니면 그 거래일 − 1일로 `end`를 옮긴다.
- **받은 건수로 멈추지 않는다.** 아래 실측처럼 해외 지수·환율 API는 최대 건수보다 적게 주면서 시작 쪽 행을 빼먹는다. 건수로 멈추면 빠진 앞부분을 다시 받지 않아 차트의 시작이 며칠 늦다.
- `end`는 매번 줄어든다. 2에서 구간 밖 행을 먼저 빼므로, 요청보다 늦은 행만 오는 응답에도 같은 요청을 되풀이하지 않는다.
- 비용: 시작일이 휴장일이면 빈 응답을 확인하는 호출이 1회 늘어난다. 끝만 채우는 동기화는 저장된 마지막 거래일부터 요청하므로 늘지 않는다.
- 결과는 `[from, to]` 밖의 행을 버린다.
- 5년 구간을 처음 채우면 국내 지수는 약 25회, 해외 지수·환율은 약 13회 이상 호출한다. 이후에는 끝만 채우므로 하루 1회다.
- 날짜(`stck_bsop_date`)가 빈 행은 버린다. KIS는 데이터가 없을 때 필드가 빈 객체를 주기도 한다.

**시작 쪽 행 누락 (2026-10-07 실측, 모의 도메인, 나스닥 `COMP`, 시작일 8/7 고정)**

| 요청 구간 | 받은 행 | 가장 오래된 행 | 빠진 거래일 |
| --- | --- | --- | --- |
| 8/7~8/31 | 16 | 8/10 | 8/7 |
| 8/7~9/15 | 27 | 8/7 | 없음 |
| 8/7~9/30 | 36 | 8/11 | 8/7, 8/10 |
| 8/7~10/6 | 39 | 8/12 | 8/7, 8/10, 8/11 |
| 환율 `FX@KRW` 8/7~10/6 | 39 | 8/13 | 8/7, 8/10~8/12 |

- 빠진 날의 데이터는 있다. 8/1~8/14로 부르면 8/3부터 다 온다.
- 빠지는 양이 구간 길이와 비례하지 않는다. 그래서 구간을 짧게 쪼개는 방식으로는 막을 수 없고, 종료 조건을 바꿨다.
- 국내 지수와 주식 일봉(`KisDailyPriceProvider`)에서는 관찰되지 않았다. 같은 날 기본 구간으로 받은 국내 33종목·미국 31종목의 첫 거래일이 모두 같았다.

| 저장 필드 | `DOMESTIC_INDEX` (`output2[]`) | `OVERSEAS_INDEX`, `EXCHANGE_RATE` (`output2[]`) |
| --- | --- | --- |
| `tradeAt` | `stck_bsop_date` | `stck_bsop_date` |
| `openValue` | `bstp_nmix_oprc` | `ovrs_nmix_oprc` |
| `highValue` | `bstp_nmix_hgpr` | `ovrs_nmix_hgpr` |
| `lowValue` | `bstp_nmix_lwpr` | `ovrs_nmix_lwpr` |
| `closeValue` | `bstp_nmix_prpr` | `ovrs_nmix_prpr` |

- 확정된 행에서는 `prpr`(현재값)이 그날 종가다.
- 수정주가 파라미터가 없다. 지수와 환율에는 수정 개념이 없다.
- 어댑터는 `IndicatorKind`를 `switch`로 분기하고 `default`를 두지 않는다 (`SPEC-market-quote.md`와 같음).

### 설계

- `IndicatorDailyPriceProvider`(포트): `List<DailyPrice> fetch(MarketIndicator indicator, LocalDate from, LocalDate to)`.
  - 구현 `KisIndicatorDailyPriceProvider`가 종류별 API, 페이지 반복, 필드 매핑을 담당한다.
- `MarketDailyPriceRepository`(JPA): 구간 조회, 가장 이른·늦은 거래일, 구간 안의 거래일 목록. `StockDailyPriceRepository`와 같은 쿼리를 `indicator`로 둔다.
- `IndicatorDailyPriceSyncPolicy`: "덮는다" 판정, 마지막 거래일 추정, 하루 1회 제한. `Clock`을 주입받는다.
  - `StockDailyPriceSyncPolicy`와 로직이 비슷하지만 공통화하지 않는다.
  - 그쪽은 `StockWithMarket`(상장일)과 `stockId`에 묶여 있다. 일반화하면 두 모듈의 정책이 한 클래스에 얽힌다.
  - 공통 부분은 주말 계산 5줄이다.
- `MarketDailyPriceService`: `IndicatorDailyPriceReader` 구현체. `@Transactional`. 정책, 리포지토리, provider를 조합한다.
- 기존 `StoredRange`(`stock.price` 루트)를 재사용한다. 종목 전용 개념이 없는 값 객체다.

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*IndicatorDailyPrice*'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/market/MarketDailyPrices.java                → 삭제 (아래로 이동)
src/main/java/com/swyp/ploutos/market/price/MarketDailyPrices.java          → 엔티티 수정·이동
src/main/java/com/swyp/ploutos/market/price/IndicatorDailyPriceSyncPolicy.java
src/main/java/com/swyp/ploutos/market/price/repository/MarketDailyPriceRepository.java
src/main/java/com/swyp/ploutos/market/price/service/                        → IndicatorDailyPriceReader, IndicatorDailyPriceProvider,
                                                                              MarketDailyPriceService
src/main/java/com/swyp/ploutos/market/price/kis/                            → KisIndicatorDailyPriceProvider, KIS 응답 DTO
db/alter-market-daily-prices.sql                                            → 운영 DB 변경 (신규)
src/test/java/com/swyp/ploutos/market/price/**                              → 대상과 같은 패키지
```

- 엔티티를 `market/price`로 옮긴다. `stock/price/StockDailyPrices`와 같은 위치 규칙이다(기능 모듈의 루트). 리포지토리는 자기 모듈(`market.price`)에서만 쓴다 (`ArchitectureTest` 규칙 3).
- `IndicatorDailyPriceReader`, `IndicatorDailyPriceProvider`, 엔티티, 정책만 `public`이다. 서비스, 어댑터, KIS 응답 DTO는 package-private다.

## 코드 스타일

- 엔티티는 `protected` 기본 생성자와 `public` 생성자를 둔다. Setter는 없다.
- 날짜는 `Clock`과 `MarketIndicator.zoneId()`로 계산한다. `LocalDate.now()`를 직접 부르지 않는다.
- `else` 없이 guard clause를 쓴다. 파일 끝에 개행을 넣는다.

```java
// IndicatorDailyPriceSyncPolicy: 원자적으로 시도권을 얻는다
public boolean tryStartSync(MarketIndicator indicator, LocalDate from, LocalDate today) {
    AtomicBoolean granted = new AtomicBoolean(false);
    lastAttempts.compute(indicator, (key, previous) -> {
        if (previous != null && previous.covers(from, today)) {
            return previous;
        }
        granted.set(true);
        return new Attempt(today, from);
    });
    return granted.get();
}
```

## 테스트 전략

- JUnit 6, BDD(`// given` `// when` `// then`), 테스트명은 한글 `조건_결과`.
- **단위 (60%)**
  - `IndicatorDailyPriceSyncPolicyTest`: 덮음 판정, 주말 처리, 하루 1회, 이른 시작일 재허용, 지표 타임존 기준 오늘.
  - `MarketDailyPricesTest`: `toDailyPrice`의 volume 0.
- **통합 (30%)**
  - `MarketDailyPriceRepositoryTest`(`@DataJpaTest` + Testcontainers): 유니크 제약, 구간 조회, `indicator` 컬럼이 `varchar`인지 (`information_schema.columns`로 확인).
  - `MarketDailyPriceServiceTest`: 가짜 `IndicatorDailyPriceProvider`로 read-through 흐름을 검증한다. 덮으면 호출하지 않음, 빈 쪽만 받음, 당일 행 제외, 중복 건너뜀, 실패 전파.
  - `KisIndicatorDailyPriceProviderTest`(가짜 `KisApiClient`): 종류별 파라미터, 시작일까지 이어 받는 페이지 반복, 구간 밖 행만 올 때 멈춤, 필드 매핑, 날짜가 빈 행 제거. 응답 본문은 실측 응답을 쓴다.
- E2E 없음. HTTP API가 없다.
- `ArchitectureTest`가 통과해야 한다. 새 규칙은 추가하지 않는다.
- 테스트에서 실제 KIS를 호출하지 않는다.

## 경계

- **항상:** 저장 전 당일 행을 뺀다. 유니크 충돌 행은 건너뛴다. 엔티티를 바꾸면 `db/` SQL도 함께 고친다. 커밋 전 `./gradlew test`.
- **먼저 묻기:** 스키마 추가 변경(거래량 컬럼 등), 스케줄러 도입, 저장 정책 변경(당일 봉 저장, 기존 행 갱신), 운영 DB에 SQL 실행.
- **절대 안 함:** 기존 행 덮어쓰기, `indicator`를 네이티브 `ENUM` 컬럼으로 두기, 테스트에서 실제 KIS 호출, 동기화 실패를 삼키고 빈 결과 반환.

## 성공 기준

> **구현 상태 (2026-10-02):** 태스크 10까지 끝나 #14~#16만 테스트로 증명된다.
> #1~#13이 가리키는 `MarketDailyPriceService`·`IndicatorDailyPriceSyncPolicy`·`KisIndicatorDailyPriceProvider`는
> 아직 만들지 않았다(태스크 11~13). 표만 보고 완료로 읽지 않도록 적어 둔다.


| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 저장된 봉이 구간을 덮지 못하면 KIS에서 받아 저장하고 오름차순으로 돌려준다. | `MarketDailyPriceServiceTest.저장된_봉이_구간을_덮지_못하면_외부에서_받아_저장한다` |
| 2 | 저장된 봉이 구간을 덮으면 KIS를 호출하지 않는다. | `저장된_봉이_구간을_덮으면_외부를_호출하지_않는다` |
| 3 | 앞이 비었으면 `from`부터, 끝만 낡았으면 저장된 마지막 거래일부터 받는다. | `앞이_비었으면_요청_시작일부터_받는다`, `끝만_낡았으면_저장된_마지막_거래일부터_받는다` |
| 4 | 당일 행은 저장하지 않는다. | `당일_봉은_저장하지_않는다` |
| 5 | 이미 있는 거래일은 다시 저장하지 않는다. | `이미_있는_거래일은_다시_저장하지_않는다` |
| 6 | KIS 실패는 `MARKET_DATA_UNAVAILABLE`로 전파된다. | `외부_호출이_실패하면_예외를_전파한다` |
| 7 | 토·일에는 직전 금요일을 마지막 거래일로 본다. | `IndicatorDailyPriceSyncPolicyTest.주말이면_직전_금요일을_마지막_거래일로_본다` |
| 8 | 오늘은 지표 타임존으로 계산한다. | `오늘은_지표_타임존으로_계산한다` |
| 9 | 같은 지표의 동기화는 하루 1회, 더 이른 시작일이면 한 번 더 허용한다. | `같은_지표의_동기화는_하루_한_번만_시도한다`, `같은_날_더_이른_시작일을_요청하면_다시_동기화한다` |
| 10 | 동시에 시도해도 시도권은 하나만 얻는다. | `동시에_시도해도_한_요청만_시도권을_얻는다` |
| 11 | 페이지가 요청 시작일까지 닿지 않으면 남은 구간을 이어서 받는다. 받은 건수로 멈추지 않는다. 요청 구간보다 늦은 행만 오면 멈춘다. | `KisIndicatorDailyPriceProviderTest.첫_페이지가_시작일까지_닿지_않으면_남은_구간을_이어서_받는다`, `요청_구간보다_늦은_행만_오면_더_부르지_않는다` |
| 12 | 종류별 응답 필드가 일봉으로 매핑된다 (종가는 `prpr`). | `국내_지수_응답을_일봉으로_매핑한다`, `해외_지수_응답을_일봉으로_매핑한다`, `환율_응답을_일봉으로_매핑한다` |
| 13 | 날짜가 빈 행은 버린다. | `날짜가_빈_행은_버린다` |
| 14 | `(indicator, tradeAt)`는 유니크다. | `MarketDailyPriceRepositoryTest.같은_지표_같은_거래일은_중복_저장할_수_없다` |
| 15 | `indicator` 컬럼은 `varchar`다. | `지표_컬럼은_varchar다` |
| 16 | 엔티티를 일봉으로 바꾸면 거래량은 0이다. | `MarketDailyPricesTest.일봉으로_바꾸면_거래량은_0이다` |

## 미해결 질문

- **장중 일봉 동작과 환율 전날 행:** 일봉 실측은 한국 시간 9/30 새벽이었다.
  - 그때 지수 최신 행은 9/29(전 거래일)였고, 환율은 9/28이었다.
  - 확인할 것은 두 가지다. 장중에 지수가 진행 중인 날의 행을 주는가? 환율 전날 행은 언제 들어오는가?
  - 한국 장중(평일 09:00~15:30 KST)에 다시 실측한다. 절차는 `SPEC-market-chart.md`의 미해결 질문에 적어 두었다.
    명세가 참조했던 `kis-probe-delay.sh`는 저장소에 없다(커밋된 적 없음).
  - 저장소에는 하루 1회 헛호출만 생긴다.
  - 차트에는 확정 봉과 진행 중인 봉 사이에 하루 공백이 생길 수 있다. 이 처리는 `SPEC-market-chart.md`에서 정한다.
- **호출 한도 공유:** 5년 구간을 처음 채우면 국내 지수는 약 25회를 연속 호출한다.
  - 실측에서는 3초 간격도 `EGW00201`에 걸렸다. AWS에 떠 있는 운영 앱이 같은 앱키로 `QuoteRefresher`를 돌리고 있었기 때문으로 본다 (사용자 확인).
  - 앱키 하나의 한도를 운영 앱, 로컬 실행, 실측 스크립트가 함께 쓴다. 로컬·실측용 앱키를 따로 두는 것을 검토한다.
  - 운영 도메인이 실전(초당 20건)인지 모의인지는 기능 맵의 미해결 질문과 같다.

## 추후 구현

1. 유가: `OVERSEAS_FUTURES` 분기, 해외선물 일봉 API, 월물 교체 규칙 (`CAPABILITY-MAP-market.md` "유가를 넣을 때 할 일").
2. 과거 구간 재동기화가 필요해지면(KIS가 과거 값을 정정한 경우 등) 지표 단위 삭제 후 재적재 정책을 정한다.
