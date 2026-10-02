-- market_daily_prices 를 시장 지표 일봉(OHLC)용으로 고친다. (RQ-0301, SPEC-market-daily-price.md)
--
-- 운영은 ddl-auto=none 이라 Hibernate 가 스키마를 바꾸지 않는다. 일봉 모듈이 들어간 버전을
-- 배포하기 전에 이 SQL 을 운영 DB 에 직접 실행한다.
--
-- 2026-09-30 운영 DB 확인 결과:
--   테이블이 있고 행은 0건. 컬럼은 market_id, change_rate, close_value, trade_at, PK.
--   유니크 제약은 없었다.
--
-- indicator 는 네이티브 enum 이 아니라 VARCHAR 이다. enum 으로 두면 지표(유가)를 추가할 때마다
-- ALTER TABLE 이 필요하다. 엔티티도 @JdbcTypeCode(SqlTypes.VARCHAR) 로 맞춰 두었다.

-- 1) 적용 전 확인: 0 이어야 한다. 행이 있으면 멈추고 데이터의 출처를 먼저 확인한다.
--    NOT NULL 컬럼 추가는 행이 있으면 실패한다.
SELECT COUNT(*) FROM market_daily_prices;

-- 2) 스키마 변경
ALTER TABLE market_daily_prices
    DROP COLUMN market_id,
    DROP COLUMN change_rate,
    ADD COLUMN indicator VARCHAR(20) NOT NULL,
    ADD COLUMN open_value DECIMAL(20,4) NOT NULL,
    ADD COLUMN high_value DECIMAL(20,4) NOT NULL,
    ADD COLUMN low_value DECIMAL(20,4) NOT NULL,
    ADD CONSTRAINT uk_market_daily_prices_indicator_trade_at UNIQUE (indicator, trade_at);

-- 3) 적용 후 확인: indicator varchar(20), open/high/low_value, 유니크 제약이 있고
--    market_id 와 change_rate 가 없어야 한다.
SHOW CREATE TABLE market_daily_prices;
