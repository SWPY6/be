# 기능 맵: 종목 상세 — 차트 및 주요 지표 (RQ-1001 ~ RQ-1008)

종목 상세 화면의 "차트 및 주요 지표" 영역을 구성하는 모듈과 빌드 순서다. 요구사항 하나가 독립적으로 테스트 가능한 네 기능에 걸치므로 명세를 모듈별로 나눈다.

## 모듈

| 모듈 id | 책임 | 의존 대상 | 명세 |
| --- | --- | --- | --- |
| `kis-client` | 한국투자증권(KIS) Open API 접근토큰 발급·캐시, 공통 헤더, HTTP 호출, KIS 오류 → `ErrorCode` 변환 | — | `SPEC-kis-client.md` |
| `stock-daily-price` | 종목 일봉(`StockDailyPrices`) 저장, read-through 동기화, 기간 조회, 최근 20거래일 평균 거래량. HTTP API 없음 | `kis-client` | `SPEC-stock-daily-price.md` |
| `stock-quote` | 현재가·직전 정규장 종가 대비 등락률·가격 기준 시각·실시간 여부·주요 지표 8종. 종목별 TTL 캐시. `GET /api/v1/stocks/{stockId}/quote` | `kis-client`, `stock-daily-price` | `SPEC-stock-quote.md` |
| `stock-chart` | 기간별 확정 일봉 + 당일 진행 중 봉 + 20거래일 평균 거래량 기준선. `GET /api/v1/stocks/{stockId}/chart` | `stock-daily-price`, `stock-quote` | `SPEC-stock-chart.md` |

빌드 순서: `kis-client` → `stock-daily-price` → `stock-quote` → `stock-chart`

## 왜 이렇게 나누는가

- 차트의 당일 봉(시가·고가·저가·현재가·누적 거래량)은 현재가 응답에서 나온다 → `stock-chart`가 `stock-quote`에 의존한다.
- 현재가의 "20거래일 평균 대비 거래량"은 일봉 저장소에서 나온다 → `stock-quote`가 일봉에 의존한다.
- 일봉 저장·동기화·20일 평균을 `stock-daily-price`로 분리해야 의존이 한 방향이 된다. 두 모듈이 서로를 필요로 하면 그것은 하나의 모듈이다.
- KIS 토큰 발급(1분 1회 제한, 24시간 유효)과 공통 호출은 두 도메인 모듈이 모두 쓰므로 `kis-client`가 소유한다.

## 요구사항 ↔ 모듈 매핑

| RQ-ID | 기능명 | 담당 모듈 | 비고 |
| --- | --- | --- | --- |
| RQ-1001 | 가격 정보 표시 | `stock-quote` | 현재가, 등락률, `priceAt`, `priceTiming` |
| RQ-1002 | 차트 기간 선택 | `stock-chart` | `period` 파라미터, 기본 `1M` |
| RQ-1003 | 차트 모양 선택 | `stock-chart` | 봉마다 OHLC를 내려주고 라인/캔들 전환은 프론트가 재요청 없이 처리 |
| RQ-1004 | 차트 상세값 확인 | `stock-chart` | 마우스 오버 표시는 프론트. 백엔드는 날짜·OHLC 제공 |
| RQ-1005 | 차트 범위 조절 | `stock-chart` | 확대·축소·기간 초기화는 프론트. 백엔드는 기간 전체 데이터 제공 |
| RQ-1006 | 거래량 차트 | `stock-chart` | 봉마다 `volume`, `averageVolume20d` |
| RQ-1007 | 20거래일 평균 거래량 | `stock-daily-price` (계산), `stock-chart` (응답) | 기준선 1개(스칼라) |
| RQ-1008 | 주요 지표 | `stock-quote` | 전일 종가, 시가, 고가, 저가, 거래량, 20거래일 평균 대비 거래량, 시가총액, 거래대금 |

## 프론트/백엔드 책임 경계

RQ-1003 라인/캔들 전환, RQ-1004 마우스 오버, RQ-1005 휠 확대·축소·기간 초기화, RQ-1007 범례 표시는 프론트엔드 렌더링 책임이다. 백엔드는 기간별 OHLCV + 거래량 + 20거래일 평균 거래량을 한 번에 내려주고, 프론트는 같은 데이터로 라인(종가)과 캔들을 그린다. 그래서 차트 모양을 바꿔도 재요청이 없고 선택한 기간이 유지된다.

## 공통 결정

| 항목 | 결정 |
| --- | --- |
| 저장소 | MySQL 유지. 시계열 DB는 도입하지 않는다. 필요한 데이터가 일봉(종목당 연 250행)뿐이라 `(stock_id, trade_at)` 유니크 인덱스로 충분하다. 분봉·틱 요구가 생기면 재검토한다 |
| 시세 출처 | KIS Open API만 사용. 실전 서버·실전 키. 해외 실시간 무료 시세 신청 완료 전제 |
| 대상 시장 | 국내 KOSPI·KOSDAQ, 미국 NASDAQ·S&P500 (AMEX 제외) |
| 현재가 제공 | REST 조회, 프론트가 폴링. 종목당 KIS 조회 1회 → TTL(기본 10초) 동안 **Redis** 캐시 값을 응답. Redis 장애 시 KIS 직접 호출 |
| 일봉 저장 | DB 저장, 부족 구간만 KIS에서 받아 채움(read-through). 스케줄러 없음 |
| 당일 봉 | 차트 끝에 진행 중 봉을 붙인다. 현재가 캐시에서 구성하고 저장하지 않는다 |
| KIS 실패 | 폴백 없이 예외 → 502 `P007` |
| API 문서 | 명세의 "API 계약" 절을 프론트에 전달한다. 구현 후에는 Swagger(`/swagger-ui.html`)가 살아 있는 문서다 |
