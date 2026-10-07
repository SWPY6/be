#!/usr/bin/env bash
#
# KIS 장중 실측 스크립트. 명세의 미해결 질문에 답하기 위한 것이다.
#
#   docs/SPEC-market-chart.md       "미해결 질문"
#   docs/SPEC-market-quote.md       "미해결 질문"
#   docs/SPEC-market-daily-price.md "미해결 질문"
#
# 사용법:
#   scripts/kis-probe-delay.sh kr [--raw]   한국 장중 (평일 09:00~15:30 KST). 호출 5회
#   scripts/kis-probe-delay.sh us [--raw]   미국 장중 (평일 22:30~05:00 KST). 호출 2회
#
# 환경변수 (.env가 있으면 자동으로 불러온다):
#   KIS_APP_KEY, KIS_APP_SECRET   필수
#   KIS_BASE_URL                  기본값은 모의 도메인. 실전은 https://openapi.koreainvestment.com:9443
#   KIS_PROBE_DELAY               호출 간 대기 초. 기본 1
#   KIS_PROBE_INTERVAL            실시간 여부를 보려고 다시 부를 때까지 대기 초. 기본 180
#
# 주의:
#   - 앱키를 AWS 운영 앱과 나눠 쓴다. 이 스크립트의 호출이 운영 한도를 먹는다.
#     모의 도메인은 3초 간격 호출도 EGW00201(한도 초과)에 걸린 적이 있다.
#   - KIS는 토큰 발급을 1분에 1회로 제한한다. 이 스크립트는 실행 1회당 한 번만 받아 재쓴다.
#     1분 안에 두 번 돌리면 발급이 실패한다.
#   - 앱키·앱시크릿·접근토큰은 출력하지 않는다. 요청 헤더도 덤프하지 않는다.

set -euo pipefail

readonly DEFAULT_BASE_URL="https://openapivts.koreainvestment.com:29443"
readonly TOKEN_PATH="/oauth2/tokenP"
readonly DOMESTIC_DAILY_PATH="/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice"
readonly DOMESTIC_DAILY_TR="FHKUP03500100"
readonly DOMESTIC_QUOTE_PATH="/uapi/domestic-stock/v1/quotations/inquire-index-price"
readonly DOMESTIC_QUOTE_TR="FHPUP02100000"
readonly OVERSEAS_PATH="/uapi/overseas-price/v1/quotations/inquire-daily-chartprice"
readonly OVERSEAS_TR="FHKST03030100"

MODE="${1:-}"
RAW="${2:-}"
ACCESS_TOKEN=""

usage() {
    sed -n '3,30p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

fail() {
    echo "오류: $*" >&2
    exit 1
}

require_tools() {
    command -v curl >/dev/null || fail "curl이 필요하다."
    command -v jq >/dev/null || fail "jq가 필요하다. brew install jq"
}

# .env를 불러온다. 내용은 출력하지 않는다.
load_env() {
    if [[ -f .env ]]; then
        set -a
        # shellcheck disable=SC1091
        . ./.env
        set +a
    fi
    : "${KIS_BASE_URL:=$DEFAULT_BASE_URL}"
    : "${KIS_PROBE_DELAY:=1}"
    : "${KIS_PROBE_INTERVAL:=180}"
    [[ -n "${KIS_APP_KEY:-}" ]] || fail "KIS_APP_KEY가 없다. .env를 확인하라."
    [[ -n "${KIS_APP_SECRET:-}" ]] || fail "KIS_APP_SECRET이 없다. .env를 확인하라."
}

# 도메인만 보여 준다. 키는 설정 여부만 알린다.
print_context() {
    local domain
    domain="${KIS_BASE_URL#https://}"
    echo "도메인   ${domain%%:*}  ($( [[ "$KIS_BASE_URL" == *openapivts* ]] && echo 모의 || echo 실전 ))"
    echo "앱키     설정됨"
    echo "앱시크릿 설정됨"
    echo "지금     KST $(TZ=Asia/Seoul date '+%Y-%m-%d (%a) %H:%M')  /  NY $(TZ=America/New_York date '+%Y-%m-%d (%a) %H:%M')"
    echo
}

issue_token() {
    local response
    response=$(curl -sS -X POST "${KIS_BASE_URL}${TOKEN_PATH}" \
        -H 'content-type: application/json' \
        -d "{\"grant_type\":\"client_credentials\",\"appkey\":\"${KIS_APP_KEY}\",\"appsecret\":\"${KIS_APP_SECRET}\"}")
    ACCESS_TOKEN=$(echo "$response" | jq -r '.access_token // empty')
    if [[ -z "$ACCESS_TOKEN" ]]; then
        # 본문에 키는 없지만 혹시 모르니 오류 코드와 메시지만 꺼낸다.
        fail "토큰 발급 실패: $(echo "$response" | jq -rc '{error_code, error_description, msg1} | with_entries(select(.value != null))')"
    fi
    echo "토큰     발급됨"
    echo
}

# $1 path, $2 tr_id, 나머지: 쿼리 파라미터 (key=value)
kis_get() {
    local path="$1" tr_id="$2"
    shift 2
    local query=""
    local pair
    for pair in "$@"; do
        query+="&${pair}"
    done
    sleep "$KIS_PROBE_DELAY"
    curl -sS -G "${KIS_BASE_URL}${path}?${query#&}" \
        -H 'content-type: application/json; charset=utf-8' \
        -H "authorization: Bearer ${ACCESS_TOKEN}" \
        -H "appkey: ${KIS_APP_KEY}" \
        -H "appsecret: ${KIS_APP_SECRET}" \
        -H "tr_id: ${tr_id}" \
        -H 'custtype: P'
}

# 실패 응답이면 멈춘다. 성공이면 본문을 그대로 돌려준다.
check() {
    local body="$1" label="$2"
    local rt_cd
    rt_cd=$(echo "$body" | jq -r '.rt_cd // "?"')
    if [[ "$rt_cd" != "0" ]]; then
        fail "${label} 실패 (rt_cd=${rt_cd}): $(echo "$body" | jq -rc '{msg_cd, msg1} | with_entries(select(.value != null))')"
    fi
    [[ "$RAW" == "--raw" ]] && echo "$body" | jq . >&2
    return 0
}

ymd() {
    TZ="$1" date -v"$2" '+%Y%m%d' 2>/dev/null || TZ="$1" date -d "$2" '+%Y%m%d'
}

probe_kr() {
    local today5 today
    today=$(ymd Asia/Seoul +0d)
    today5=$(ymd Asia/Seoul -7d)

    echo "── 1. 코스피 일봉 (최근 5거래일) ─────────────────────────"
    local index_daily
    index_daily=$(kis_get "$DOMESTIC_DAILY_PATH" "$DOMESTIC_DAILY_TR" \
        "FID_COND_MRKT_DIV_CODE=U" "FID_INPUT_ISCD=0001" \
        "FID_INPUT_DATE_1=${today5}" "FID_INPUT_DATE_2=${today}" "FID_PERIOD_DIV_CODE=D")
    check "$index_daily" "코스피 일봉"
    echo "$index_daily" | jq -r '.output2[:3][] | "  \(.stck_bsop_date)  종가 \(.bstp_nmix_prpr)"'
    local index_latest index_latest_close
    index_latest=$(echo "$index_daily" | jq -r '.output2[0].stck_bsop_date')
    index_latest_close=$(echo "$index_daily" | jq -r '.output2[0].bstp_nmix_prpr')
    if [[ "$index_latest" == "$today" ]]; then
        echo "  판정: 장중에 오늘(${today}) 행을 준다 → 예"
        echo "        (market-daily-price가 저장 전에 버리므로 차트에는 영향이 없다)"
    else
        echo "  판정: 오늘(${today}) 행이 없다 → 아니오. 최신은 ${index_latest}"
    fi
    echo

    echo "── 2. 원/달러 일봉 (최근 5거래일) ────────────────────────"
    local fx_daily
    fx_daily=$(kis_get "$OVERSEAS_PATH" "$OVERSEAS_TR" \
        "FID_COND_MRKT_DIV_CODE=X" "FID_INPUT_ISCD=FX@KRW" \
        "FID_INPUT_DATE_1=${today5}" "FID_INPUT_DATE_2=${today}" "FID_PERIOD_DIV_CODE=D")
    check "$fx_daily" "환율 일봉"
    echo "$fx_daily" | jq -r '.output2[:3][] | "  \(.stck_bsop_date)  종가 \(.ovrs_nmix_prpr)"'
    local fx_latest fx_latest_close
    fx_latest=$(echo "$fx_daily" | jq -r '.output2[0].stck_bsop_date')
    fx_latest_close=$(echo "$fx_daily" | jq -r '.output2[0].ovrs_nmix_prpr')
    if [[ "$fx_latest" == "$index_latest" ]]; then
        echo "  판정: 지수와 같은 날짜까지 와 있다 → 뒤처지지 않는다"
    else
        echo "  판정: 지수 최신 ${index_latest} vs 환율 최신 ${fx_latest} → 환율이 뒤처진다"
        echo "        (SPEC-market-chart.md \"알려진 한계\"의 날짜 어긋남이 지금도 재현된다)"
    fi
    echo

    echo "── 3. 코스피 현재값 (진행봉 조건 2 실측) ─────────────────"
    local index_quote value prdy_vrss previous_close
    index_quote=$(kis_get "$DOMESTIC_QUOTE_PATH" "$DOMESTIC_QUOTE_TR" \
        "FID_COND_MRKT_DIV_CODE=U" "FID_INPUT_ISCD=0001")
    check "$index_quote" "코스피 현재값"
    value=$(echo "$index_quote" | jq -r '.output.bstp_nmix_prpr')
    prdy_vrss=$(echo "$index_quote" | jq -r '.output.bstp_nmix_prdy_vrss')
    previous_close=$(python3 -c "print(f'{float('$value') - float('$prdy_vrss'):.2f}')")
    echo "  현재값 ${value}, 전일대비 ${prdy_vrss} → 전일 종가 ${previous_close}"
    echo "  마지막 확정 봉(${index_latest}) 종가 ${index_latest_close}"
    if python3 -c "import sys; sys.exit(0 if abs(float('$previous_close') - float('$index_latest_close')) < 0.005 else 1)"; then
        echo "  판정: 전일 종가 = 마지막 확정 봉 종가 → 진행 중인 봉을 붙인다"
    else
        echo "  판정: 다르다 → 진행 중인 봉을 붙이지 않는다 (이미 확정된 거래일의 시세다)"
    fi
    echo "  시가 $(echo "$index_quote" | jq -r '.output.bstp_nmix_oprc') (0이면 개장 전으로 본다)"
    echo

    echo "── 4. 원/달러 현재값 ─────────────────────────────────────"
    local fx_quote fx_value fx_previous
    fx_quote=$(kis_get "$OVERSEAS_PATH" "$OVERSEAS_TR" \
        "FID_COND_MRKT_DIV_CODE=X" "FID_INPUT_ISCD=FX@KRW" \
        "FID_INPUT_DATE_1=${today}" "FID_INPUT_DATE_2=${today}" "FID_PERIOD_DIV_CODE=D")
    check "$fx_quote" "환율 현재값"
    fx_value=$(echo "$fx_quote" | jq -r '.output1.ovrs_nmix_prpr')
    fx_previous=$(echo "$fx_quote" | jq -r '.output1.ovrs_nmix_prdy_clpr')
    echo "  현재값 ${fx_value}, 전일 종가 ${fx_previous}"
    echo "  환율 마지막 확정 봉(${fx_latest}) 종가 ${fx_latest_close}"
    if python3 -c "import sys; sys.exit(0 if abs(float('$fx_previous') - float('$fx_latest_close')) < 0.00005 else 1)"; then
        echo "  판정: 전일 종가 = 마지막 확정 봉 종가 → 진행 중인 봉을 붙인다"
        [[ "$fx_latest" != "$index_latest" ]] && \
            echo "        주의: 2번에서 환율 일봉이 뒤처져 있으므로, 붙는 봉의 날짜가 실제 거래일과 어긋난다"
    else
        echo "  판정: 다르다 → 진행 중인 봉을 붙이지 않는다"
    fi
    echo

    echo "── 5. 원/달러 실시간 여부 (${KIS_PROBE_INTERVAL}초 뒤 재호출) ──────────"
    sleep "$KIS_PROBE_INTERVAL"
    local fx_again fx_value2
    fx_again=$(kis_get "$OVERSEAS_PATH" "$OVERSEAS_TR" \
        "FID_COND_MRKT_DIV_CODE=X" "FID_INPUT_ISCD=FX@KRW" \
        "FID_INPUT_DATE_1=${today}" "FID_INPUT_DATE_2=${today}" "FID_PERIOD_DIV_CODE=D")
    check "$fx_again" "환율 현재값 재호출"
    fx_value2=$(echo "$fx_again" | jq -r '.output1.ovrs_nmix_prpr')
    echo "  ${fx_value} → ${fx_value2}"
    if [[ "$fx_value" == "$fx_value2" ]]; then
        echo "  판정: 값이 움직이지 않았다. 지연이거나 호가 변동이 없었다 → 공개 시세와 견줘 확인하라"
    else
        echo "  판정: 값이 움직였다 → 실시간으로 본다"
    fi
}

probe_us() {
    local today
    today=$(ymd America/New_York +0d)

    echo "── 나스닥 실시간 여부 (${KIS_PROBE_INTERVAL}초 간격 2회) ───────"
    local first second value1 value2
    first=$(kis_get "$OVERSEAS_PATH" "$OVERSEAS_TR" \
        "FID_COND_MRKT_DIV_CODE=N" "FID_INPUT_ISCD=COMP" \
        "FID_INPUT_DATE_1=${today}" "FID_INPUT_DATE_2=${today}" "FID_PERIOD_DIV_CODE=D")
    check "$first" "나스닥 현재값"
    value1=$(echo "$first" | jq -r '.output1.ovrs_nmix_prpr')
    echo "  1회차 현재값 ${value1}, 전일 종가 $(echo "$first" | jq -r '.output1.ovrs_nmix_prdy_clpr')"
    echo "  뉴욕 날짜 ${today}, 조회 시각 NY $(TZ=America/New_York date '+%H:%M')"

    sleep "$KIS_PROBE_INTERVAL"
    second=$(kis_get "$OVERSEAS_PATH" "$OVERSEAS_TR" \
        "FID_COND_MRKT_DIV_CODE=N" "FID_INPUT_ISCD=COMP" \
        "FID_INPUT_DATE_1=${today}" "FID_INPUT_DATE_2=${today}" "FID_PERIOD_DIV_CODE=D")
    check "$second" "나스닥 현재값 재호출"
    value2=$(echo "$second" | jq -r '.output1.ovrs_nmix_prpr')
    echo "  2회차 현재값 ${value2}"
    echo
    if [[ "$value1" == "$value2" ]]; then
        echo "  판정: 값이 움직이지 않았다 → 지연 의심. 공개 시세와 견줘 몇 분 뒤처지는지 확인하라"
        echo "        지연이면 카드에 지연 표시 필드가 필요하다 (SPEC-market-summary.md 미해결 질문)"
    else
        echo "  판정: 값이 움직였다 → 실시간으로 본다"
    fi
}

main() {
    case "$MODE" in
        kr|us) ;;
        *) usage ;;
    esac
    require_tools
    load_env
    print_context
    issue_token
    if [[ "$MODE" == "kr" ]]; then
        probe_kr
    else
        probe_us
    fi
    echo
    echo "결과를 명세의 미해결 질문에 적어 닫아라 (docs/SPEC-market-chart.md, SPEC-market-quote.md, SPEC-market-daily-price.md)."
}

main
