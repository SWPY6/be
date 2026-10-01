# 기능 맵: 시장 지표 조회 (RQ-0301)

시장 요약 화면의 "시장 한눈에 보기"를 구성하는 모듈과 빌드 순서다. 국내·해외 시장별로 주요 지수, 환율, 유가의
현재값과 등락률을 카드로 보여 주고, 지표마다 캔들 차트를 제공한다. 국내·해외 시장을 전환하면 지표가 그 시장 기준으로 바뀐다.

종목 상세 화면의 기능 맵은 `CAPABILITY-MAP.md`에 따로 있다. 이 맵은 그 위에 얹힌다.
`kis-client`를 그대로 쓰고, `stock-chart`의 봉 집계 도메인을 재사용한다.

## 모듈

| 모듈 id | 책임 | 의존 대상 | 명세 |
| --- | --- | --- | --- |
| `market-indicator` | 지표 마스터 5종(`MarketIndicator`), 시장 탭별 구성, 지표별 KIS 조회 코드. HTTP API 없음 | — | `SPEC-market-indicator.md` |
| `market-daily-price` | 지표 일봉(OHLC) 저장, 읽기를 통한 동기화(read-through), 기간 조회. HTTP API 없음 | `market-indicator`, `kis-client` | `SPEC-market-daily-price.md` |
| `market-quote` | 지표 현재값, 직전 종가 대비 등락폭·등락률, 당일 시가·고가·저가, 기준 시각. Redis TTL 캐시. HTTP API 없음 | `market-indicator`, `kis-client` | `SPEC-market-quote.md` |
| `market-summary` | 시장 탭별 지표 카드 목록. `GET /api/v1/markets/summary?region=DOMESTIC\|OVERSEAS` | `market-indicator`, `market-quote` | `SPEC-market-summary.md` |
| `market-chart` | 지표별 확정 일봉과 당일 진행 중인 봉. `GET /api/v1/markets/indicators/{indicator}/chart` | `market-daily-price`, `market-quote` | `SPEC-market-chart.md` |

빌드 순서: `market-indicator` → (`market-quote`, `market-daily-price`) → `market-summary` → `market-chart`

- `market-summary`는 `market-quote`만 있으면 되므로 차트보다 먼저 출시할 수 있다.
- 유가는 **이번 범위에서 제외**하고 나중에 넣는다. 아래 "유가 (추후)" 절을 참고한다.

## 왜 이렇게 나누는가

- `stock-*` 모듈을 재사용하지 않고 새로 두는 이유:
  - 기존 모듈은 전 구간이 `stockId`(`Stocks` 행)에 묶여 있다.
  - 지수는 `Markets` 행이 있지만 환율과 유가는 `Markets` 행이 아니다.
  - 그래서 `stocks`에 지표를 가짜 종목으로 넣지 않고 식별자를 따로 둔다.
- `market-indicator`를 따로 두는 이유:
  - 나머지 네 모듈이 **모두** "어떤 지표가 어느 탭에 속하고, KIS에서 어떤 코드로 조회하는가"를 필요로 한다.
  - HTTP API가 없는 모듈이라는 선례는 `industry`, `stock-daily-price`가 이미 만들어 뒀다.
- `market-quote`와 `market-daily-price`는 서로 의존하지 않는다.
  - 지표 카드에는 "20거래일 평균 거래량" 같은 일봉 파생 값이 없어서, `stock-quote`와 달리 일봉 저장소가 필요 없다.
- `market-chart`의 당일 진행 중인 봉은 현재값에서 나온다. 그래서 `market-chart`가 `market-quote`에 의존한다. `stock-chart`와 같은 구조다.

## 요구사항 ↔ 모듈 매핑

| 요구사항 | 담당 | 비고 |
| --- | --- | --- |
| 주요 지수·환율의 현재값과 등락률 카드 | `market-summary` | 탭 하나의 지표 3개를 한 번에 응답. 서버는 지표마다 KIS를 따로 호출하고 캐시한다 |
| 유가 현재값과 등락률 카드 | **추후** | 아래 "유가 (추후)" 절 |
| 지표 차트 | `market-chart` | 봉마다 OHLC. 라인/캔들 전환은 프론트가 재요청 없이 처리 |
| 국내·해외 시장 전환 시 지표 변경 | `market-indicator` (구성), `market-summary` (`region` 파라미터) | 탭 전환은 프론트. 백엔드는 `region`별 목록만 준다 |

**탭 구성** (사용자 확정. 유가는 추후 두 탭에 모두 추가):

| region | 지표 |
| --- | --- |
| `DOMESTIC` | KOSPI, KOSDAQ, USD/KRW |
| `OVERSEAS` | NASDAQ, S&P500, USD/KRW |

## KIS 실측 결과 (2026-09-30, 모의 도메인 `openapivts`)

| 지표 | TR | KIS 코드 | 현재값 | 일봉 OHLC | 비고 |
| --- | --- | --- | --- | --- | --- |
| KOSPI | `FHPUP02100000` / `FHKUP03500100` | `U` / `0001` | ✅ `bstp_nmix_prpr`, `bstp_nmix_prdy_ctrt` | ✅ `output2` | 거래량 있음 |
| KOSDAQ | 위와 같음 | `U` / `1001` | ✅ | ✅ | |
| NASDAQ | `FHKST03030100` | `N` / `COMP` | ✅ `output1.ovrs_nmix_prpr`, `prdy_ctrt` | ✅ `output2` | 당일 행의 거래량이 0 |
| S&P500 | `FHKST03030100` | `N` / `SPX` | ✅ | ✅ | 거래량 항상 0 |
| USD/KRW | `FHKST03030100` | `X` / `FX@KRW` | ✅ 소수점 4자리 | ✅ | 거래량 0. **`output2`에 당일 행이 없음** (최신 행이 전 거래일) |
| WTI | `FHKST03030100` (`S` / `CL`) | — | ❌ 전부 0 | ❌ 0행 | 코드가 틀렸거나 지원하지 않음 |
| WTI | `HHDFC55010000` (해외선물 현재가) | `CLX26` | ❌ `EGW00551` NYMEX SUB거래소 신청 계좌가 아님 | — | 시세 신청 후 재실측 |

- 모의 도메인은 초당 호출 한도가 작다. 1초 간격 호출도 `EGW00201`(초당 거래건수 초과)에 걸렸다.
- 해외 지표는 `FHKST03030100` 한 번으로 현재값(`output1`)과 일봉(`output2`)을 모두 받는다. 기간을 오늘 하루로 주면 `output1`만 온다.
- 일봉 한 번 호출의 최대 건수: 국내 지수 50, 해외 지수·환율 100.
- 거래일: 국내 지수는 한국 휴장일, 해외 지수는 미국 휴장일을 뺀다. 환율은 휴일 없는 평일 시계열이다.
- 위 일봉 실측은 한국 시간 새벽(장 시작 전)이었다. 그때 환율은 전날(9/29) 행이 아직 없었다. 장중 동작은 다시 실측한다 (`SPEC-market-daily-price.md`).

## 공통 결정

| 항목 | 결정 |
| --- | --- |
| 지표 식별자 | 새 enum `MarketIndicator { KOSPI, KOSDAQ, NASDAQ, SP500, USD_KRW }`. API 경로와 응답에 enum 이름을 쓴다. `marketId`는 쓰지 않는다 (환율과 유가는 `Markets` 행이 없다) |
| 지표 종류 | 지표마다 종류(`IndicatorKind`)와 심볼을 가진다. 종류는 `DOMESTIC_INDEX`, `OVERSEAS_INDEX`, `EXCHANGE_RATE` 세 가지다. KIS 어댑터가 종류별로 TR과 시장구분 코드를 정한다. TR ID는 `market-indicator`에 두지 않는다 (`SPEC-market-indicator.md`) |
| 탭 구분 | 새 enum `MarketRegion { DOMESTIC, OVERSEAS }`. 지표가 자기가 속한 탭을 집합으로 가진다. 탭 구성은 `market-indicator`가 소유한다. 환율은 두 탭에 모두 속한다 |
| 단위 | 카드와 차트 응답에 `unit`을 둔다: 지수는 `POINT`, 환율은 `KRW` |
| 카드 응답 형태 | 카드는 고정 필드가 아니라 **배열**이다. 카드마다 `indicator`, 표시명, `unit`이 들어간다. 프론트는 받은 배열을 그대로 그린다 |
| 등락률 기준 | 직전 거래일 종가 대비. KIS가 준 등락률을 그대로 쓰지 않고 `stock-quote`의 `Quote`처럼 현재값과 직전 종가로 계산해 기준을 통일한다 |
| 현재값 캐시 | Redis TTL 캐시, 키는 `market-quote:{indicator}`. 지표가 5개로 고정이라 `stock-quote`의 활성 종목 추적(`quote:active`)은 두지 않는다. 캐시 미스일 때 KIS를 호출한다 |
| 일봉 저장 | DB 저장, 부족한 구간만 KIS에서 받아 채운다(read-through). `stock-daily-price`와 같은 규칙이고 당일 봉은 저장하지 않는다 |
| 일봉 테이블 | 기존 `market_daily_prices`를 고쳐서 쓴다 (사용자 결정). `marketId` → `indicator`(`VARCHAR`), 시가·고가·저가 추가, `changeRate` 삭제. 값 타입은 `stock.price.DailyPrice`를 재사용하고 volume은 0이다 (`SPEC-market-daily-price.md`) |
| 거래량 | **응답에 넣지 않는다.** RQ-0301에 거래량 요구가 없고, 해외 지수와 환율은 KIS가 0을 준다. 그래서 `averageVolume`과 봉의 `volume`도 없다 |
| 봉 집계 재사용 | `stock/chart`의 `Chart`, `ChartInterval`, `ChartRange`, `ChartCandle`을 **옮기지 않고** 재사용한다. 모듈 루트끼리의 의존이라 허용된다. `Chart`가 `Quote` 대신 `LiveCandle`(당일자 일봉 + 기준 시각)을 받도록만 바꾼다. 기존 `stock-chart` 테스트가 모두 통과해야 한다 (`SPEC-market-chart.md`) |
| 차트 규칙 | `stock-chart`와 같다. `interval`은 `1D\|1W\|1M\|3M\|1Y`, `from`/`to` 기본값은 최근 2개월, 최대 5년 |
| 진행 중인 봉 | `market-quote`의 현재값·시가·고가·저가로 만들고 저장하지 않는다. 시세의 전일 종가가 마지막 확정 봉 종가와 같을 때만 붙인다. 장 시작 전 KIS가 전날 값을 주기 때문이다 (`SPEC-market-chart.md`) |
| KIS 실패 | 폴백 없이 502 `P007`. 카드 목록은 지표 하나라도 실패하면 전체 502다 (`SPEC-market-summary.md`) |
| 표기 | 서버는 반올림한 JSON number까지 책임진다. 지수는 소수 둘째 자리, 환율은 소수 둘째 자리(KIS는 넷째 자리까지 줌). 부호, `%`, 통화 기호는 프론트가 붙인다 |
| 스케줄러 | 두지 않는다 |
| API 문서 | 명세의 "API 계약" 절을 프론트에 전달한다. 구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다 |

## 유가 (추후)

이번 범위에서 제외한다. `MarketIndicator`에도 넣지 않는다.

### 확장 대비

유가를 넣을 때 **API 계약과 DB 스키마를 바꾸지 않도록** 위 공통 결정을 잡아 두었다. 각 결정은 이미 있는 지표 5종의 차이 때문에 필요한 것이고, 유가만을 위한 코드(빈 `WTI` 상수, 미구현 스텁)는 두지 않는다.

| 대비 지점 | 유가가 들어올 때 | 지금 필요한 이유 |
| --- | --- | --- |
| 지표 종류 | `OVERSEAS_FUTURES`를 하나 더 추가한다 | 지금도 종류가 세 가지다 |
| 탭 집합 | 두 탭에 모두 속하게 한다 | 환율이 이미 두 탭에 속한다 |
| `unit` | `USD`를 추가한다 | 지수와 환율의 단위가 다르다 |
| 카드 배열 | 배열에 원소가 하나 늘 뿐, 계약은 그대로다 | 탭마다 구성이 이미 다르다 |
| 일봉 테이블 `indicator` 키 | 스키마 변경 없이 행만 늘어난다. 컬럼은 네이티브 `ENUM`이 아니라 `VARCHAR`로 매핑한다 | 환율에 `marketId`가 없다 |
| 거래량 제외 | 선물 거래량을 다룰 필요가 없다 | 해외 지수와 환율의 거래량이 0이다 |

대비하지 않는 것: 선물 월물 코드 교체 규칙, 해외선물 일봉 TR. 둘 다 시세 신청 후 실측해야 정할 수 있다.

### 유가를 넣을 때 할 일

1. NYMEX 시세 신청. 신청 전에 Open API 과금 여부와 재배포 가능 여부를 고객센터에 확인한다.
2. 실측으로 현재가·일봉 TR과 월물 코드 규칙을 확정한다.
3. `MarketIndicator.WTI`와 종류 `OVERSEAS_FUTURES`를 추가하고, 두 탭에 소속시키고, `unit=USD`로 둔다.
4. KIS 어댑터에 해외선물 분기를 추가한다.
5. 명세의 탭 구성과 API 예시를 갱신한다.

### 조사 결과

- 방향: KIS 해외선물 API(`HHDFC55010000`)와 계좌의 NYMEX 시세 신청.
- 비용: HTS·MTS의 NYMEX 실시간 시세는 무료지만, **API로 받는 CME/NYMEX 시세는 거래소마다 월 221.10 USD**(부가세 포함, 2025-01-01 적용, 한국투자증권 공지)다. 신청 전에 고객센터에서 Open API 과금 여부와 서비스 사용자에게 재배포해도 되는지를 확인한다.
- 절차: 해외선물옵션 계좌 준비 → HTS [7936] 또는 MTS에서 시세 신청 → 그 계좌의 앱키로 재실측.
- 대안: 원유 ETF(`USO`) 가격을 기존 해외 시세 연동(`HHDFS76200200`)으로 조회한다. 추가 비용은 없지만 WTI 가격이 아니다.
- 토스증권 Open API는 쓸 수 없다. 원자재 지표가 없고, 약관이 제3자 배포를 금지한다.
- 선물이라 **월물 코드가 바뀐다** (`CLX26` → `CLZ26` …). 어떤 월물(최근월물)을 쓸지, 만기 때 코드를 어떻게 바꿀지 정해야 한다.
- 선물은 일봉 TR이 해외지수와 다르다. 차트용 일봉 TR도 신청 후 확인한다.

## 미해결 질문

- KIS Open API 약관에 재배포 제한이 있는가? 있다면 유가만이 아니라 이미 쓰는 주식 시세 전체에 해당하는 문제다.
- `CAPABILITY-MAP.md`는 "실전 서버·실전 키"를 전제하지만 현재 `.env`는 모의 도메인이다. 운영 환경의 base URL을 확인해야 한다.
- 환율은 거의 24시간 움직인다. `stock-quote`의 `priceTiming`(장중·장 마감) 같은 구분을 지표 카드에도 줄 것인가?
- 해외 지수의 "당일"은 미국 날짜 기준이다. 한국 시간 새벽에 조회하면 국내 탭과 날짜가 다르게 보인다. 카드에 기준 시각(`priceAt`)을 주는 것으로 충분한가?
