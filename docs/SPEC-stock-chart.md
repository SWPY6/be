# 명세: 종목 차트 (stock-chart)

종목 상세 화면의 가격 차트와 거래량 차트(RQ-1002 ~ RQ-1007)에 필요한 데이터를 제공하는 API의 명세다. 저장된 확정 일봉을 요청한 봉 단위로 집계하고, 당일 진행 중 봉을 붙인 뒤, 평균 거래량 기준선을 함께 준다.

기능 맵: `CAPABILITY-MAP.md`. 의존 대상: `stock-daily-price`, `stock-quote`.

## 내가 세운 전제

1. 종목 식별자는 `stockId`(Long) 경로 변수, 경로 접두어는 `/api/v1`, 인증 없음 (`SPEC-stock-quote.md`와 같다).
2. 조회 구간(`from`·`to`)과 봉 단위(`interval`)는 **직교하는 별개의 축**이다. 하나로 합칠 수 없다 — "1개월 구간을 월봉으로"는 봉이 1개라서 차트가 성립하지 않는다.
3. **확대·축소는 재요청을 일으킨다.** 줌 아웃하면 프론트가 `from`을 뒤로 밀어 다시 부르고, 줌 인하면 `interval`을 좁혀 다시 부른다. 라인/캔들 전환(RQ-1003), 마우스 오버 표시(RQ-1004), 범례(RQ-1007)는 여전히 프론트 렌더링 책임이며 재요청이 없다 — 응답에 종가와 OHLC가 모두 있기 때문이다.
4. **상위 봉(주·월·분기·연봉)은 저장된 일봉을 우리가 집계해서 만든다.** KIS가 주봉·월봉 API를 제공하지만 쓰지 않는다. 봉 단위별 저장 스키마와 동기화 정책이 새로 필요하고 외부 호출이 봉 단위 배수로 늘기 때문이다.
5. 당일 진행 중 봉은 `stock-quote`의 현재가 캐시로 만든다. 추가 KIS 호출은 없다.
6. "오늘"과 거래일은 시장 현지일 기준이다 — 국내 KST, 미국 America/New_York.

## 목표

구간과 봉 단위를 지정하면 그 구간의 봉별 시가·고가·저가·종가·거래량과 평균 거래량 기준선이 한 응답으로 온다. 장중에는 마지막 봉이 현재가를 반영하는 진행 중 봉이다.

## 규칙

### 구간과 봉 단위

| 파라미터 | 타입 | 생략 시 | 설명 |
| --- | --- | --- | --- |
| `from` | `LocalDate` (ISO `yyyy-MM-dd`) | `to` − 2개월 | 조회 시작일 |
| `to` | `LocalDate` (ISO `yyyy-MM-dd`) | 시장 현지 오늘 | 조회 종료일 |
| `interval` | `1D` \| `1W` \| `1M` \| `3M` \| `1Y` | `1D` | 봉 단위 — 일·주·월·분기·연 |

- 확정 봉은 `stock-daily-price`의 `DailyPriceReader.findBetween(stockId, from, to)`로 읽는다. 거래일만 포함하고 오름차순이다.
- `to`를 어제가 아니라 오늘로 두는 이유: 생략했을 때 오늘이 구간 안에 들어와야 진행 중인 봉이 붙는다(아래 조건 3). 어제로 자르면 장중에도 진행 중인 봉이 사라지고, 조건 4도 발동할 수 없다.
- **요청한 `to`를 오늘로 깎지는 않는다.** 과거 구간에 오늘 봉이 끼어드는 것은 구간을 바꿔서가 아니라 진행 중인 봉을 막아서 해결한다(조건 3). 요청 구간을 말없이 바꾸면 응답 구간이 요청과 어긋난다 — "불완전 버킷도 그대로 포함한다"와 같은 이유다.
- **구간 길이 상한은 5년이다.** 넘으면 400 `P001`. 저장된 일봉이 없으면 KIS를 100건씩 페이징해 채우므로(`KisDailyPriceProvider`), 상한이 없으면 한 요청이 수십 회의 외부 호출로 번진다. 5년이면 최악 일봉 약 1250개 ≈ 13회다.
- **기본 구간이 2개월인 이유**는 첫 조회의 KIS 페이징을 1회로 묶기 위해서다. KIS 호출은 1건당 5초에서 끊기므로(`KisClientConfig.READ_TIMEOUT`), 호출이 늘수록 한 번은 걸릴 확률이 커진다. 1년이면 3회, 2개월이면 여유 30일을 더해도 거래일 약 62개라 한 페이지로 끝난다. 더 긴 구간이 필요하면 프론트가 `from`을 명시한다.
- **봉이 1~2개가 되는 조합은 막지 않는다.** 어떤 조합을 화면에 노출할지는 프론트 책임이다. 서버가 조합표를 들면 프론트가 바뀔 때마다 서버를 고쳐야 한다.

### 집계

`interval`이 `1D`가 아니면 확정 일봉을 버킷으로 묶어 봉 하나로 접는다.

- 버킷 경계: 주 = ISO 주(월~일), 월 = 달력 월, 분기 = 달력 분기, 연 = 달력 연.
- 버킷의 `tradeAt` = 그 버킷에 **실제로 포함된 첫 거래일**. 버킷의 달력상 시작일이 아니다(휴장일일 수 있다).
- `open` = 첫 봉의 시가, `high` = 최댓값, `low` = 최솟값, `close` = 마지막 봉의 종가, `volume` = 합계.
- **불완전 버킷도 그대로 포함한다.** `from`이 달 중간이면 첫 월봉은 며칠짜리다. 잘라내지 않는다 — 잘라내면 사용자가 요청한 구간과 응답 구간이 말없이 어긋난다.
- 집계는 `DailyPrices`/`DailyPrice`만 입력으로 받는 순수 계산이다. 리포지토리나 `~Reader`를 참조하지 않는다(`ArchitectureTest`의 도메인→계층 금지 규칙).

### 당일 진행 중 봉

- `QuoteReader.read(stockId)`로 얻은 `Quote`에서 `open`, `high`, `low`, `close = price`, `volume = 당일 누적 거래량`을 구성한다.
- **당일 봉은 마지막 버킷에 합쳐진다.** `interval=1M`이고 오늘이 9월 29일이면 9월 월봉의 `high`/`low`/`volume`에 당일 값이 반영되고 `close`가 현재가가 된다. 그 버킷은 `closed: false`다.
- 구현은 `Quote`를 당일자 `DailyPrice`로 한 번 변환해 일봉 목록 끝에 붙인 뒤 통째로 집계한다. 그래야 "마지막 버킷에 합치기"가 특수 분기가 되지 않고 집계 경로가 하나로 유지된다.
- 당일 봉은 저장하지 않는다. 캐시 TTL 안에서는 `/quote` 응답과 같은 값이 나온다.

**다음 중 하나라도 해당하면 붙이지 않는다.** 그때 마지막 봉은 `closed: true`이고 `asOf`는 `null`이다.

| # | 조건 | 이유 |
| --- | --- | --- |
| 1 | 시가가 0이다 | 장 시작 전. KIS가 개장 전 시가를 0으로 준다 |
| 2 | **오늘 봉으로 만든 시가·고가·저가·종가·거래량이 구간 안 마지막 확정 봉과 모두 같다** | 시세가 직전 거래일의 반복이다. 아래 설명 참고 |
| 3 | 오늘이 요청 구간 안에 없다 | 과거·미래 구간을 조회할 때 오늘 봉이 끼어들지 않게 한다 |
| 4 | 오늘 날짜의 확정 봉이 이미 있다 | 장 마감 후 동기화가 끝난 뒤. `Chart`가 처리한다 |

**조건 2·3이 필요한 이유 (2026-10-05 관측)**
- `?from=2026-09-28&to=2026-10-02`를 요청했는데 국내 종목은 `2026-10-05`, 미국 종목은 `2026-10-04` 봉이 `closed: false`로 돌아왔다.
- 요청 구간이 10/2에서 끝나는데 오늘 봉이 끼어든 것을 막는 것이 조건 3이다. 미국 종목이 10/4인 것은 `today`가 시장 현지 날짜여서 한국 낮에는 뉴욕이 아직 전날이기 때문이다.
- 10/4는 뉴욕의 일요일이고 10/5 18시는 국내 장 마감 후다. **둘 다 시가가 0이 아니라서 조건 1로는 걸러지지 않는다.** 휴장일·마감 후에 KIS가 직전 거래일의 값을 그대로 주기 때문이다.
- 그대로 붙이면 거래가 없던 날짜에 직전 거래일과 같은 가짜 봉이 생긴다. 오늘 봉 값이 마지막 확정 봉과 모두 같으면 걸러진다(조건 2). 2026-10-07 04:35 실측에서 국내 34종목 전부 이 상태였다(예: 현대건설 10/6 확정 봉 `115100 / 116600 / 112500 / 113100 / 578,555`와 현재가 다섯 값이 똑같았다).
- 날짜를 쓰지 않고 값으로 판단하므로 국내·미국에 같은 규칙이 통한다.
- **비교는 `BigDecimal.compareTo`로 한다.** 저장 정밀도는 소수 넷째 자리(`StockDailyPrices`)이고 KIS 현재가는 정수로 오므로, `equals`를 쓰면 `115100.0000`과 `115100`이 달라 걸러지지 않는다.
- **지표 차트처럼 "전일 종가 = 마지막 확정 봉 종가"로 판정하지 않는 이유.** 처음에는 그렇게 넣었다. 2026-10-07 15:44(국내 장 마감 직후) 모의 도메인에서 국내 35종목 중 30종목의 현재가 기준가(`stck_sdpr`)가 저장된 10/6 종가와 달라(현대건설 112,900 대 113,100, 차이 ±10~2,000원, 방향 제각각) 정상적인 오늘 봉이 5종목에만 붙었다. 값이 직전 봉의 반복인지만 보면 이 어긋남에 영향받지 않는다. 바꾼 뒤 같은 조건에서 35종목 전부 붙었다.
- **미국 종목의 당일 봉 `open`은 장중에 부정확할 수 있다.** KIS가 "장중 당일 시가는 상이할 수 있으며 익일 정정"이라고 명시한다. 확정 일봉은 정정된 값이므로 영향이 없다. 보정하지 않고 받은 값을 그대로 쓴다.

### 평균 거래량 기준선

- `averageVolume`은 **응답에 담긴 확정 봉 중 마지막 최대 20개의 단순 평균**이다. 거래량 차트에 수평 점선으로 그리는 기준선 1개(스칼라)다.
- 봉 단위를 따라간다. `interval=1D`면 최근 20거래일 평균, `1M`이면 최근 20개월 평균이다. 기준선과 막대의 스케일이 항상 일치한다.
- 확정 봉이 20개 미만이면 **있는 만큼** 평균한다. 줌 인해도 기준선이 사라지지 않는 편이 낫다.
- 확정 봉이 하나도 없으면 `null`.
- 진행 중 봉은 제외한다. 미완성 거래량이 기준선을 끌어내리기 때문이다.
- **응답 구간 안에서만 계산한다.** 구간 밖의 일봉을 따로 더 받아오지 않는다. `interval=1Y`로 20봉을 채우려면 20년치 일봉이 필요한데 구간 상한이 5년이라 애초에 불가능하고, 기준선 하나를 위해 상한을 넘기는 건 앞뒤가 맞지 않는다.

### 응답 구간

- `from`·`to`는 실제 포함된 첫·마지막 **봉의** `tradeAt`이다. 요청한 `from`·`to`와 다를 수 있다(요청 구간의 양끝이 휴장일이면).
- `asOf`는 당일 봉의 기준 시각(`Quote.priceAt`)이다. 당일 봉이 없으면 `null`. 당일 봉이 붙지 않는 조건은 위 "당일 진행 중 봉"의 1~4다.
- 봉이 하나도 없으면(신규 상장 직후 등) `candles: []`, `from`·`to`·`asOf`·`averageVolume`은 `null`.

### 알려진 한계

- 장 시작 전 KIS가 직전 거래일 값 중 **일부만** 바꿔 주면(예: 거래량만 정정) 조건 2로 걸러지지 않는다. 2026-10-07 실측에서는 다섯 값이 모두 같았다.
- 구간에 확정 봉이 없어도 오늘이 구간 안이면 진행 중인 봉이 붙는다. `?from=오늘&to=오늘`은 장중이면 오늘 봉 하나가 온다. 시장 지표 차트는 비교할 종가가 없어 빈 응답이다(`SPEC-market-chart.md`).

### 오류

| 상황 | HTTP | 코드 |
| --- | --- | --- |
| `interval`이 허용되지 않은 값 | 400 | `P001` |
| `from`·`to`가 `yyyy-MM-dd`로 파싱되지 않음 | 400 | `P001` |
| `from`이 `to`보다 뒤 | 400 | `P001` |
| 구간 길이가 5년 초과 | 400 | `P001` |
| `stockId`가 정수가 아님 | 400 | `P001` |
| 종목 없음 | 404 | `P002` |
| 동기화·현재가 조회 중 KIS 실패 | 502 | `P007` |

`ErrorCode`에 날짜 구간 전용 코드가 없으므로 전부 `INVALID_INPUT_VALUE`(`P001`)로 모은다. 날짜 파싱 실패는 `GlobalExceptionHandler`가 `MethodArgumentTypeMismatchException`을 이미 `P001`로 번역한다.

### 설계

- `ChartInterval`(enum): `DAY("1D")` … `YEAR("1Y")`. `from(String code)`로 요청 문자열을 받고(기본값 `DAY`, 미허용 값이면 `INVALID_INPUT_VALUE`), `bucketStart(LocalDate)`로 그룹핑 키를 낸다.
- `ChartRange`(값 객체): `of(from, to, today)`가 기본값 채우기와 검증(역전·상한)을 한다. `contains(LocalDate)`가 그 날짜가 구간 안인지 답한다(양끝 포함). `to`만 보지 않는 이유는 `of`가 미래 구간도 허용하기 때문이다.
- `DailyPrices.repeatsLast(DailyPrice)`(`stock-daily-price`): 주어진 봉이 마지막 확정 봉과 거래일만 다르고 값이 모두 같은가(조건 2). 값 비교는 `DailyPrice.sameValuesAs`가 `compareTo`로 한다. 비어 있으면 `false`다.
- `DailyPrices.lastCloseIs(BigDecimal)`(`stock-daily-price`): 마지막 확정 봉의 종가와 같은 값인가. 시장 지표 차트의 조건 2가 쓴다(`SPEC-market-chart.md`).
- `Chart`(값 객체): 확정 봉 목록 + `Optional<LiveCandle>` + `ChartInterval`을 받아 `candles`, `from`, `to`, `asOf`, `averageVolume`을 만든다. 당일 봉 결합과 집계 규칙을 여기 둔다.
  - `LiveCandle`(값 객체): 당일자 일봉과 그 값의 기준 시각. 시세 출처를 담지 않아 종목 현재가와 시장 지표 현재값이 같은 타입으로 들어온다 (`SPEC-market-chart.md`).
  - **"진행 중 봉이 있는가"는 `Chart`가 아니라 호출자가 판단한다.** 조건 1~3은 `StockChartService.cannotAttach`가 보고 `Optional.empty()`를 넘긴다. 시가 0이 개장 전을 뜻하는 것이나 직전 거래일 시세가 반복되는 것은 KIS 응답의 성질이고 봉 집계 규칙이 아니다.
  - `Chart`가 직접 하는 판정은 하나다(조건 4): 그 거래일이 이미 확정 봉으로 있으면 붙이지 않는다.
- `ChartCandle`: `of(List<DailyPrice> bucket, boolean closed)`가 버킷 하나를 봉 하나로 접는다.
- `StockChartService`: `interval` 검증 → 종목 조회(없으면 `STOCK_NOT_FOUND`) → `ChartRange` → `DailyPriceReader` → `QuoteReader` → `Chart`. **`interval` 검증이 종목 조회보다 앞선다** — 잘못된 요청에 DB를 건드리지 않는다.
- `StockChartController`: `GET /api/v1/stocks/{stockId}/chart` → `ApiResult<StockChartResponse>`.

## API 계약

프론트엔드에 전달하는 계약이다. 봉투 규칙은 `SPEC-api-response.md`를 따른다. 구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다.

### `GET /api/v1/stocks/{stockId}/chart`

요청: 경로 변수 `stockId`(정수). 쿼리 `from`·`to`(ISO 날짜), `interval`(`1D`|`1W`|`1M`|`3M`|`1Y`). 전부 선택이며 기본값은 위 표와 같다.

화면 동작과의 대응:

| 사용자 동작 | 프론트가 보내는 것 |
| --- | --- |
| 첫 진입(일봉) | `?` — 기본 구간 2개월 → 일봉 약 40개 |
| 첫 진입(월봉) | `?from=2025-09-30&interval=1M` — 월봉은 기본 구간으로 3개뿐이라 `from`을 명시한다 |
| 줌 아웃 | `from`을 뒤로 민다 — `?from=2021-09-29&interval=1M` |
| 줌 인 | `interval`을 좁히고 구간을 줄인다 — `?from=2026-09-01&interval=1D` |
| 라인↔캔들 전환 | **재요청 없음** (응답에 종가와 OHLC가 다 있다) |
| 장중 갱신 | 같은 요청을 `/quote`와 같은 주기로 폴링 (캐시 공유라 KIS 호출이 늘지 않는다) |

**200 성공** — 월봉, 장중 (`?from=2025-09-30&interval=1M`, 오늘 2026-09-29)

```json
{
  "data": {
    "stockId": 1,
    "interval": "1M",
    "currency": "KRW",
    "from": "2025-09-30",
    "to": "2026-09-01",
    "asOf": "2026-09-29T14:31:05+09:00",
    "averageVolume": 14820000,
    "candles": [
      { "tradeAt": "2025-09-30", "open": 238000, "high": 241500, "low": 237200, "close": 240100, "volume": 12760000, "closed": true },
      { "tradeAt": "2025-10-02", "open": 240500, "high": 262300, "low": 239800, "close": 258900, "volume": 18430000, "closed": true },
      { "tradeAt": "2026-09-01", "open": 122100, "high": 142000, "low": 112300, "close": 117700, "volume": 15982000, "closed": false }
    ]
  }
}
```

마지막 봉의 `tradeAt`이 `2026-09-01`(9월 첫 거래일)이고 `closed`가 `false`다. 9월 한 달이 진행 중이며 `close`는 현재가, `volume`은 9월 1일부터 오늘까지의 누계다. `to`는 마지막 **봉**의 거래일이므로 오늘이 아니라 `2026-09-01`이다.

**200 성공** — 일봉, 과거 구간 (`?from=2026-09-21&to=2026-09-25&interval=1D`, 오늘 2026-09-29)

```json
{
  "data": {
    "stockId": 1,
    "interval": "1D",
    "currency": "KRW",
    "from": "2026-09-21",
    "to": "2026-09-25",
    "asOf": null,
    "averageVolume": 703252,
    "candles": [
      { "tradeAt": "2026-09-21", "open": 126700, "high": 128100, "low": 123500, "close": 125600, "volume": 387162, "closed": true },
      { "tradeAt": "2026-09-22", "open": 130600, "high": 136100, "low": 125700, "close": 131300, "volume": 1023754, "closed": true },
      { "tradeAt": "2026-09-23", "open": 130000, "high": 130000, "low": 120200, "close": 121100, "volume": 1201523, "closed": true },
      { "tradeAt": "2026-09-25", "open": 121900, "high": 126800, "low": 118900, "close": 124600, "volume": 200570, "closed": true }
    ]
  }
}
```

요청 구간이 오늘(9/29)보다 앞에서 끝나므로 진행 중인 봉을 붙이지 않는다(조건 3). 그래서 `asOf`가 `null`이고 마지막 봉도 `closed: true`다. 확정 봉이 4개뿐이라 `averageVolume`은 20개가 아닌 4개의 평균이다.

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `stockId` | integer | X | 종목 ID |
| `interval` | `"1D"` \| `"1W"` \| `"1M"` \| `"3M"` \| `"1Y"` | X | 적용된 봉 단위. 생략 요청이면 `"1D"` — 선택 상태 표시용 |
| `currency` | `"KRW"` \| `"USD"` | X | 가격 통화 |
| `from` | string(date) | O | 첫 봉의 거래일. 봉이 없으면 `null` |
| `to` | string(date) | O | 마지막 봉의 거래일. 봉이 없으면 `null` |
| `asOf` | string(ISO-8601, 오프셋 포함) | O | 진행 중 봉의 기준 시각. 진행 중 봉이 없으면 `null` |
| `averageVolume` | integer | O | 확정 봉 중 마지막 최대 20개의 평균 거래량 — 거래량 차트 기준선 1개 (RQ-1006·1007). 확정 봉이 없으면 `null` |
| `candles[]` | array | X | 거래일 오름차순. 빈 배열 가능 |
| `candles[].tradeAt` | string(date) | X | 봉에 포함된 첫 거래일. `interval=1D`면 그 거래일 |
| `candles[].open` | number | X | 시가 — 봉의 첫 거래일 시가 |
| `candles[].high` | number | X | 고가 — 봉 구간의 최댓값 |
| `candles[].low` | number | X | 저가 — 봉 구간의 최솟값 |
| `candles[].close` | number | X | 종가 — 봉의 마지막 거래일 종가. 진행 중 봉은 현재가. 라인 차트는 이 값만 쓴다 (RQ-1003·1004) |
| `candles[].volume` | integer | X | 거래량(주) — 봉 구간의 합계 (RQ-1006) |
| `candles[].closed` | boolean | X | `true` 확정 봉, `false` 진행 중 봉(폴링하면 값이 바뀐다) |

**400 잘못된 봉 단위** (`?interval=2W`)

```json
{
  "error": {
    "name": "InvalidInputValueException",
    "code": "P001",
    "message": "잘못된 입력값입니다."
  }
}
```

구간 상한 초과(`?from=2015-01-01`), 역전(`?from=2026-09-29&to=2026-09-01`), 날짜 형식 오류(`?from=notadate`)도 같은 응답이다.

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
src/main/java/com/swyp/ploutos/stock/chart/            → ChartInterval, ChartRange, Chart, ChartCandle
src/main/java/com/swyp/ploutos/stock/chart/service/    → StockChartService, StockChartDetail
src/main/java/com/swyp/ploutos/stock/chart/controller/ → StockChartController, StockChartResponse
src/test/java/com/swyp/ploutos/stock/chart/**          → 대상과 같은 패키지에 테스트
```

## 코드 스타일

- 응답 DTO는 `record`. `ChartCandle`은 확정 봉과 진행 중 봉이 같은 형태이므로 `closed` 필드 하나로 구분한다.
- `ChartInterval`은 요청 문자열 → enum 변환과 버킷 경계 계산을 스스로 한다. 허용되지 않은 값은 `INVALID_INPUT_VALUE`.
- `ChartRange`는 기본값과 검증을 스스로 한다. 컨트롤러·서비스는 구간 규칙을 알지 않는다.
- `else` 없이 guard clause. `@Getter`/`@Setter` 금지.
- 오늘 날짜는 `Clock`과 시장 타임존으로 구한다.

```java
// ChartInterval — 버킷 경계
LocalDate bucketStart(LocalDate tradeAt) {
    return switch (this) {
        case DAY -> tradeAt;
        case WEEK -> tradeAt.with(DayOfWeek.MONDAY);
        case MONTH -> tradeAt.withDayOfMonth(1);
        case QUARTER -> tradeAt.withDayOfMonth(1).withMonth(firstMonthOfQuarter(tradeAt));
        case YEAR -> tradeAt.withDayOfYear(1);
    };
}
```

## 테스트 전략

- JUnit 6, BDD, 한글 `조건_결과`.
- 단위(60%): `ChartInterval`(문자열 변환, 기본값, 버킷 경계), `ChartRange`(기본값, 역전, 상한 경계), `Chart`(집계, 당일 봉 결합, `from`/`to`/`asOf`/`averageVolume` 계산, 빈 봉 처리) — POJO. `StockChartService`는 가짜 `DailyPriceReader`·`QuoteReader`로.
- 통합(30%): `@WebMvcTest(StockChartController)` + MockMvc로 JSON 본문·상태 코드·기본값·400.
- E2E(10%): `@SpringBootTest`에서 KIS를 스텁하고 일봉 1건 + 월봉 집계 1건.

## 경계

- **항상:** 확정 봉은 `DailyPriceReader`로만 읽는다(직접 리포지토리 접근 금지). 진행 중 봉은 `QuoteReader`로만 만든다. 집계는 `stock.chart`의 순수 값 객체에서만 한다. 커밋 전 `./gradlew test`. 응답 형식이 바뀌면 이 명세와 Swagger를 먼저 고친다.
- **먼저 묻기:** 봉 단위 추가(분봉 등), 구간 상한 변경, 응답 필드 추가·이름 변경(프론트 계약), 이동평균 시계열 제공.
- **절대 안 함:** 차트 API에서 KIS를 직접 호출, KIS 주봉·월봉 API 사용(전제 4), 당일 봉 저장, 확정 봉과 진행 중 봉을 다른 배열로 분리(프론트 계약 위반).

## 성공 기준

| # | 인수 기준 | 검증 테스트 |
| --- | --- | --- |
| 1 | `from`·`to`를 생략하면 최근 2개월을 조회한다. | `ChartRangeTest.구간을_생략하면_오늘까지_최근_2개월이다`, `StockChartServiceTest.구간을_생략하면_오늘까지_최근_2개월을_조회한다` |
| 2 | `interval`을 생략하면 일봉으로 조회한다. | `ChartIntervalTest.봉_단위를_생략하면_일봉이다` |
| 3 | 구간을 지정하면 해당 구간의 거래일 봉만 오름차순으로 반환한다. | `ChartTest.구간을_지정하면_해당_구간의_거래일_봉만_오름차순으로_반환한다` (RQ-1002) |
| 4 | 봉마다 시가·고가·저가·종가·거래량이 있다. | `ChartTest.봉마다_시가_고가_저가_종가_거래량을_포함한다` (RQ-1003·1004·1006) |
| 5 | 월봉이면 같은 달의 일봉이 봉 하나로 묶인다. | `ChartTest.월봉이면_같은_달_봉을_하나로_묶는다` |
| 6 | 묶인 봉의 OHLCV는 첫 시가·최댓값·최솟값·마지막 종가·합계다. | `ChartTest.묶인_봉의_시가는_첫_봉_고가는_최댓값_저가는_최솟값_종가는_마지막_봉이다`, `ChartTest.묶인_봉의_거래량은_합계다` |
| 7 | 주봉은 월요일을 기준으로 묶는다. | `ChartTest.주봉은_월요일을_기준으로_묶는다` |
| 8 | 구간 시작이 달 중간이면 첫 봉은 불완전한 채로 포함된다. | `ChartTest.구간_시작이_달_중간이면_첫_봉은_불완전한_채로_포함된다` |
| 9 | 봉의 `tradeAt`은 그 봉에 실제 포함된 첫 거래일이다. | `ChartTest.묶인_봉의_거래일은_버킷의_첫_거래일이다` |
| 10 | 장중이면 진행 중 봉이 마지막 버킷에 합쳐지고 `closed`는 `false`다. | `ChartTest.장중이면_마지막_버킷에_당일_봉이_합쳐지고_미확정이다` |
| 11 | 당일 시가가 0이면(조건 1) 진행 중 봉을 붙이지 않고 `asOf`는 `null`이다. | `StockChartServiceTest.당일_시가가_없으면_당일_봉을_붙이지_않는다` |
| 12 | 당일 거래일이 이미 확정 봉으로 있으면 진행 중 봉을 붙이지 않는다. | `ChartTest.당일_봉이_이미_확정되어_있으면_붙이지_않는다` |
| 13 | `averageVolume`은 확정 봉만으로, 마지막 최대 20개를 평균한다. | `ChartTest.평균_거래량은_확정_봉만으로_계산한다`, `ChartTest.확정_봉이_스무개보다_적으면_있는_만큼_평균한다` |
| 14 | `from`·`to`는 실제 포함된 첫·마지막 봉의 거래일이다. | `ChartTest.시작일과_종료일은_실제_포함된_봉의_거래일이다` |
| 15 | 봉이 없으면 빈 배열과 `null` 요약값을 반환한다. | `ChartTest.봉이_없으면_빈_배열을_반환한다` |
| 16 | 구간이 5년을 넘으면 400 / `P001`. | `ChartRangeTest.구간이_오년을_넘으면_예외다`, `StockChartControllerTest.구간이_5년을_넘으면_400과_P001을_반환한다` |
| 17 | `from`이 `to`보다 뒤면 400 / `P001`. | `ChartRangeTest.시작일이_종료일보다_뒤면_예외다` |
| 18 | 허용되지 않은 `interval`이면 400 / `P001`. | `StockChartControllerTest.잘못된_봉_단위면_400과_P001을_반환한다` |
| 19 | 날짜 형식이 잘못되면 400 / `P001`. | `StockChartControllerTest.잘못된_날짜_형식이면_400과_P001을_반환한다` |
| 20 | 없는 종목이면 404 / `P002`. | `StockChartControllerTest.없는_종목이면_404와_P002를_반환한다` |
| 21 | KIS 실패면 502 / `P007`. | `StockChartControllerTest.시세_조회에_실패하면_502와_P007을_반환한다` |
| 22 | 같은 날 더 넓은 구간을 요청하면 일봉을 다시 동기화한다. | `StockDailyPriceSyncPolicyTest.같은_날_더_이른_시작일을_요청하면_다시_동기화한다` |
| 23 | 차트 모양을 바꿔도 재요청 없이 같은 응답으로 라인·캔들을 그릴 수 있다. | 4번 테스트로 증명. 프론트 검증 항목 (RQ-1003) |
| 24 | 요청 구간이 오늘보다 앞에서 끝나면(조건 3) 오늘 봉이 끼어들지 않는다. | `StockChartServiceTest.과거_구간을_조회하면_오늘_봉이_끼어들지_않는다` |
| 25 | 요청 구간이 오늘보다 뒤에서 시작해도(조건 3) 오늘 봉이 끼어들지 않는다. | `StockChartServiceTest.미래_구간을_조회하면_오늘_봉을_붙이지_않는다` |
| 26 | 시가가 0이 아니어도 오늘 봉 값이 마지막 확정 봉과 모두 같으면(조건 2, 장 시작 전 전날 시세) 붙이지 않는다. | `StockChartServiceTest.장_시작_전_전날_시세면_진행_중_봉을_붙이지_않는다` |
| 27 | 자릿수가 달라도 같은 값이면 반복으로 본다 (`115100` = `115100.0000`). 거래량 하나만 달라도 반복이 아니다. | `DailyPricesTest.마지막_봉과_값이_같으면_자릿수가_달라도_반복으로_본다` |
| 28 | 현재가 기준가가 저장된 종가와 달라도 오늘 장의 시세면 붙인다. 구간에 확정 봉이 없어도 오늘이 구간 안이면 붙인다. | `StockChartServiceTest.기준가가_저장된_종가와_달라도_오늘_시세면_진행_중_봉을_붙인다`, `오늘_하루만_조회해도_장중이면_진행_중_봉을_붙인다` |

## 미해결 질문

- 개장 직후 KIS가 당일 `low`를 0으로 주는 등 이상값이 있는지. "붙이지 않는 조건"은 2026-10-05 관측으로 조건 2·3을 더해 보강했고, `low` 이상값은 아직 보지 못했다.
- **주식 현재가 기준가와 일봉 종가의 어긋남:** 2026-10-07 모의 도메인에서 국내 35종목 중 30종목이 어긋났다(위 "조건 2" 참고). 주식 차트는 이 값을 견주지 않게 바꿨지만, 원인(모의 도메인의 성질인지)과 운영 KIS가 모의인지 실전인지는 확인 전이다.
- 미국 종목의 애프터마켓 시세가 현재가에 반영되는 경우 진행 중 봉을 정규장 기준으로 자를지 — 현재는 KIS 현재가를 그대로 쓴다.
- 구간 상한 5년이 실제 사용 패턴에 맞는지 — 프론트의 최대 줌 아웃 범위가 정해지면 다시 본다.
