-- stocks 의 ceo · listed_at · float_shares 를 NULL 허용으로 바꾼다.
-- 명세: docs/SPEC-stock-master.md 「선행 조건 ②」
--
-- 왜
--   KIS 종목정보 파일이 이 세 값을 주지 않는다.
--     ceo          어느 출처도 주지 않는다 (DART company.json 은 주지만 국내만이다)
--     listed_at    국내만 있다. 해외 파일에 없다
--     float_shares 파일이 주는 것은 "상장주수"이고 이 필드는 "유동주식수"다 — 다른 개념이라
--                  넣으면 필드 이름이 거짓이 된다
--   지금 72행은 이 값들이 가짜다(db/seed-test-stocks.sql 주석이 그렇게 적어 뒀다).
--   8,227행을 넣으면서 같은 짓을 하면 가짜가 127배로 늘어난다. NULL 은 "모른다"를 정직하게
--   말하고, 나중에 채우는 사람이 WHERE ceo IS NULL 로 찾을 수 있다.
--
-- 순서
--   이 파일은 "넓히기"라 배포보다 먼저 실행해도 안전하다. 기존 코드는 세 값을 항상 넣으므로
--   제약이 느슨해져도 동작이 바뀌지 않는다.
--   단, listed_at 이 NULL 인 행을 넣기 전에 애플리케이션 수정이 배포돼 있어야 한다 —
--   Stocks.listedAfter 에 NULL 가드가 없으면 해외 종목 차트에서 NPE 가 난다.
--
-- 타입을 추측하지 않았다. 로컬 DB 의 SHOW CREATE TABLE stocks 에서 그대로 옮겼다.
--   MODIFY 는 타입을 다시 적어야 하므로, 틀린 타입을 쓰면 컬럼이 조용히 바뀐다
--   (varchar(50) 을 varchar(100) 자리에 쓰면 값이 잘린다).
--   아래 0) 으로 서버 타입이 같은지 먼저 확인한다. 다르면 이 파일을 쓰지 말고 다시 만든다.

-- ---------------------------------------------------------------------------
-- 0) 실행 전 확인 — 세 컬럼의 타입이 아래와 같아야 한다
--      ceo          varchar(50)
--      float_shares bigint
--      listed_at    date
-- ---------------------------------------------------------------------------
-- SHOW CREATE TABLE stocks\G

-- ---------------------------------------------------------------------------
-- 1) 변경 — 한 문장으로 묶어 테이블을 한 번만 다시 쓴다
-- ---------------------------------------------------------------------------
ALTER TABLE stocks
    MODIFY ceo          varchar(50) NULL,
    MODIFY float_shares bigint      NULL,
    MODIFY listed_at    date        NULL;

-- ---------------------------------------------------------------------------
-- 2) 실행 후 확인 — 세 행의 is_nullable 이 모두 YES 여야 한다
-- ---------------------------------------------------------------------------
-- SELECT column_name, column_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_schema = database() AND table_name = 'stocks'
--   AND column_name IN ('ceo', 'float_shares', 'listed_at')
-- ORDER BY column_name;

-- ---------------------------------------------------------------------------
-- 3) 되돌리기 — 세 컬럼에 NULL 인 행이 없을 때만 된다
-- ---------------------------------------------------------------------------
-- ALTER TABLE stocks
--     MODIFY ceo          varchar(50) NOT NULL,
--     MODIFY float_shares bigint      NOT NULL,
--     MODIFY listed_at    date        NOT NULL;
