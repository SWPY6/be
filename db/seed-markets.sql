-- 시장 3행을 더한다 — KOSDAQ · NASDAQ · NYSE.
-- 명세: docs/SPEC-stock-master.md 「시장은 네 개다 — 파일이 곧 시장」
--
-- 선행 조건
--   db/ddl-markets-code-nyse.sql 을 먼저 실행한다. code enum 에 NYSE 가 없으면 이 파일이
--   "Data truncated for column 'code'" 로 멈춘다.
--
-- 네 시장이 KIS 종목정보 파일 네 개와 1:1 로 대응한다
--   kospi_code.mst  → KOSPI    (KR, KRW)
--   kosdaq_code.mst → KOSDAQ   (KR, KRW)
--   NASMST.COD      → NASDAQ   (US, USD)
--   NYSMST.COD      → NYSE     (US, USD)
--
--   KOSPI 는 이미 있다(db/seed-test-stocks.sql 이 9001 로 넣었다). 실제 종목이 그 행을 그대로
--   쓴다 — 시장 자체는 가짜가 아니다.
--   SP500 은 쓰지 않는다. 지수라서 구성 종목이 나스닥과 뉴욕에 걸쳐 있고, 시장으로 쓰면 한
--   종목이 두 시장에 속해 종목코드로 특정할 수 없게 된다. 이유는 ddl-markets-code-nyse.sql 에
--   적어 뒀다.
--
-- market_id 를 지정하지 않는다
--   auto_increment 에 맡긴다. 뒤이어 실행하는 seed-stocks-*.sql 이 숫자가 아니라 이름으로
--   찾는다 — 그래야 로컬과 서버의 auto_increment 값이 달라도 같게 동작한다.
--
--     set @kosdaq = (select market_id from markets
--                    where code = 'KOSDAQ' and country = 'KR' limit 1);
--
-- 여러 번 실행해도 결과가 같다
--   where not exists 로 막는다. markets 에는 (code, country) 유일 제약이 없어서
--   ON DUPLICATE KEY UPDATE 가 걸리지 않는다 — 그래서 이 방식을 쓴다.

-- ---------------------------------------------------------------------------
-- 0) 실행 전 확인 — code enum 에 NYSE 가 있어야 한다
-- ---------------------------------------------------------------------------
-- SELECT column_type FROM information_schema.columns
-- WHERE table_schema = database() AND table_name = 'markets' AND column_name = 'code';
--
-- 지금 있는 시장도 본다
-- SELECT market_id, code, country, currency FROM markets ORDER BY market_id;

-- ---------------------------------------------------------------------------
-- 1) 코스닥
-- ---------------------------------------------------------------------------
insert into markets (code, country, currency, trading_session, created_at, updated_at)
select 'KOSDAQ', 'KR', 'KRW', 'REGULAR', now(6), now(6)
from dual
where not exists (select 1 from markets where code = 'KOSDAQ' and country = 'KR');

-- ---------------------------------------------------------------------------
-- 2) 나스닥
-- ---------------------------------------------------------------------------
insert into markets (code, country, currency, trading_session, created_at, updated_at)
select 'NASDAQ', 'US', 'USD', 'REGULAR', now(6), now(6)
from dual
where not exists (select 1 from markets where code = 'NASDAQ' and country = 'US');

-- ---------------------------------------------------------------------------
-- 3) 뉴욕
-- ---------------------------------------------------------------------------
insert into markets (code, country, currency, trading_session, created_at, updated_at)
select 'NYSE', 'US', 'USD', 'REGULAR', now(6), now(6)
from dual
where not exists (select 1 from markets where code = 'NYSE' and country = 'US');

-- ---------------------------------------------------------------------------
-- 4) 실행 후 확인 — 다섯 행이 보여야 한다
--      KOSPI(KR) · KOSDAQ(KR) · NASDAQ(US) · NYSE(US) · SP500(US, 쓰지 않음)
--    그리고 SP500 에 속한 종목이 0 이어야 한다
-- ---------------------------------------------------------------------------
-- SELECT m.market_id, m.code, m.country, m.currency, count(s.stock_id) AS stocks
-- FROM markets m LEFT JOIN stocks s ON s.market_id = m.market_id
-- GROUP BY m.market_id, m.code, m.country, m.currency
-- ORDER BY m.market_id;
