#!/usr/bin/env bash
# KIS 종목정보 파일로 db/seed-stocks-*.sql 네 개를 만든다.
# 명세: docs/SPEC-stock-master.md
#
# 쓰는 법
#   bash db/generate-stock-seeds.sh                # 파일을 새로 받아 생성
#   bash db/generate-stock-seeds.sh <마스터파일디렉터리>   # 받아 둔 파일로 생성
#
# 왜 스크립트와 SQL 을 둘 다 저장소에 두는가
#   SQL 만 두면 "이 오프셋이 왜 167 인가"를 설명할 수 없고, 스크립트만 두면 서버에 무엇이
#   들어갔는지 기록이 남지 않는다. 스크립트는 근거, SQL 은 기록이다.
#
# 운영 코드가 아니다
#   적재는 사람이 한 번 한다. 앱이 이 URL 을 긁지 않는다 — 개발자센터 문서의 엔드포인트가
#   아니라 KIS 예제 코드가 쓰는 정적 경로라서, 바뀌어도 공지가 없다.
#
# 3단으로 나누는 이유
#   국내 파일은 CP949 고정폭이다. 한글이 섞여 있어 바이트 단위로 끊어야 하고, 그래서
#   UTF-8 로 바꾸기 전에 자른다. 문자 단위로 세면 한글 1자가 1자로 줄어 위치가 틀어진다.
#     1단  CP949 바이트로 잘라 TSV      (awk, LC_ALL=C)
#     2단  CP949 → UTF-8               (iconv)
#     3단  이름 필터 · 상장일 검사 · SQL 생성  (awk)
#   주식이 아닌 이름을 3단에서 거른다 — 한글 키워드를 CP949 바이트로 비교하면 깨지기 쉽다.

set -euo pipefail

OUT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="https://new.real.download.dws.co.kr/common/master"
ROWS_PER_INSERT=500

# 주식이 아닌 것. 해외 종목유형 2 는 "주식"이 아니라 "ETF 가 아닌 것"이라 섞여 있다.
# KisMoverRankingProvider.NOT_STOCK 과 같은 규칙이어야 한다 — 조회와 적재가 어긋나면
# 목록에 있는 종목을 우리가 모르거나, 모르는 종목이 목록에 남는다.
NOT_STOCK='워런트|유닛|채권'

# 상장일 오프셋이 코스피와 코스닥에서 5바이트 다르다. 코스피 위치를 코스닥에 쓰면
# 30500000 같은 값이 나오는데 예외도 안 나고 파싱도 성공한다 — 3단에서 전수 검사한다.
#   코스피 288바이트, 상장일 167
#   코스닥 282바이트, 상장일 162

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# ---------------------------------------------------------------------------
# 마스터 파일 준비
# ---------------------------------------------------------------------------
if [ $# -ge 1 ]; then
    src="$1"
    echo "받아 둔 파일을 쓴다: $src"
else
    src="$work/master"
    mkdir -p "$src"
    echo "KIS 종목정보 파일을 받는다"
    for f in kospi_code.mst kosdaq_code.mst nasmst.cod nysmst.cod; do
        curl -fsS --max-time 120 -o "$src/$f.zip" "$BASE_URL/$f.zip"
        (cd "$src" && unzip -oq "$f.zip")
    done
fi

for f in kospi_code.mst kosdaq_code.mst NASMST.COD NYSMST.COD; do
    [ -f "$src/$f" ] || { echo "없는 파일: $src/$f" >&2; exit 1; }
done

# ---------------------------------------------------------------------------
# 1단·2단 — 바이트로 잘라 TSV 로, 그다음 UTF-8 로
#   TSV: ticker \t name \t listed_at(YYYYMMDD 또는 빈칸)
# ---------------------------------------------------------------------------
domestic_tsv() {   # $1 파일, $2 상장일 오프셋
    LC_ALL=C awk -v off="$2" '
        substr($0, 62, 2) != "ST" { next }
        {
            code = substr($0, 1, 9);  sub(/ +$/, "", code)
            name = substr($0, 22, 40); sub(/ +$/, "", name)
            printf "%s\t%s\t%s\n", code, name, substr($0, off, 8)
        }' "$1" | iconv -f CP949 -t UTF-8
}

overseas_tsv() {   # $1 파일
    LC_ALL=C awk -F'\t' '$9 == 2 { printf "%s\t%s\t\n", $5, $7 }' "$1" \
        | iconv -f CP949 -t UTF-8
}

# ---------------------------------------------------------------------------
# 3단 — SQL 생성
# ---------------------------------------------------------------------------
to_sql() {   # $1 시장코드, $2 국가, $3 거래소, $4 상장일을_쓰나, $5 이름필터를_거나
    awk -F'\t' -v code="$1" -v country="$2" -v exchange="$3" -v dated="$4" -v filtered="$5" \
        -v notstock="$NOT_STOCK" -v chunk="$ROWS_PER_INSERT" '
    function q(s) { gsub(/\x27/, "\x27\x27", s); return "\x27" s "\x27" }
    function head() {
        printf "insert into stocks (market_id, ticker, name, img_url, status, exchange,\n"
        printf "                    float_shares, ceo, listed_at, created_at, updated_at)\nvalues\n"
    }
    function foot() {
        printf "as new\non duplicate key update\n"
        printf "    name = new.name, listed_at = new.listed_at, updated_at = now(6);\n\n"
    }
    BEGIN {
        print "-- " code " 종목. db/generate-stock-seeds.sh 가 만든다 — 손으로 고치지 않는다."
        print "-- 명세: docs/SPEC-stock-master.md"
        print "--"
        print "-- 선행: db/ddl-stocks-nullable.sql · db/ddl-markets-code-nyse.sql · db/seed-markets.sql"
        print "-- 여러 번 실행해도 결과가 같다 (on duplicate key update)."
        print ""
        print "-- 이 파일은 UTF-8 이다. 클라이언트가 latin1 로 읽으면 한글이 이중 인코딩돼"
        print "-- 들어간다 — 삼성전자가 4자가 아니라 12자로 저장되고, 눈으로는 구분되지 않는다."
        print "-- 실행 방식에 맡기지 않고 파일이 스스로 선언한다."
        print "set names utf8mb4;"
        print ""
        print "-- market_id 를 숫자로 박지 않는다 — 로컬과 서버의 auto_increment 가 다르다."
        print "-- 시장이 없으면 @market 이 NULL 이 되고, market_id 가 NOT NULL 이라 곧바로 멈춘다."
        printf "set @market = (select market_id from markets\n"
        printf "               where code = %s and country = %s\n", q(code), q(country)
        printf "               order by market_id limit 1);\n\n"
        n = 0; bad = 0; skipped = 0
    }
    {
        ticker = $1; name = $2; d = $3
        if (ticker == "" || name == "") { skipped++; next }
        if (filtered == "yes" && name ~ notstock) { skipped++; next }

        if (dated == "yes") {
            # 전수 검사. 오프셋이 틀리면 여기서 드러난다 — 통과하지 못한 줄은 버리지 않고
            # 세어서 끝에 알린다. 한 건이라도 있으면 이 SQL 을 쓰면 안 된다.
            if (d !~ /^(19|20)[0-9][0-9][0-9][0-9][0-9][0-9]$/) { bad++; next }
            listed = q(substr(d, 1, 4) "-" substr(d, 5, 2) "-" substr(d, 7, 2))
        } else {
            listed = "null"
        }

        if (n % chunk == 0) { if (n > 0) { print ""; foot() } head(); first = 1 }
        printf "%s(@market, %s, %s, null, \x27ACTIVE\x27, %s, null, null, %s, now(6), now(6))",
               (first ? "    " : ",\n    "), q(ticker), q(name), q(exchange), listed
        first = 0
        n++
    }
    END {
        if (n > 0) { printf "\n"; foot() }
        printf "-- 생성: %d행", n
        if (skipped > 0) printf " / 이름으로 제외: %d", skipped
        if (bad > 0)     printf " / 상장일 형식 불량: %d  ← 0 이 아니면 이 파일을 쓰지 않는다", bad
        printf "\n"
        printf "ROWS\t%d\t%d\t%d\n", n, skipped, bad > "/dev/stderr"
    }'
}

# ---------------------------------------------------------------------------
# 실행
# ---------------------------------------------------------------------------
total=0
run() {   # $1 출력이름, $2 시장코드, $3 국가, $4 거래소, $5 상장일, $6 이름필터, $7.. TSV명령
    local name="$1" market="$2" country="$3" exchange="$4" dated="$5" filtered="$6"
    local out="$OUT_DIR/seed-stocks-$name.sql"
    shift 6
    "$@" > "$work/in.tsv"
    to_sql "$market" "$country" "$exchange" "$dated" "$filtered" < "$work/in.tsv" > "$out.tmp" 2> "$work/stats"
    mv "$out.tmp" "$out"
    local n skipped bad
    read -r _ n skipped bad < "$work/stats"
    printf "%-22s %5s행  (제외 %s, 상장일불량 %s)  → %s\n" \
           "$market" "$n" "$skipped" "$bad" "db/seed-stocks-$name.sql"
    if [ "$bad" -ne 0 ]; then
        echo "  ⚠️ 상장일 형식이 틀린 줄이 $bad 건이다. 오프셋을 다시 확인한다." >&2
        exit 1
    fi
    total=$((total + n))
}

run kospi  KOSPI  KR KRX    yes no  domestic_tsv "$src/kospi_code.mst"  167
run kosdaq KOSDAQ KR KRX    yes no  domestic_tsv "$src/kosdaq_code.mst" 162
run nasdaq NASDAQ US NASDAQ no  yes overseas_tsv "$src/NASMST.COD"
run nyse   NYSE   US NYSE   no  yes overseas_tsv "$src/NYSMST.COD"

echo "합계 ${total}행"
