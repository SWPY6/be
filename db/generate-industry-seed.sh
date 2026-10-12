#!/usr/bin/env bash
# KIS 종목정보 파일의 업종코드로 db/seed-stock-industries.sql 을 만든다.
# 명세: docs/SPEC-industry.md 「종목 매핑」
#
# 쓰는 법
#   bash db/generate-industry-seed.sh <마스터파일디렉터리>
#   bash db/generate-industry-seed.sh                      # 파일을 새로 받아 생성
#
# 종목을 하나씩 분류하지 않는다
#   업종코드를 우리 9개 산업에 대응시키는 표가 전부다. 종목이 늘어도 표는 그대로다.
#
# stock_id 를 박지 않는다
#   로컬과 서버의 auto_increment 가 다르다. 종목코드와 시장으로 찾아 넣는다 —
#   INSERT ... SELECT 라 어느 환경에서든 같게 동작한다.

set -euo pipefail

OUT="$(cd "$(dirname "$0")" && pwd)/seed-stock-industries.sql"
BASE_URL="https://new.real.download.dws.co.kr/common/master"

# 해외 종목정보 파일의 종목유형 2 에 섞여 있는 비주식. 종목 적재와 같은 규칙이어야 한다
# (db/generate-stock-seeds.sh · KisMoverRankingProvider.NOT_STOCK).
NOT_STOCK='워런트|유닛|채권'

# 운송장비 코드가 자동차·조선·항공을 한 덩어리로 묶는다. 코드로 가를 수 없어 이름으로 뺀다.
# 조선 10 · 항공우주 10 · 철도와 방산 3. 사람이 만든 목록이라 낡는다 — 명세에 적어 뒀다.
NOT_AUTOMOBILE='010140 012450 042660 047810 064350 075580 329180 439260
067390 092460 099320 099410 101930 221840 266690 274090 288180 333430 460930 462350 474170 478340 488900'

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# ---------------------------------------------------------------------------
# 마스터 파일
# ---------------------------------------------------------------------------
if [ $# -ge 1 ]; then
    src="$1"
else
    src="$work/master"
    mkdir -p "$src"
    for f in kospi_code.mst kosdaq_code.mst nasmst.cod nysmst.cod; do
        curl -fsS --max-time 120 -o "$src/$f.zip" "$BASE_URL/$f.zip"
        (cd "$src" && unzip -oq "$f.zip")
    done
fi
for f in kospi_code.mst kosdaq_code.mst NASMST.COD NYSMST.COD; do
    [ -f "$src/$f" ] || { echo "없는 파일: $src/$f" >&2; exit 1; }
done

printf '%s\n' $NOT_AUTOMOBILE | sed '/^$/d' | sort > "$work/not-auto"

# ---------------------------------------------------------------------------
# market \t industry \t ticker 를 모은다
#   국내는 종목코드·업종코드가 모두 ASCII 라 iconv 가 필요 없다.
#   해외는 이름으로 비주식을 걸러야 해서 UTF-8 로 바꾼다.
# ---------------------------------------------------------------------------
domestic() {   # $1 파일, $2 시장
    LC_ALL=C awk -v market="$2" '
        substr($0, 62, 2) != "ST" { next }
        {
            code = substr($0, 1, 9); sub(/ +$/, "", code)
            big = substr($0, 65, 4); mid = substr($0, 69, 4)
            key = big "/" mid
            ind = ""
            # 코스피
            if (key == "0027/0015") ind = "AUTOMOBILE"
            else if (big == "0018")  ind = "CONSTRUCTION"
            else if (big == "0019")  ind = "TRANSPORT"
            else if (big == "0016")  ind = "RETAIL"
            else if (key == "0027/0005") ind = "FOOD_BEVERAGE"
            else if (big == "0020")  ind = "TELECOM"
            else if (key == "0027/0011") ind = "STEEL"
            else if (big == "0017")  ind = "ENERGY"
            else if (key == "0027/0008") ind = "CHEMICAL"
            # 코스닥 — 번호 체계가 다르다
            else if (key == "1009/1030") ind = "AUTOMOBILE"
            else if (big == "1010")  ind = "CONSTRUCTION"
            else if (big == "1013")  ind = "TRANSPORT"
            else if (big == "1011")  ind = "RETAIL"
            else if (key == "1009/1019") ind = "FOOD_BEVERAGE"
            else if (key == "1009/1026") ind = "STEEL"
            else if (key == "1009/1023") ind = "CHEMICAL"
            if (ind == "") next
            printf "%s\t%s\t%s\n", market, ind, code
        }' "$1"
}

overseas() {   # $1 파일, $2 시장
    LC_ALL=C awk -F'\t' -v market="$2" '$9 == 2 { printf "%s\t%s\t%s\t%s\n", market, $20, $5, $7 }' "$1" \
    | iconv -f CP949 -t UTF-8 \
    | awk -F'\t' -v notstock="$NOT_STOCK" '
        $4 ~ notstock { next }
        {
            s = $2; ind = ""
            if (s == "310" || s == "320") ind = "AUTOMOBILE"
            else if (s == "120" || s == "220") ind = "CONSTRUCTION"
            else if (s == "270" || s == "280" || s == "290") ind = "TRANSPORT"
            else if (s == "380" || s == "430") ind = "RETAIL"
            else if (s == "410") ind = "FOOD_BEVERAGE"
            else if (s == "810" || s == "820") ind = "TELECOM"
            else if (s == "130") ind = "STEEL"
            else if (s == "010" || s == "910" || s == "920" || s == "930") ind = "ENERGY"
            else if (s == "110") ind = "CHEMICAL"
            if (ind == "") next
            printf "%s\t%s\t%s\n", $1, ind, $3
        }'
}

export NOT_STOCK
{
    domestic "$src/kospi_code.mst"  KOSPI
    domestic "$src/kosdaq_code.mst" KOSDAQ
    overseas "$src/NASMST.COD"      NASDAQ
    overseas "$src/NYSMST.COD"      NYSE
} > "$work/all.tsv"

# 조선·항공·우주를 자동차에서 뺀다
awk -F'\t' 'NR==FNR { skip[$1]; next } !($2 == "AUTOMOBILE" && ($3 in skip))' \
    "$work/not-auto" "$work/all.tsv" | sort -t"$(printf '\t')" -k1,1 -k2,2 -k3,3 > "$work/mapped.tsv"

# ---------------------------------------------------------------------------
# SQL
# ---------------------------------------------------------------------------
{
    echo "-- 종목↔산업 매핑. db/generate-industry-seed.sh 가 만든다 — 손으로 고치지 않는다."
    echo "-- 명세: docs/SPEC-industry.md 「종목 매핑」"
    echo "--"
    echo "-- 선행: db/seed-industries.sql · db/seed-markets.sql · db/seed-stocks-*.sql"
    echo "-- 여러 번 실행해도 결과가 같다 (not exists 로 막는다)."
    echo "--"
    echo "-- stock_id 도 market_id 도 박지 않는다 — 로컬과 서버의 auto_increment 가 다르다.
-- 세션 변수도 쓰지 않는다. 문장을 따로 실행하면 변수가 NULL 이 되고, 그러면 아무 행도
-- 맞지 않는데 오류도 나지 않는다(조용한 0건). 각 문장이 스스로 시장을 찾는다."
    echo "-- 종목코드와 시장으로 찾아 넣으므로 어느 환경에서든 같게 동작한다."
    echo "set names utf8mb4;"
    echo
    echo
    awk -F'\t' '
        function flush_group() {
            if (n == 0) return
            printf "insert into stock_industries (stock_id, industry_id)\n"
            printf "select s.stock_id, (select industry_id from industries where name = \x27%s\x27)\n", ind
            country = (market == "NASDAQ" || market == "NYSE") ? "US" : "KR"
            printf "from stocks s\nwhere s.market_id = (select market_id from markets\n                        where code = \x27%s\x27 and country = \x27%s\x27 order by market_id limit 1)\n  and s.ticker in (\n", market, country
            for (i = 1; i <= n; i++)
                printf "%s\x27%s\x27%s", (i % 10 == 1 ? "    " : ""), t[i],
                       (i == n ? "\n" : (i % 10 == 0 ? ",\n" : ", "))
            printf "  )\n"
            printf "  and not exists (select 1 from stock_industries si\n"
            printf "                  where si.stock_id = s.stock_id\n"
            printf "                    and si.industry_id = (select industry_id from industries where name = \x27%s\x27));\n\n", ind
            total += n
            printf "%s\t%s\t%d\n", market, ind, n > "/dev/stderr"
            n = 0
        }
        { if ($1 != market || $2 != ind) { flush_group(); market = $1; ind = $2 } t[++n] = $3 }
        END { flush_group(); printf "-- 매핑 %d건\n", total; printf "TOTAL\t%d\n", total > "/dev/stderr" }
    ' "$work/mapped.tsv"
} > "$OUT" 2> "$work/stats"

awk -F'\t' '$1 != "TOTAL" { printf "  %-7s %-14s %5s\n", $1, $2, $3 } $1 == "TOTAL" { t = $2 } END { printf "합계 %s건 → db/seed-stock-industries.sql\n", t }' "$work/stats"
