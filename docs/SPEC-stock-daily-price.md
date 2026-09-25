# 명세: 종목 일봉 저장소 (stock-daily-price)

종목의 거래일별 시가·고가·저가·종가·거래량(일봉)을 저장하고, 없는 구간을 KIS에서 받아 채우며, 최근 20거래일 평균 거래량을 계산하는 모듈의 명세다. HTTP API는 노출하지 않는다 — `stock-quote`와 `stock-chart`가 인터페이스로 읽는다.

기능 맵: `CAPABILITY-MAP.md`. 의존 대상: `kis-client`.

## 내가 세운 전제

1. 저장소는 MySQL이다. 시계열 DB는 도입하지 않는다. 종목당 연 250행 규모라 `(stock_id, trade_at)` 유니크 인덱스로 충분하다.
2. 저장 대상은 **확정된 거래일의 일봉**뿐이다. 당일 진행 중 봉은 저장하지 않는다.
3. 수정주가를 쓴다 (국내 `FID_ORG_ADJ_PRC=0`, 해외 `MODP=1`).
4. 스케줄러는 없다. 동기화는 조회 요청이 들어올 때 필요한 만큼만 한다(read-through).
5. "오늘"과 거래일은 시장 현지일 기준이다 — 국내 KST, 미국 America/New_York.
6. KIS 국내/해외 API 선택과 거래소 코드는 `Stocks.exchange`(KRX·NASDAQ·NYSE)로 정한다. `Markets.code`는 시장·지수 소속이라 거래소를 말해 주지 않는다(S&P500 구성 종목은 NASDAQ 또는 NYSE 상장). 시장 타임존은 `Markets.country`로 안다.

## 목표

차트와 지표가 필요로 하는 일봉을 한 곳에서 관리해, 같은 종목·같은 구간에 대한 KIS 호출이 반복되지 않게 한다. 20거래일 평균 거래량의 정의를 한 곳에 두어 `stock-quote`(배수)와 `stock-chart`(기준선)가 같은 값을 쓰게 한다.

## 규칙

### 엔티티

`StockDailyPrices` (`stock_daily_prices`). 엔티티명 복수형, 필드명 단수형, `@Column` 명시, FK는 Long 필드.

| 필드 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `stockDailyPriceId` | Long | PK, IDENTITY | |
| `stockId` | Long | not null | FK: `Stocks.stockId` (연관관계 매핑 없음) |
| `tradeAt` | LocalDate | not null | 거래일. `(stockId, tradeAt)` 유니크 |
| `openPrice` | BigDecimal(20,4) | not null | 시가 |
| `highPrice` | BigDecimal(20,4) | not null | 고가 |
| `lowPrice` | BigDecimal(20,4) | not null | 저가 |
| `closePrice` | BigDecimal(20,4) | not null | 종가 |
| `volume` | Long | not null | 거래량(주) |

정밀도는 기존 `MarketDailyPrices.closeValue`와 같다. 거래대금은 저장하지 않는다(당일 거래대금은 `stock-quote`가 현재가 API에서 받는다).

### 노출 인터페이스

```java
public interface DailyPriceReader {
    DailyPrices findBetween(Long stockId, LocalDate from, LocalDate to);
    Optional<Long> averageVolume20d(Long stockId);
}
```

두 메서드 모두 호출 시 필요하면 먼저 동기화한다. 호출자는 동기화의 존재를 모른다.

### 20거래일 평균 거래량

- 저장된 마지막 거래일부터 거슬러 최근 20거래일의 `volume` 단순 평균, 소수점 버림.
- 저장된 거래일이 20일 미만이면 `Optional.empty()`.
- 하나의 스칼라다. 이동평균 시계열이 아니다(화면의 점선 기준선 1개).
- 계산은 일급 컬렉션 `DailyPrices`의 메서드로 두어 스프링·DB 없이 검증한다.

### read-through 동기화

요청 구간 `[from, to]`에 대해 DB가 구간을 **덮으면** KIS를 호출하지 않는다. 덮는다는 것은 다음 둘을 모두 만족한다는 뜻이다.

- (a) `min(tradeAt) ≤ from` 이거나 `Stocks.listedAt > from` (상장 전 구간은 존재하지 않는다)
- (b) `max(tradeAt) ≥ 마지막 거래일 추정치`. 추정치 = 시장 현지 오늘 − 1일, 토·일이면 직전 금요일.

덮지 못하면 KIS에서 받아 저장한다.

- 받는 구간은 `from − 30일`부터 어제까지다. 20거래일 평균을 위해 `from` 앞에 여유를 둔다.
- 100건씩 페이지를 반복한다. 국내는 `FID_INPUT_DATE_1/2`를 100거래일 단위로 뒤로 옮기고, 해외는 `BYMD`를 받은 마지막 행의 전날로 옮긴다. 응답이 비면 멈춘다.
- `(stockId, tradeAt)`이 이미 있으면 건너뛴다. 갱신하지 않는다.
- **당일 행은 저장하지 않는다.** KIS 일봉 응답의 첫 행이 오늘이면 버린다.
- 공휴일에는 (b)가 거짓이 되어 한 번 더 호출하지만 새 행이 없어 무해하다. 같은 종목에 대한 동기화 시도는 **하루 1회**로 제한한다(메모리에 `stockId → 마지막 시도일` 기록).
- KIS 호출이 실패하면 `MARKET_DATA_UNAVAILABLE`이 그대로 위로 올라간다. 부분 저장된 행은 남긴다(다음 요청에서 이어서 채운다).

### KIS 파라미터 매핑

| `Stocks.exchange` | API | 파라미터 |
| --- | --- | --- |
| KRX | 국내 일봉 `FHKST03010100` | `FID_COND_MRKT_DIV_CODE=J`, `FID_INPUT_ISCD=ticker` |
| NASDAQ | 해외 일봉 `HHDFS76240000` | `EXCD=NAS`, `SYMB=ticker` |
| NYSE | 해외 일봉 `HHDFS76240000` | `EXCD=NYS`, `SYMB=ticker` |

응답 필드 대응

| 저장 필드 | 국내 (`output2[]`) | 해외 (`output2[]`) |
| --- | --- | --- |
| `tradeAt` | `stck_bsop_date` | `xymd` |
| `openPrice` | `stck_oprc` | `open` |
| `highPrice` | `stck_hgpr` | `high` |
| `lowPrice` | `stck_lwpr` | `low` |
| `closePrice` | `stck_clpr` | `clos` |
| `volume` | `acml_vol` | `tvol` |

### 설계

- `DailyPriceProvider`(포트): `List<DailyPrice> fetch(StockWithMarket stock, LocalDate from, LocalDate to)`. 구현 `KisDailyPriceProvider`가 `Stocks.exchange`로 국내/해외 API를 고르고 페이지를 반복한다.
- `StockDailyPriceRepository`(JPA): 구간 조회, 최근 N건 조회, `(stockId, tradeAt)` 존재 확인.
- `StockDailyPriceSyncPolicy`: "덮는다" 판정과 하루 1회 제한. `Clock`을 주입받는다.
- `DailyPriceReader` 구현이 위 셋을 조합한다.

## 명령어

```
Build: ./gradlew build
Test: ./gradlew test
Single test: ./gradlew test --tests '*DailyPricesTest'
Compile: ./gradlew compileJava
Run: ./gradlew bootRun
```

## 프로젝트 구조

```
src/main/java/com/swyp/ploutos/stock/price/            → StockDailyPrices, DailyPrice, DailyPrices, StoredRange, StockDailyPriceSyncPolicy
src/main/java/com/swyp/ploutos/stock/price/repository/ → StockDailyPriceRepository
src/main/java/com/swyp/ploutos/stock/price/service/    → DailyPriceReader, DailyPriceProvider, StockDailyPriceService
src/main/java/com/swyp/ploutos/stock/price/kis/        → KisDailyPriceProvider, KIS 응답 DTO
src/test/java/com/swyp/ploutos/stock/price/**          → 대상과 같은 패키지에 테스트
```

## 코드 스타일

- 엔티티는 JPA용 `protected` 기본 생성자와 `public` 전체 생성자를 둔다. 정적 팩토리는 생성 경로가 둘 이상이거나 생성 규칙이 있을 때만 도입한다. Setter 없음. 값은 의도가 드러나는 메서드로만 노출한다.
- `else` 없이 guard clause.
- 날짜 계산은 `Clock`과 시장 타임존으로 한다. `LocalDate.now()`를 직접 부르지 않는다.

```java
// 20거래일 평균 (DailyPrices)
public Optional<Long> averageVolumeOfLast(int days) {
    if (prices.size() < days) {
        return Optional.empty();
    }
    long sum = prices.subList(prices.size() - days, prices.size()).stream()
        .mapToLong(DailyPrice::volume)
        .sum();
    return Optional.of(sum / days);
}
```

## 테스트 전략

- JUnit 6, BDD, 한글 `조건_결과`.
- 단위(60%): `DailyPrices`(20일 평균, 정렬), `StockDailyPriceSyncPolicy`(덮음 판정, 주말 처리, 하루 1회) — POJO.
- 통합(30%): `@DataJpaTest` + Testcontainers로 유니크 제약·구간 조회. `DailyPriceReader` 구현은 가짜 `DailyPriceProvider`로 read-through 흐름을 검증. `KisDailyPriceProvider`는 `MockRestServiceServer`로 페이지 반복과 필드 매핑을 검증.
- E2E 없음. HTTP를 노출하지 않는다.

## 경계

- **항상:** 저장 전 당일 행 제거. 유니크 충돌은 건너뛴다. 커밋 전 `./gradlew test`.
- **먼저 묻기:** 스키마 변경(컬럼 추가, `Stocks`에 거래소 코드 추가), 스케줄러 도입, 저장 정책 변경(당일 봉 저장, 갱신 허용), 수정주가 → 원주가 전환.
- **절대 안 함:** 기존 행 덮어쓰기, 테스트에서 실제 KIS 호출, 동기화 실패를 삼키고 빈 결과 반환.

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | 저장된 봉이 구간을 덮지 못하면 KIS에서 받아 저장한다. | `DailyPriceReaderTest.저장된_봉이_구간을_덮지_못하면_외부에서_받아_저장한다` |
| 2 | 저장된 봉이 구간을 덮으면 KIS를 호출하지 않는다. | `저장된_봉이_구간을_덮으면_외부를_호출하지_않는다` |
| 3 | 상장일이 `from`보다 늦으면 그 앞 구간은 덮은 것으로 본다. | `StockDailyPriceSyncPolicyTest.상장일이_시작일보다_늦으면_덮은_것으로_본다` |
| 4 | 토·일에는 직전 금요일을 마지막 거래일로 본다. | `주말이면_직전_금요일을_마지막_거래일로_본다` |
| 5 | 같은 종목의 동기화는 하루 1회만 시도한다. | `같은_종목의_동기화는_하루_한_번만_시도한다` |
| 6 | 당일 행은 저장하지 않는다. | `DailyPriceReaderTest.당일_봉은_저장하지_않는다` |
| 7 | 이미 있는 거래일은 다시 저장하지 않는다. | `이미_있는_거래일은_다시_저장하지_않는다` |
| 8 | 최근 20거래일 거래량의 평균을 버림으로 반환한다. | `DailyPricesTest.최근_20거래일_거래량의_평균을_반환한다` |
| 9 | 거래일이 20일 미만이면 평균은 없다. | `거래일이_20일_미만이면_평균은_없다` |
| 10 | 100건을 넘는 구간은 페이지를 반복해 모두 받는다. | `KisDailyPriceProviderTest.백건을_넘으면_페이지를_반복해_모두_받는다` |
| 11 | 국내·해외 응답 필드가 저장 필드로 매핑된다. | `국내_응답을_일봉으로_매핑한다`, `해외_응답을_일봉으로_매핑한다` |
| 12 | `(stockId, tradeAt)`는 유니크다. | `StockDailyPriceRepositoryTest.같은_종목_같은_거래일은_중복_저장할_수_없다` |
| 13 | KIS 실패는 `MARKET_DATA_UNAVAILABLE`로 전파되고 부분 저장은 남는다. | `DailyPriceReaderTest.외부_호출이_실패하면_예외를_전파한다` |

## 미해결 질문

- 액면분할·감자 뒤 과거 수정주가가 바뀌면 저장된 값이 낡는다. 재동기화 정책(해당 종목 삭제 후 재적재)은 필요해질 때 정한다.
