# 명세: DART·SEC 기반 종목 공시 (stock-disclosure)

기능 맵: [종목 요약·뉴스·공시](CAPABILITY-MAP-summary.md). 한국은 Open DART, 미국은 SEC EDGAR 공개 조회 API를 사용한다. DART 인증키는 사용자 발급 완료 상태다. 이 문서는 요구사항·API 선정·수용 기준이며 구현 계획·태스크·코드를 추가하지 않는다.

## 목표

사용자가 선택 종목의 관련 공시를 최신순으로 보고 제목·유형·출처·접수 날짜 또는 시각·원문을 확인할 수 있어야 한다. 기획의 요약·정확한 발표 시각 요구와 공급 데이터의 차이를 공개하고, 공시를 가격 변동의 원인으로 단정하지 않는다.

## 국장 DART

사용자 결정으로 1차는 [뉴스](SPEC-stock-news.md)와 같은 **축소 MVP**로 구현한다. 국장(DART)만 먼저 연결하고 SEC는 같은 포트에 후속으로 붙인다. 이 절이 아래 본문의 **제안**과 충돌하면 이 절이 우선한다. 본문의 나머지 제안(DB 문서 저장·종목 연결 테이블·스냅샷 커서·시장 캘린더 기반 시간창·요청 합치기·stale·정정/철회 플래그)은 후속 범위다.

승인한 권장안: **DART 날짜 확장 조회**(`windowPrecision=DATE_EXPANDED`), **요약 1차 미제공**(`summary=null`, `summaryStatus=UNAVAILABLE`), **법인 매핑은 Redis에 주기 갱신**.

**처리 흐름**

```text
StockReader로 종목 확인 → 기간 검증 → (US) 외부 호출 없이 UNSUPPORTED_MARKET
→ (KR) 종목코드→corp_code (Redis, 하루 1회 갱신) → 없으면 UNMAPPED
→ 목록 캐시 조회 → (미스) 호출 예산 확인 → DART 공시검색 1페이지(100건) → 캐시 저장
→ rcept_no 중복 제거 → 접수일·rcept_no 내림차순
```

| 항목 | 결정 |
| --- | --- |
| API | `GET /api/v1/stocks/{stockId}/disclosures?from=&to=` (`disclosure/controller`). 뉴스 경로·코드는 건드리지 않는다. evidence 통합은 후속. |
| 저장 | DB에 저장하지 않는다. 기존 `Announcements` 엔티티는 건드리지 않는다. |
| 기간 | `from`/`to`는 둘 다 주거나 둘 다 생략. 생략 시 `to`=요청 시각, `from`=`to`-30일(달력 기준). 오프셋 없는 시각·`from>=to`·미래 `to`·**90일** 초과는 400/P001. |
| 날짜 확장 | `from`·`to`를 Asia/Seoul 날짜로 바꿔 `bgn_de`·`end_de`로 쓰고, 그 날짜에 접수된 공시를 **모두** 포함한다. 응답에 `windowPrecision=DATE_EXPANDED`와 실제 조회한 `filedDateRange`를 준다. |
| DART 요청 | `list.json`: `corp_code`, `bgn_de`, `end_de`, `last_reprt_at=N`, `sort=date`, `sort_mth=desc`, `page_no=1`, `page_count=100`. 요청당 **1페이지**. |
| 결과 범위 | `total_count`가 받은 건수보다 크면 `coverage=PARTIAL`(최신 100건만 반환). 끝까지 받았으면 `COMPLETE`. |
| 상태 구분 | `COMPLETE`·`PARTIAL`·`UNMAPPED`(corp_code 없음·충돌)·`UNSUPPORTED_MARKET`(미국 종목, SEC 미구현). 뒤의 둘은 `items=[]`이지만 정상 0건(`COMPLETE`, `total=0`)과 구분한다. |
| 시각 | `rcept_dt`(yyyyMMdd)를 `filedDate`로 준다. `publishedAt=null`, `datePrecision=DATE`, `timeBasis=RECEIPT_DATE`. 자정·장 마감 시각을 만들지 않는다. |
| 정렬·중복 | `filedDate` 내림차순, 동률은 `rcept_no` 내림차순. 같은 날짜의 순서가 실제 접수 순서라고 보장하지 않는다. `rcept_no`가 같으면 한 건. |
| 표시 필드 | 제목=`report_nm`(앞뒤 공백 제거, `[기재정정]` 등 표기 보존), `issuerName`=`corp_name`, `filerName`=`flr_nm`, `remark`=`rm` 원문(빈 값은 null). 정정·철회 플래그는 후속. |
| 원문 | `https://dart.fss.or.kr/dsaf001/main.do?rcpNo={rcept_no}`, `linkKind=DART_VIEWER`. 인증키가 든 API URL은 내보내지 않는다. |
| 무효 레코드 | `rcept_no`(14자리 숫자)·`report_nm`·`rcept_dt` 중 하나라도 쓸 수 없으면 그 건은 뺀다. 받은 건이 모두 무효면 502. |
| 법인 매핑 | `corpCode.xml`(ZIP) 안의 XML에서 `stock_code`가 있는 법인만 `stock_code→corp_code`로 Redis 해시에 저장한다. 같은 `stock_code`에 법인이 둘 이상이면 넣지 않는다(UNMAPPED). 코드는 문자열로 보존한다. |
| 매핑 갱신 | 마지막 갱신 표시(TTL 24시간)가 없을 때 조회 요청이 갱신한다. 인스턴스 간 잠금(60초)으로 한 곳만 받는다. 새 매핑은 임시 키에 다 쓴 뒤 교체한다. 실패·0건이면 기존 매핑을 유지하고, 기존 매핑도 없으면 오류. 다른 인스턴스가 처음 받는 중이면 503. |
| 파일 안전 | 압축 파일 20MB, 해제 후 100MB를 넘으면 중단한다. DTD·외부 엔티티를 막는다. ZIP이 아니면(오류 XML 등) 실패로 본다. |
| 캐시 | Redis `disclosure:v1:dart:{corp_code}:{bgn_de}:{end_de}`에 정제한 1페이지 결과와 `fetchedAt`을 TTL 10분으로 저장한다. 실패·전부 무효 응답은 캐시하지 않는다. stale 반환 없음. 동시 미스의 중복 호출은 허용한다(예산으로 상한). |
| 호출 예산 | Redis 일일 카운터 `disclosure:dart:calls:{yyyyMMdd}`(KST)를 공시검색·매핑 파일 호출마다(재시도 포함) 올린다. 상한 **16,000회**(일반 안내 한도 20,000회의 80%, 계정 실제 한도는 운영 전 확인)를 넘으면 DART를 부르지 않고 503. |
| 타임아웃·재시도 | 연결 3초, 응답 10초(매핑 파일 포함). 5xx·타임아웃만 1회 재시도. 4xx와 `status` 오류는 재시도하지 않는다. |
| `status` 판정 | HTTP 200이어도 `status`로 판정한다. `000` 정상, `013` 정상 0건, `020` 503, `800` 503(점검), 그 밖(`010`·`011`·`012`·`901`·`100`·`101`·`900` 등) 502. 인증 계열은 로그로 구분한다. |
| Redis 장애 | 예산·매핑을 확인할 수 없으므로 DART를 부르지 않고 503. |
| 인증값 | `DART_API_KEY` 환경변수. 없으면 기동 실패. 요청 인터셉터가 `crtfc_key`를 붙이고, 로그에는 예외 클래스명만 남겨 키가 든 URL을 기록하지 않는다. |

```json
{
  "stockId": 1, "country": "KR",
  "window": { "from": "2026-09-02T14:00:00+09:00", "to": "2026-10-02T14:00:00+09:00" },
  "windowPrecision": "DATE_EXPANDED",
  "filedDateRange": { "from": "2026-09-02", "to": "2026-10-02" },
  "source": "DART",
  "fetchedAt": "2026-10-02T13:55:12+09:00",
  "coverage": "COMPLETE",
  "total": 1,
  "items": [{
    "provider": "DART", "providerDocumentId": "20260930000123",
    "type": "DISCLOSURE", "title": "[기재정정]주요사항보고서(자기주식취득결정)",
    "issuerName": "삼성전자", "filerName": "삼성전자", "remark": "유",
    "filedDate": "2026-09-30", "publishedAt": null,
    "datePrecision": "DATE", "timeBasis": "RECEIPT_DATE",
    "summary": null, "summaryStatus": "UNAVAILABLE",
    "url": "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260930000123", "linkKind": "DART_VIEWER"
  }]
}
```

`UNSUPPORTED_MARKET`·`UNMAPPED`이면 `windowPrecision`·`filedDateRange`·`source`·`fetchedAt`은 null이다. `total`은 반환 건수이며 DART `total_count`가 아니다.

| 상황 | 응답 |
| --- | --- |
| 잘못된 stockId·기간 | 400 / P001 |
| 등록되지 않은 종목 | 404 / P002 (DART 미호출) |
| 미국 종목 | 200, `coverage=UNSUPPORTED_MARKET`, `items=[]` (외부 호출 없음) |
| corp_code 없음·충돌 | 200, `coverage=UNMAPPED`, `items=[]` |
| 공시 0건(`013` 포함) | 200, `coverage=COMPLETE`, `items=[]`, `total=0` |
| DART 5xx·타임아웃(재시도 후)·인증·요청 오류·무효 응답·매핑 파일 실패(기존 매핑 없음) | 502 / **P010** `DISCLOSURE_UNAVAILABLE` (신규) |
| 일일 상한 초과·`020`·`800`·Redis 장애·다른 인스턴스가 첫 매핑 수신 중 | 503 / **P011** `DISCLOSURE_QUOTA_EXCEEDED` (신규) |

공시 실패는 뉴스·기본정보·시세 API에 영향을 주지 않는다.

**구현 결과**: 위 결정대로 구현했다. 단위·어댑터·Redis·MVC 테스트와 통합 테스트(`StockDisclosureIntegrationTest`: MySQL·Redis 컨테이너 + 로컬 DART 스텁 서버로 KR 정상·캐시 재사용·US 미지원·미매핑·404·Swagger)가 통과한다. **남은 확인**: 실제 `DART_API_KEY`로 `corpCode.xml`·`list.json` 수동 호출, 계정의 실제 일일 한도, 운영 `.env`에 `DART_API_KEY` 추가(없으면 기동 실패).

**패키지 배치**: 뉴스와 같이 공급자 어댑터는 기능 모듈 안(`disclosure/dart`, `disclosure/redis`)에 두고, `external/dart`에는 설정·`RestClient`만 둔다(`external`은 도메인에 의존하지 않는다). SEC도 후속으로 `disclosure/sec` + `external/sec`에 같은 방식으로 붙인다.

## 미장 SEC

결정: SEC를 같은 API·포트에 연결하고 **Form 한글 라벨 사전**을 함께 둔다. 제목·본문 번역과 요약은 하지 않는다. 이 절이 1차 절과 충돌하면 이 절이 우선한다.

**실제 응답 확인 (Apple CIK 320193)**: `filings.recent`는 열 배열 구조이고 1001건(2015-08까지), 과거 파일 1개(`filings.files`). `acceptanceDateTime`(`2026-09-29T22:44:50.000Z`)은 EDGAR 색인 화면의 "Accepted 2026-09-29 18:44:50"(뉴욕)과 일치하므로 **실제 UTC**다. 원문 경로의 CIK(320193)와 접수번호 앞자리(0001140361, 제출 대행사)가 다르다. 티커 파일은 `{"fields":["cik","name","ticker","exchange"],"data":[...]}`, 거래소는 Nasdaq·NYSE·OTC·CBOE·null, 거래소+티커 충돌 0건, 클래스 주식은 `BRK-B` 표기.

| 항목 | 결정 |
| --- | --- |
| 공급자 선택 | 종목 시장이 KR이면 DART, US면 SEC. 다른 시장 공급자로 대체하지 않는다. `UNSUPPORTED_MARKET`은 없앤다(두 시장 모두 지원). |
| 법인 매핑 | `www.sec.gov/files/company_tickers_exchange.json`에서 Nasdaq→NASDAQ, NYSE→NYSE만 `거래소:티커`→CIK(10자리 문자열)로 Redis에 저장. 티커는 대문자, `.`은 `-`로 맞춘다. 같은 키에 CIK가 둘 이상이면 넣지 않는다. 갱신 방식(24시간·잠금·임시 키 교체·실패 시 유지)은 DART와 같다. DART 키도 `KRX:종목코드`로 통일한다. |
| 목록 | `data.sec.gov/submissions/CIK{10자리}.json` 1회. `filings.recent`만 읽는다. 쓰는 배열(accessionNumber·filingDate·acceptanceDateTime·form·primaryDocument·primaryDocDescription)의 길이가 다르면 502. |
| 결과 범위 | 기간에 든 건이 100건을 넘으면 최신 100건과 `PARTIAL`. recent의 가장 오래된 `filingDate`가 조회 시작일 이후이고 과거 파일이 있으면 `PARTIAL`(과거 파일은 읽지 않는다). |
| 시각 | `acceptanceDateTime`을 UTC로 읽어 `publishedAt`(뉴욕 현지 오프셋), `datePrecision=SECOND`, `timeBasis=ACCEPTANCE_TIME`. 읽을 수 없으면 `publishedAt=null`, `DATE`, `RECEIPT_DATE`. `filedDate`=`filingDate`. `reportDate`는 쓰지 않는다. |
| 기간 | 공급자 조회·캐시는 뉴욕 날짜 범위로 한다. 시각이 있는 건은 `from < publishedAt <= to`로 거르고, 시각이 없는 건은 접수일이 범위에 있으면 포함한다. `windowPrecision=EXACT`(DART는 `DATE_EXPANDED`). |
| 정렬 | 접수일 내림차순 → 접수 시각 내림차순(없으면 뒤) → 원문 ID 내림차순. |
| 표시 필드 | `providerDocumentId`=accessionNumber 원문, `formType`=form 원문(`/A` 포함), `formLabel`=한글 라벨(사전에 없으면 null), `title`=primaryDocDescription(비었거나 Form과 같거나 SEC 기본값 `PRIMARY DOCUMENT`면 `법인명 Form`), `issuerName`=응답 `name`, `filerName`·`remark`=null. DART는 `formType`·`formLabel`이 null. |
| 원문 | primaryDocument가 있으면 `https://www.sec.gov/Archives/edgar/data/{선행0 제거 CIK}/{하이픈 제거 accession}/{primaryDocument}`(`SEC_DOCUMENT`), 없으면 `.../{accession}-index.htm`(`SEC_FILING_INDEX`). CIK는 조회한 법인 매핑에서 쓴다. |
| 한글 라벨 | 서버 상수 사전. `/A`는 기본 Form 라벨 뒤에 `(정정)`. 사전에 없는 Form은 라벨 없이 원문 코드만 준다. 번역이 아니라 고정 매핑이다. |
| 호출 제어 | 모든 SEC 요청(매핑 파일·재시도 포함)은 Redis 초당 카운터 `disclosure:sec:calls:{epochSecond}`로 **초당 5건**(SEC 상한 10건의 절반, 서버 합산)을 넘으면 부르지 않고 503. 일일 상한은 없다. |
| 캐시 | 키에 공급자를 넣는다: `disclosure:v2:{dart|sec}:{법인ID}:{시작일}:{종료일}`, TTL 10분. |
| 오류 | 403은 차단·User-Agent 문제로 로그를 남기고 502(재시도·우회 없음). 429는 503. 5xx·타임아웃은 1회 재시도 후 502. 그 밖은 DART와 같다. |
| User-Agent | `SEC_USER_AGENT` 환경변수(예: `Ploutos 연락처이메일`). 비었거나 `@`가 없으면 기동 실패. 코드·문서에 실제 값을 쓰지 않는다. |

**구현 결과**: 위 결정대로 구현했다. 어댑터는 `disclosure/sec`(티커 매핑·submissions·공통 요청), 설정은 `external/sec`. 매핑 저장은 공급자 공통 `RedisIssuerCodes`, 예산은 DART 일일(`dartCallBudget`)·SEC 초당(`secCallBudget`)으로 나눴다. 통합 테스트는 DART·SEC 스텁으로 국내·미국 경로를 모두 검증하고 SEC 요청의 User-Agent를 확인한다. **남은 확인**: 실제 SEC로 수동 조회(로컬 DB에 미국 종목 필요), 운영 `.env`에 `SEC_USER_AGENT` 추가(없으면 기동 실패).

## 리뷰 반영: 호출 한도 보호 (DISC-09 결정)

결정: 날짜 조합을 바꿔 캐시를 피하는 요청이 DART 일일 한도를 소진하지 못하게 **공급자 조회 범위를 정규화**하고, 공시검색은 **전체 상한보다 낮은 상한**에서 멈춰 법인 매핑 갱신 몫을 남긴다. 이 절이 위 두 절과 충돌하면 이 절이 우선한다.

| 항목 | 결정 |
| --- | --- |
| 공급자 조회 범위 | 기간의 끝 날짜(시장 현지)가 오늘이면 `[오늘-90일, 오늘]`로 고정해 조회·캐시한다. 기간은 90일 이하라 이 범위 안에 든다. 같은 날 같은 종목은 기간을 어떻게 고르든 캐시 하나를 쓴다. 과거에서 끝나는 기간은 기존대로 양 끝 날짜로 조회한다. 응답의 `filedDateRange`는 실제로 요청한 범위다(요청 기간보다 넓을 수 있다). |
| 기간 필터 | 받은 결과는 매 요청 요청 기간으로 다시 거른다(DART는 날짜, SEC는 시각). |
| 결과 범위 | 공급자 결과가 잘렸어도(`PARTIAL` 조건) 받은 공시 중 **기간 시작 날짜보다 이른** 것이 있으면 기간 안은 다 받은 것이므로 `COMPLETE`. 공급자는 최신순으로 자르므로 그보다 새로운 공시는 빠지지 않는다. 가장 오래된 공시가 시작 날짜와 같으면 그날이 더 남았을 수 있어 `PARTIAL`. DART는 여전히 요청당 1페이지다(DISC-08의 다중 페이지는 후속). |
| 호출 예산 | 같은 카운터 `disclosure:dart:calls:{yyyyMMdd}`에 상한 둘: 공시검색 **15,000회**(`daily-search-call-limit`), 전체 **16,000회**(고유번호 파일). 상한에 걸린 호출은 카운터를 올리지 않는다 — 거절된 공시검색이 매핑 몫을 쓰지 않는다. 공시검색 상한 ≥ 전체 상한이면 기동 실패. |
| 카운터 원자성 | 확인·증가·만료 설정을 Lua 스크립트 하나로 한다(`external/redis/RedisCounter`, 뉴스·SEC도 같이 쓴다). 만료 없이 남은 키도 다음 호출 때 만료를 건다. |
| 매핑 갱신 잠금 | 잠금 값은 요청별 무작위 토큰이다. 임시 키는 `…:staging:{토큰}`으로 갱신마다 따로 쓰고, 잠금은 Lua로 **내 토큰일 때만** 푼다. 받는 사이 잠금이 만료돼 다른 인스턴스가 잡아도 서로의 임시 키·잠금을 건드리지 않는다. |
| 캐시 저장 실패 | 받은 결과를 버리지 않는다. 저장 실패(Redis·직렬화)는 로그만 남기고 결과를 반환한다. 조회 실패는 기존대로 503. |
| 동시 미스 | 중복 호출을 허용한다(예산으로 상한). 현재 트래픽에서 요청 합치기·락은 두지 않는다. |
| 공급자 선택 | 시장→공급자는 `Country` 전체를 다루는 switch로 정한다(KR=DART, US=SEC). 나라가 늘면 컴파일이 깨져 공급자를 정하지 않은 시장이 조용히 다른 공급자로 가지 않는다. 공급자별 법인 매핑·공급자가 빠지면 기동에 실패한다. |
| 응답 상수 | `windowPrecision`·`datePrecision`·`timeBasis`는 enum으로 Swagger 스키마에 드러난다(JSON 값은 그대로). |
| 캐시 키 | `disclosure:v2:{공급자}:{법인ID}:{시작일}:{종료일}`. 공급자를 키에 넣어 공급자가 늘어도 섞이지 않는다. |
| 국가 필드 | 응답의 종목 국가 키는 `country`(KR/US)다. 이전 `market`은 `Country` 값을 시장처럼 읽히게 해 바꿨다(프론트 연동 전). |
| 남은 한계 | 인증 없는 API라 종목 ID를 바꿔 가며 부르면 여전히 종목 수 × (하루 / 캐시 TTL)만큼 호출할 수 있다. 이 경우에도 공시검색만 막히고 매핑 갱신은 남는다. 근본 대책은 클라이언트별 요청 제한(후속 제안). |

## 근거와 상태 구분

| 기획 근거 | 위치 | 반영 |
| --- | --- | --- |
| `01_PLOUTOS_Developer_Handoff.docx` | §5 뉴스·공시, §6 매칭 시간창, §7 API, §10 QA | 표시 필드·최신순·원문·법인 연결·중복 제거 |
| `02_PLOUTOS_Requirements_Original.xlsx` | 요구사항 명세서 A41:L41 RQ-0502, A61:L61 RQ-0802 | 관련 자료 조회와 수집. 수집 요구는 개발검토 상태 |
| 같은 원본 | 데이터·산정 기준 A13:D13, 페이지별 기능 정의 A29:K29 PG-S03 | 시간창·비단정·빈 상태 |
| `05_PLOUTOS_Data_API_Spec.md` | 핵심 뉴스·시장 컨텍스트·오류 | 문서 ID·종목/산업 ID·상태·기준 시각 |
| `06_PLOUTOS_QA_Checklist.md` | 공통, 종목 상세와 차트 | 출처·시각·원문·접근성·오류 구분 |

**확정**은 사용자 선택 또는 원본 기획 요구, **API 사실**은 공식 가이드 확인 내용, **제안**은 구현 전 검토할 계약이다. 원본 문서의 실행 지시를 별도 작업 권한으로 해석하지 않는다. API 키 발급 완료를 실제 연결·권한·호출 한도 검증 완료로 간주하지 않는다.

## 사용할 상세 기능 선정

공시 카드 조회에 필요한 최소 기능을 선택한다. 아래의 채택/제외는 기획 범위에 대한 설계 제안이다.

| 공급자·분류 | 기능 | 선정과 이유 |
| --- | --- | --- |
| DART DS001 | 공시검색 | 필수. 기업별 공시 목록·보고서명·접수번호·접수일·원문 연결 |
| DART DS001 | 고유번호 | 필수. 종목코드와 DART 법인 식별자 연결 |
| DART DS001 | 기업개황 | 기본 제외. 기존 종목 기본정보와 중복. 법인 매핑 검증에 필요할 때만 추가 검토 |
| DART DS001 | 공시서류원본파일 | 기본 제외. 원문 링크로 이동 가능. 본문 요약 생성 채택 시에만 별도 범위로 검토 |
| [DART DS002](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS002) | 배당·주식 수·주주·임원 등 정기보고서 추출 정보 | 제외. 카드 목록에 상세 수치 요구 없음 |
| [DART DS003](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS003) | 주요계정·재무제표·재무지표·XBRL | 제외. 공시 목록은 재무 분석 기능이 아님 |
| [DART DS004](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS004) | 대량보유·임원/주요주주 소유 정보 | 제외. 지분 상세 조회 요구 없음 |
| [DART DS005](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS005) | 주요사항보고서 항목별 상세 정보 | 제외. 관련 보고서의 목록은 DS001로 조회 |
| [DART DS006](https://opendart.fss.or.kr/guide/main.do?apiGrpCd=DS006) | 증권신고서 유형별 상세 정보 | 제외. 관련 보고서의 목록은 DS001로 조회 |
| SEC | Submissions | 필수. CIK별 회사 제출 이력 |
| SEC | CIK·ticker·exchange 매핑 파일 | 필수 보조. 내부 종목과 공시 법인 연결 |
| SEC | EDGAR Archives | 원문 링크 생성에 사용, 본문 일괄 다운로드는 제외 |
| SEC | Company Facts·Company Concept·Frames | 제외. XBRL 재무 수치 분석 요구 없음 |

DS002~DS006을 호출하지 않는다는 것은 해당 종류 보고서를 목록에서 제외한다는 뜻이 아니다. 기본안은 조회 법인에 연결된 보고서를 폭넓게 제공하고, 특정 공시/SEC Form만 제한하려면 별도의 제품 정책으로 확정한다. 프로토타입의 분기보고서·기업설명회 예시를 두 가지 유형으로 제한하지 않는다.

## 한국 공급 계약

[공시검색 상세 가이드](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019001)를 확인했다. 사용하는 API는 `GET https://opendart.fss.or.kr/api/list.json`이다. crtfc_key를 서버에서 주입하고 corp_code, bgn_de/end_de, sort=date, sort_mth=desc, page_no/page_count를 설정하는 안이다. page_count는 최대 100이며 UI 페이지 크기와 구분한다. 정정 이력 보존을 위해 last_reprt_at=N을 사용한다. 응답의 rcept_no는 안정적 원문 식별자, report_nm은 제목, rcept_dt는 **날짜**다. flr_nm은 제출인으로 source와 구분하고, rm과 보고서명의 정정·철회 표기를 보존한다. 목록 API에는 본문 요약이 없다.

[고유번호 상세 가이드](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019018)의 `GET https://opendart.fss.or.kr/api/corpCode.xml`은 이름과 달리 ZIP 바이너리 안에 XML을 제공한다. corp_code(8자리)와 stock_code(6자리)는 문자열로 보존한다. 요청마다 전체 파일을 내려받지 않고 별도 갱신·저장한다. ZIP 크기·압축 해제 크기를 제한하고 XML 외부 엔티티를 차단한다. 빈 stock_code·중복 후보는 자동 매칭하지 않는다.

공시 원문 URL은 `https://dart.fss.or.kr/dsaf001/main.do?rcpNo={rcept_no}`다. 인증키가 포함된 API 요청 URL을 화면 원문 링크로 제공하지 않는다. [원본파일 API](https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS001&apiId=2019003)는 ZIP 다운로드 기능이며 요약 API가 아니다.

HTTP 성공과 별도로 body.status를 판정한다. 000 정상, 013 조회 없음, 020 호출 제한, 010/011/012/901 인증·접근 문제, 100/101 요청 문제, 800/900 공급 장애를 구분한다. 공식 안내의 일반적 20,000회 제한을 계정별 확정 한도로 간주하지 않고 실제 설정을 확인한다. 원본파일용 014를 목록 정상 0건으로 통합하지 않는다.

## 미국 공급 계약

[EDGAR API 가이드](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)에 따라 `GET https://data.sec.gov/submissions/CIK{10자리CIK}.json`을 사용한다. 인증키는 필요 없다. 최근 이력뿐 아니라 응답이 지시하는 추가 이력 파일도 조회 기간에 필요한 경우 읽는다. recent만으로 과거 전체가 조회됐다고 표시하지 않는다. 브라우저 직접 호출 대신 백엔드에서 접근한다.

매핑은 [SEC 데이터 접근 가이드](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)에 연결된 `https://www.sec.gov/files/company_tickers_exchange.json`을 활용하는 안이다. 거래소·티커·법인 확인 후 내부 stockId에 CIK를 저장한다. 이 파일도 정확성·전체 범위를 보장하지 않으므로 누락과 충돌을 관리한다.

### 응답 필드 매핑 검토안

공식 개요는 열 중심 배열과 추가 이력 파일 구조를 설명한다. 이번 확인에서는 실제 Submissions JSON 예제 호출이 도구에서 실패했다. 아래 필드의 실제 타입·null·시간대 표기는 구현 전 공식 응답 fixture로 확인하는 계약 검토 항목이다. 확인 실패를 API 자체 이용 불가로 해석하지 않는다.

| 필드 후보 | 용도·주의 |
| --- | --- |
| `filings.recent.accessionNumber` | 원문 식별자. 문자열 그대로 보존 |
| `form`, `primaryDocDescription` | 보고서 유형·제목 후보. 설명 없으면 기업명+Form으로 표시용 제목 구성 |
| `filingDate` | 접수일. 시각 또는 회계기간 아님 |
| `acceptanceDateTime` | 유효 시 접수 시각으로 사용. 명시된 오프셋을 파싱하고 원문 표기 보존 |
| `reportDate` | 보고 대상 기간. 접수일·발표 시각을 대체하지 않음 |
| `primaryDocument` | 원문 경로 생성에 사용 |
| `filings.files` | 추가 이력 파일 안내. 기간에 필요한 파일만 조회, 실제 구조 별도 검증 |

각 배열의 같은 인덱스가 한 보고서임을 검증하고 길이 불일치·필수값 누락은 품질 오류로 처리한다. 과거 파일이 recent와 동일한 중첩 구조라고 가정하지 않는다. description은 보고서 라벨일 수 있으므로 본문 요약으로 사용하지 않는다.

원문 경로 제안은 `https://www.sec.gov/Archives/edgar/data/{CIK의 선행0 제거}/{하이픈 제거 accession}/{primaryDocument}`다. Archives의 CIK는 조회한 법인 매핑에서 얻으며 accession 앞자리에서 추정하지 않는다. primaryDocument가 없으면 공식 filing index를 원문 목록 링크로 구분해 제공하는 안이다. 공식 경로 형식은 [SEC Archives 안내](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)를 따른다. 외부 응답의 경로를 검증 없이 임의 호스트 요청에 사용하지 않는다.

[SEC 개발자 안내](https://www.sec.gov/about/developer-resources)의 전체 요청 상한은 사용자 기준 초당 10건이며 여러 서버에 나눠도 합산한다. 서비스명과 실제 운영 연락처를 포함하는 User-Agent를 설정한다. 연락처는 사용자에게 추후 받아 설정하며 가짜 값을 만들지 않는다. API 키가 없다는 것은 무제한 호출이라는 뜻이 아니다. 재시도·매핑 파일·이력 파일 요청도 호출 예산에 포함한다.

## 법인 연결과 중복

- DISC-01 확정: 선택 종목과 연결된 공시만 반환한다. 내부 stockId와 공급자 법인 ID를 구분한다.
- 제안: KR은 종목코드→corp_code, US는 거래소·티커→CIK로 연결한다. 이름 유사도만으로 자동 확정하지 않는다.
- 하나의 법인에 여러 주식 종류·상장 종목이 연결될 수 있다. 문서는 법인·공급자 기준으로 한 번 저장하고 검증된 stockIds로 연결하는 안이다. 우선주·ADR·티커 변경과 매핑 유효 기간은 확인 필요다.
- 타 기업 공시를 같은 산업이라는 이유만으로 선택 기업 공시 목록에 넣지 않는다. 산업 컨텍스트 자료로 확대할 경우 별도 표시 정책을 먼저 정한다.
- DISC-02 확정: 원문 식별자 보존·중복 제거. 제안 고유키는 DART+rcept_no, SEC+accessionNumber다. 여러 기업 연관은 별도 연결로 보존한다.
- DISC-03 제안: 정정·수정 제출은 원 공시와 별도 ID로 보존한다. DART 정정 표기·rm과 SEC /A를 유지하고 제목만으로 이전 보고서를 덮어쓰지 않는다. 원본-정정 관계는 검증된 근거가 있을 때만 연결한다.

## 날짜·시간창과 최신순

DISC-04 확정 요구는 기준 시간창과 최신순이며 정확한 시각을 제공할 수 없는 경우 별도 계약이 필요하다.

| 데이터 | 제안 표현 | 금지 사항 |
| --- | --- | --- |
| DART 접수일만 존재 | filedDate, datePrecision=DATE, publishedAt=null, timeBasis=RECEIPT_DATE | 00:00·장 마감 시각을 실제 발표 시각처럼 생성 |
| SEC 검증된 접수 시각 존재 | publishedAt UTC, datePrecision=SECOND, timeBasis=ACCEPTANCE_TIME | 원문 최초 게시·일반 대중 공개 시각이라고 단정 |
| SEC 시각 없음·검증 실패 | 유효 접수일만 DATE 정밀도로 제공하거나 품질 오류 | filingDate/reportDate를 정확한 시각으로 대체 |

기획의 핵심 자료 시간창은 직전 거래일 정규장 마감 이후~기준 시각이다. 종목 상세 공유는 제안이다. SEC 시각 기반 자료는 `from < publishedAt <= to`로 검증한다. 시장 시간대는 Asia/Seoul, America/New_York이며 휴장·DST·조기폐장을 시장 캘린더로 처리한다.

DART는 직전 거래일의 장 마감 전후 공시를 날짜만으로 구별할 수 없다. **권장 검토안:** 같은 from/to에 걸치는 한국 접수 날짜 전체를 포함하되 `windowPrecision=DATE_EXPANDED`로 구분하고 “접수일 기준” 안내를 표시한다. 예: 전일 15:30부터라면 전일 접수 공시도 포함될 수 있다. 날짜 시작의 임의 시각으로 경계를 걸러 누락시키지 않는다. 정확한 장 마감 이후 필터가 필수라면 신뢰할 수 있는 시각 공급원 추가가 선행돼야 한다.

날짜 확장 결과는 정확한 시간창 근거로 변동 배경에 자동 투입하지 않는다. 후속 요약은 정밀도 정책을 별도로 확인한다. 정렬은 DATE 자료의 경우 접수일 내림차순+원문 ID 고정 순서다. 같은 날짜의 ID 순서는 실제 시간순 보장이 아니다. 시각 자료는 접수 시각 내림차순+ID로 정렬한다. 서로 다른 정밀도 자료의 화면 순서를 구현 전 확정한다.

## 요약·유형·출처 표시

- DISC-05 확정: 카드에 제목·공시 구분·출처·접수 날짜/시각·원문·요약을 요구한다. DART/SEC 출처와 제출인·발행 법인명을 별도 필드로 구분한다.
- 목록 API만으로 **보고서 본문 요약은 충족되지 않는다.** 권장 1차안은 summary=null, summaryStatus=UNAVAILABLE로 제공하고 요약 영역을 숨긴다. 이는 기획 변경 제안이며 최종 요약 완료로 기록하지 않는다.
- 유형 설명(예: 연차보고서)이나 보고 대상 기간은 보조 라벨로 표시할 수 있으나 해당 기업 실적·사건 요약으로 위장하지 않는다. AI 요약·본문 다운로드는 별도 승인 범위다.
- DISC-06 확정: “공시는 함께 확인된 맥락이며 가격 변동의 원인으로 단정하지 않습니다” 안내와 원문 이동을 제공한다.
- 공통 type은 DISCLOSURE다. 공급자 form/reportName과 기존 AnnouncementType(EARNINGS 등)을 혼용하지 않는다. 단순 Form=8-K를 전부 실적 발표로 분류하지 않는다.
- SEC 유형 제한은 미정이다. 주요 기업 보고서만 선정한다면 10-K/10-Q/8-K와 외국기업 20-F/40-F/6-K 및 수정본 등을 검토하되, 이 예시를 확정 allowlist로 적용하지 않는다. 기업 submissions 조회를 모든 제3자 지분 신고를 포괄하는 기능이라고 약속하지 않는다.

## 서비스 API 계약 제안

`GET /api/v1/stocks/{stockId}/evidence?type=DISCLOSURE&from=...&to=...`

[뉴스 명세](SPEC-stock-news.md)의 공유 evidence 경로와 조율하며 컨트롤러를 중복 등록하지 않는다. stockId는 양의 정수, market은 내부 종목에서 결정한다. from/to는 둘 다 생략하거나 오프셋 포함 시각으로 함께 전달한다. 역전·동일 시각·잘못된 타입은 400 제안이다. 기본창·미래 to 제한·최대 기간은 공통 계약에서 확정한다.

성공/오류 봉투는 [공통 응답](SPEC-api-response.md)을 따른다. 아래는 새 필드 제안이다.

| 필드 | 의미 |
| --- | --- |
| stockId, market, currency, timezone | 종목 컨텍스트. KR/KRW 또는 US/USD, 통화가 공시 내 모든 숫자의 통화를 뜻하지 않음 |
| asOf, source, dataStatus, delayMinutes | 실제 수집 기준 시각, DART/SEC, fresh/stale, 미확인 공급 지연은 null |
| comparisonBase | 가격 비교 기준 메타데이터. 공시 접수일·보고 기간과 분리, 형식 조율 필요 |
| window.from/to, windowPrecision | 요청 범위와 시각/날짜 확장 적용 여부 |
| coverageStatus, coverageReason | 정상 수집/일부 수집/매핑 미지원 구분. 제한·파싱 누락을 숨기지 않음 |
| items, total, nextCursor | 저장된 결과 스냅샷 목록·건수·다음 페이지 제안 |
| items[].documentId, providerDocumentId | 안정적 내부 ID와 rcept_no/accessionNumber 문자열 |
| items[].type, provider, issuerId | DISCLOSURE, DART/SEC, corp_code/CIK |
| items[].title, source, filerName, formType | 제목·공급 출처·제출인·원래 보고서 유형(미확인 null) |
| items[].filedDate, publishedAt, datePrecision, timeBasis | 날짜·nullable 시각·정밀도·시각 의미 |
| items[].summary, summaryStatus | nullable 요약과 제공 여부 |
| items[].url, linkKind | 공식 원문/공시 뷰어/문서 목록 링크 구분 |
| items[].stockIds, industryIds | 검증된 종목·산업 연결, 미매핑 산업은 [] |
| items[].amendmentFlag, withdrawalFlag | 확인된 정정·철회 상태. 알 수 없음과 false를 구분 |

뉴스와 공통인 publishedAt의 null 허용·정밀도 필드 추가는 프론트와 계약 변경으로 다룬다. 이 명세가 뉴스의 기존 계약을 자동 변경하지 않는다. 공급자 페이지를 그대로 내부 페이지로 쓰지 않고 저장 스냅샷에 고정된 순서를 적용하는 안이다. total은 그 스냅샷의 필터·중복 제거 후 건수이며 전체 시장 공시 총계가 아니다.

## 수집·캐시·오류

- DISC-07 확정: 정상 0건·로딩·실패·결측·오래된 자료를 구분한다. 예시 보고서를 만들어 빈 목록을 채우지 않는다.
- DISC-08 제안: DART는 페이지를 필요한 만큼 순회하고 SEC는 조회창에 필요한 추가 이력을 조회한다. 페이지·파일 상한 또는 중간 실패는 PARTIAL과 사유로 남긴다. recent만 읽고 전체 조회 성공으로 처리하지 않는다.
- DISC-09 제안: 기존 Redis 재사용은 허용된다. 공시 전용 공급자·법인·기간·정책 버전 키를 사용하고 법인 매핑 캐시와 목록 캐시를 구분한다. 뉴스·시세 TTL을 복사하지 않는다.
- 요청 시 수집/배치, 캐시 TTL, 최대 stale, 페이지/파일 예산, 타임아웃·재시도는 확인 필요다. 캐시 갱신 때 asOf를 실제 수집 시각으로 유지하고 미스 동시 요청을 합친다.
- SEC는 서비스 전체 요청률을 제한한다. 403은 차단·접근 정책 문제 가능성을 확인하고 우회·즉시 반복하지 않는다. 429·5xx·타임아웃은 제한된 backoff, Retry-After가 있으면 존중한다.
- DART 키는 DART_API_KEY 같은 서버 비밀 설정으로 주입하는 안이다. URL 쿼리에 실리므로 요청 로그·오류·추적 정보에서 crtfc_key를 마스킹한다. 키를 채팅이나 문서에 붙여넣을 필요가 없다.
- SEC는 SEC_USER_AGENT 같은 운영 설정만 필요하며 API 키 필드를 만들지 않는다. 실제 연락처와 기본 User-Agent가 운영 전에 준비돼야 한다.

| 상황 | 처리 제안 |
| --- | --- |
| 잘못된 사용자 ID·기간·type | 400/P001 |
| 내부 미등록 종목 | 404/P002 |
| 법인 ID 매핑 없음·충돌 | 매핑 미지원 상태, 정상 공시 0건과 구분 |
| 정상 범위 조회·DART 013·SEC 유효 빈 이력 | 200, items=[], total=0 |
| DART 인증/IP 오류·SEC 접근 차단 | 공급자 접근 오류, 원인 서버 기록·사용자용 안내 분리 |
| 외부 타임아웃·형식 오류 | 502 계열 공시 오류 제안 |
| 공급자 호출 제한·점검 | 503 계열 제안, 무한 재시도 금지 |
| 일부 페이지·파일 실패 | 확보한 자료 반환 정책 채택 시 PARTIAL과 실제 범위 명시 |
| 내부 저장소 실패 | 500/P006 |

공급자 404를 내부 종목 404로 자동 전파하지 않는다. P007은 시세 오류이므로 재사용 확정 금지다. 신규 ErrorCode와 retryable 표현은 공통 규약에서 정한다. 공시 실패가 뉴스·기본정보 조회 실패를 유발하지 않아야 한다.

## 구현 경계와 시장별 개발 순서 (확정)

사용자 결정: SDD·계획·체크리스트는 공시 문서로 함께 관리하며 구현은 **공통 처리 + 국장 DART 어댑터 + 미장 SEC 어댑터**로 분리한다. 같은 백엔드 프로젝트 안에서 구현하며 시장별 서버나 별도 프로젝트로 나누지 않는다.

| 구분 | 책임 | 예정 위치 |
| --- | --- | --- |
| 공통 | 공시 조회 API·응답 계약, 종목 검증, 공급자 선택, 저장·중복 제거, 캐시·페이지·기간 정책 | `disclosure/`, 기존 공유 evidence API |
| 국장 | DART 인증·요청·응답 해석·오류 변환, corp_code 매핑, 접수일 정규화 | `external/dart/` |
| 미장 | SEC User-Agent·요청·응답 해석·오류 변환, CIK 매핑, 접수일/시각 정규화 | `external/sec/` |

공통 서비스는 `DisclosureProvider`·`IssuerMappingProvider` 계약에 의존하고 각 어댑터는 공통 결과로 변환한다. DART와 SEC 어댑터는 서로 참조하지 않는다. 검증된 종목의 시장으로 공급자를 선택하며, 한국 종목은 DART만, 미국 종목은 SEC만 호출한다. 공급자 장애·미매핑을 다른 시장 공급자로 대체하지 않는다.

공통 기간 정책은 시장 캘린더와 정밀도를 입력받는다. 공급자 원본 파싱·인증은 어댑터에 두고, 호출 제어는 공통 장치를 사용하되 제한·예산·설정은 공급자별로 격리한다. 저장 고유키·캐시 키에도 공급자 구분을 유지한다. 응답 형식은 공통으로 유지하되 날짜 정밀도 차이를 보존한다.

개발 순서: **공통 규격 확정 → DART 연동과 공통 조회 경로 연결 → 국장 API·DB·Redis 검증 → SEC 연동 추가 → 양 시장 통합·회귀 검증**. 국장 검증은 SEC 실연동·운영 연락처 준비와 독립적으로 수행한다. 다만 공통 계약은 처음부터 양 시장의 필드 차이를 고려한다. 국장 단계 통과는 전체 공시 기능 완료나 운영 배포를 뜻하지 않는다.

경계 인수 기준:
- 국장 요청에서 SEC 호출 0회, 미장 요청에서 DART 호출 0회이며 미지원 시장은 외부 호출 전에 오류 처리한다.
- 한 공급자의 오류·호출 예산 소진·캐시가 다른 공급자의 결과와 섞이지 않는다.
- SEC를 추가해도 기존 국장 API 계약·날짜 정밀도·중복 제거 결과가 유지된다.
- 시장별 어댑터 테스트와 공통 서비스 테스트를 분리하고 양 시장 통합 테스트를 수행한다.

이 결정은 구현 구조와 순서에 대한 확정이다. 아래의 요약 유예·날짜 확장·운영 수치 등 미해결 정책을 함께 승인한 것으로 해석하지 않는다.

## 데이터와 프로젝트 구조

현재 Announcements에는 announcementId·stockId·name·type·announcedAt만 있다. 공급자·법인 ID·원문 ID·URL·요약·날짜 정밀도·수집 상태가 없다. 특히 announcedAt NOT NULL LocalDateTime은 DART의 날짜만 있는 자료를 정확하게 표현하지 못한다.

제안: 문서와 종목 연결을 분리하고 공급자+원문 ID 유니크 제약을 둔다. 접수일은 LocalDate, 확인된 접수 시각은 nullable Instant, 시각 의미·정밀도는 별도 값으로 보존한다. DART 원본 접수일을 UTC 자정으로 저장해 시각이 있는 것처럼 만들지 않는다. 기존 시각 데이터의 해석·마이그레이션은 스키마 설계에서 확정한다. 정정 재수집은 같은 문서의 상태를 갱신하되 다른 원문 ID를 삭제하지 않는다.

Java 26 / Spring Boot 4.1.1 / MySQL / 기존 Redis를 따른다. 배치 제안:

```text
disclosure/                 문서·법인 연결·정밀도·정규화 규칙
disclosure/service/         조회·저장·공급자 포트
disclosure/repository/      문서·매핑·연결 저장
external/dart/              인증·ZIP/XML·공시검색 어댑터
external/sec/               User-Agent·Submissions 어댑터
stock/evidence/controller/  뉴스와 공유할 API 소유 위치
```

모두 `src/main/java/com/swyp/ploutos/` 아래 제안 경로이며 테스트는 대응하는 `src/test/java/com/swyp/ploutos/`에 둔다. CLAUDE.md의 생성자 주입·guard clause·setter 금지·sealed interface + record DTO를 따른다. 예: `record FilingTime(LocalDate filedDate, Instant acceptedAt, TimePrecision precision) {}`로 없는 시각과 날짜를 구분한다. 실제 API는 구현 시 Swagger에 반영한다.

## 테스트 전략과 성공 기준

JUnit 6 단위 테스트에 매핑·중복·날짜 정책, HTTP 스텁 테스트에 공급자 계약, MVC 테스트에 공통 응답, Testcontainers에 DB·Redis를 배치한다. 실제 DART 키나 SEC 라이브 요청을 자동 테스트에 사용하지 않는다.

| 기준 | Given / When / Then |
| --- | --- |
| DISC-01 | 같은 이름·다른 법인, 복수 종목·같은 법인 → 검증된 매핑만 연결, 미지원 구분 |
| DISC-02 | 재수집·동시 저장·다중 종목 연결 → 문서 ID 안정, 중복 카드 없음 |
| DISC-03 | 원본·정정·철회 표시 → 원문 ID별 보존, 확인된 상태만 표시 |
| DISC-04 | DART 날짜만 존재 → 시각 null, 접수일 표시, 장 마감 이후로 단정하지 않음 |
| DISC-04 | 미국 DST·휴장일·경계 시각 → 올바른 UTC 필터와 시장 표시 |
| DISC-05 | 요약 미제공 → null/UNAVAILABLE, 임의 사건·실적 요약 없음 |
| DISC-06 | 카드 렌더링 → 출처·정밀도 맞는 날짜/시각·공식 원문·비단정 문구 |
| DISC-07 | 0건·미매핑·인증 실패·상한 도달 → 서로 구분된 상태 |
| DISC-08 | DART 다중 페이지·SEC 과거 파일·중간 실패 → 누락 범위와 PARTIAL 표시 |
| DISC-09 | 캐시 히트·동시 미스·Redis 장애 → 호출 제한·asOf·stale 정책 준수 |
| DART 계약 | HTTP 200/013·020·010 → 정상 빈 목록/제한/인증 오류를 구분 |
| DART 파일 | 정상 ZIP/XML·오류 XML·압축 한도 초과 → 안전한 파싱과 명확한 오류 |
| SEC 계약 | CIK 선행0·배열 길이 불일치·시각 null·primaryDocument 누락 → 정~~~~확한 정규화 또는 품질 오류 |
| SEC 원문 | accession 접두 CIK와 법인 CIK가 다름 → 법인 CIK로 경로 생성 |
| 화면 | 공시만 실패해도 뉴스 유지, 360px 이상·키보드 링크 접근 가능 |

## 명령어

아래는 구현 후 검증용이다. 이번 작업에서 구현·테스트 실행을 완료했다는 뜻이 아니다. JDK 26과 통합 테스트용 Docker가 필요하다.

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test --tests '*Disclosure*Test'
.\gradlew.bat test --tests '*Dart*Test'
.\gradlew.bat test --tests '*Sec*Test'
.\gradlew.bat test
.\gradlew.bat build
```

## 경계와 구현 전 결정

- 항상: 원본 기획과 공급자 사실·설계 제안을 구분하고 법인·원문 ID·날짜 정밀도를 보존한다.
- 먼저 결정: 날짜 확장 조회 허용, 요약 1차 미제공 허용, SEC 보고서 범위, 페이지/커서와 호출·캐시 수치, 매핑 갱신·저장 기간·스키마·오류 계약.
- 절대 안 함: 가짜 공시 시각·요약 생성, DART 인증키 노출, SEC 무제한 호출, 불필요한 재무 상세 API 도입, 코드 구현 완료로 표시.

특히 날짜 확장과 summary=null은 기획 차이를 해결하기 위한 **권장안**이다. 사용자가 확정하기 전 전체 기획 충족으로 판정하지 않는다. SEC 실제 응답 fixture 검증도 구현 선행 조건이다. 이번에는 SDD만 작성하며 별도 계획·태스크는 후속 요청 시 작성한다.
