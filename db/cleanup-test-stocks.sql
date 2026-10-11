-- 테스트 종목 72행과 그 파생 행을 지운다.
-- 명세: docs/SPEC-stock-master.md 「미해결 질문 3」 → 지우기로 정함
--
-- 왜
--   db/seed-test-stocks.sql 이 넣은 임시 데이터다. 그 파일 주석이 이렇게 적어 뒀다 —
--   "실제 데이터가 들어오면 반드시 지운다 — stocks 에 uk_stocks_ticker_market (ticker,
--    market_id) 유일 제약이 있어서 같은 종목을 다시 넣으면 충돌한다."
--   지금이 그 시점이다. 지우지 않으면 삼성전자를 다시 넣으려다 막힌다.
--
--   float_shares · ceo · listed_at 이 가짜 값이라는 점도 같은 파일에 적혀 있다. 남겨 두면
--   실제 데이터와 가짜가 섞인다.
--
-- 외래키가 없다
--   이 스키마에는 FK 제약이 하나도 없다(information_schema.key_column_usage 로 확인).
--   그래서 stocks 를 먼저 지워도 DELETE 가 막히지 않고, 파생 테이블에 고아 행만 조용히
--   남는다. 파생을 먼저 지우고 stocks 를 마지막에 지운다.
--
-- 전체 삭제를 쓰지 않는다
--   DELETE FROM stock_snapshots 처럼 쓰면 서버에 이미 들어온 다른 사람의 실제 데이터까지
--   지운다. 전부 stock_id BETWEEN 9001 AND 9072 로 겨냥한다 — seed-test-stocks.sql 이
--   그 범위를 못 박아 뒀고, 실제 데이터는 auto_increment 로 1 부터 받는다.
--
-- 지우는 것이 아까운 데이터가 있는가
--   stock_daily_prices 는 KIS 에서 받은 진짜 일봉이다. 다만 stock_id 에 묶여 있고, 같은
--   종목이 새 id 로 다시 들어오므로 이어 쓸 수 없다. 차트를 열면 다시 받는다.
--   stock_snapshots · industry_flow_stocks 는 5분마다 다시 만들어진다.

-- ---------------------------------------------------------------------------
-- 0) 실행 전 확인 — 72 가 나와야 한다. 다르면 멈추고 범위를 다시 본다
-- ---------------------------------------------------------------------------
-- SELECT count(*) FROM stocks WHERE stock_id BETWEEN 9001 AND 9072;
--
-- 범위 밖에 실제 데이터가 이미 있는지도 본다 (있으면 그것은 건드리지 않는다)
-- SELECT count(*) FROM stocks WHERE stock_id NOT BETWEEN 9001 AND 9072;

-- ---------------------------------------------------------------------------
-- 1) 파생 행부터 — 순서를 지킨다
-- ---------------------------------------------------------------------------
DELETE FROM industry_flow_stocks WHERE stock_id BETWEEN 9001 AND 9072;
DELETE FROM stock_snapshots      WHERE stock_id BETWEEN 9001 AND 9072;
DELETE FROM stock_daily_prices   WHERE stock_id BETWEEN 9001 AND 9072;
DELETE FROM stock_industries     WHERE stock_id BETWEEN 9001 AND 9072;
DELETE FROM stock_news           WHERE stock_id BETWEEN 9001 AND 9072;
DELETE FROM announcements        WHERE stock_id BETWEEN 9001 AND 9072;
DELETE FROM calendar_events      WHERE stock_id BETWEEN 9001 AND 9072;

-- ---------------------------------------------------------------------------
-- 2) 종목
-- ---------------------------------------------------------------------------
DELETE FROM stocks WHERE stock_id BETWEEN 9001 AND 9072;

-- ---------------------------------------------------------------------------
-- 3) markets 는 지우지 않는다
--      9001 KOSPI 는 실제 종목이 그대로 쓸 시장이다.
--      9002 SP500 은 쓰지 않게 되지만 남겨 둔다 — core_issues · market_daily_briefs ·
--      market_daily_prices 가 market_id 를 들고 있어, 서버에 그 행이 있으면 고아가 된다.
--      비어 있는 시장 한 행은 해롭지 않다.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 4) 실행 후 확인 — 모두 0 이어야 한다
-- ---------------------------------------------------------------------------
-- SELECT "stocks" t, count(*) n FROM stocks WHERE stock_id BETWEEN 9001 AND 9072
-- UNION ALL SELECT "stock_industries",     count(*) FROM stock_industries     WHERE stock_id BETWEEN 9001 AND 9072
-- UNION ALL SELECT "stock_snapshots",      count(*) FROM stock_snapshots      WHERE stock_id BETWEEN 9001 AND 9072
-- UNION ALL SELECT "stock_daily_prices",   count(*) FROM stock_daily_prices   WHERE stock_id BETWEEN 9001 AND 9072
-- UNION ALL SELECT "industry_flow_stocks", count(*) FROM industry_flow_stocks WHERE stock_id BETWEEN 9001 AND 9072;
--
-- 산업 흐름은 다음 갱신(5분)에 다시 만들어진다. 그 전까지 산업별 동향 탭이 빌 수 있다.
