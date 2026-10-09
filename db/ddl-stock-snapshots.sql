-- stock_snapshots: 종목별 시세 스냅샷. (RQ-0701~RQ-0707, SPEC-stock-movers.md)
--
-- 운영은 ddl-auto=none 이라 Hibernate 가 스키마를 만들지 않는다. 이 테이블을 쓰는 버전을
-- 배포하기 전에 이 SQL 을 운영 DB 에 직접 실행한다.
--
-- 배포보다 먼저 실행해야 한다(넓히기 → 배포 → 좁히기). 테이블이 없는 채로 배포하면
-- 산업 흐름 갱신기가 스냅샷 저장 단계에서 매 바퀴 실패한다. 산업 평균은 저장된 뒤에
-- 부르므로 흐름·뉴스 API 는 계속 동작하지만, 로그에 오류가 쌓이고 목록 화면은 비어 있다.
--
-- 아래 정의는 손으로 쓴 것이 아니라 테스트 컨텍스트(ddl-auto=create)가 엔티티에서 생성한
-- SHOW CREATE TABLE 결과를 옮긴 것이다. 컬럼명을 손으로 쓰면 네이밍 전략과 어긋나
-- 값이 조용히 null 로 남는다(PLO-41 에서 avg_trading_value_20d 로 잘못 쓴 적이 있다).
--
-- 이번에도 같은 함정이 있다: averageVolume20d 는 average_volume_20d 가 아니라
-- average_volume20d 다. 기본 네이밍 전략은 대문자 앞에만 밑줄을 넣고 숫자 앞에는 넣지 않는다.

-- 1) 적용 전 확인: 테이블이 없어야 한다.
SHOW TABLES LIKE 'stock_snapshots';

-- 2) 생성
CREATE TABLE `stock_snapshots` (
  `change_rate` decimal(10,2) NOT NULL,
  `market_cap` decimal(20,4) DEFAULT NULL,
  `price` decimal(20,4) NOT NULL,
  `trading_value` decimal(20,4) DEFAULT NULL,
  `average_volume20d` bigint DEFAULT NULL,
  `calculated_at` datetime(6) NOT NULL,
  `stock_id` bigint NOT NULL,
  `stock_snapshot_id` bigint NOT NULL AUTO_INCREMENT,
  `volume` bigint NOT NULL,
  PRIMARY KEY (`stock_snapshot_id`),
  UNIQUE KEY `uk_stock_snapshots_stock` (`stock_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3) 적용 후 확인: 컬럼 9개와 stock_id 유니크 제약이 있어야 한다.
SHOW CREATE TABLE stock_snapshots;

-- 4) 배포 후 확인: 산업 흐름이 한 바퀴 돌면 행이 쌓인다.
--    rows_now 가 0 이 아니고 latest 가 방금이면 정상이다.
SELECT COUNT(*) AS rows_now, MAX(calculated_at) AS latest FROM stock_snapshots;
