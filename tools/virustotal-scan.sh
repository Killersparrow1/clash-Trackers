#!/usr/bin/env bash
# Scans one APK with VirusTotal (API v3) and writes a plain-text report.
#
#   tools/virustotal-scan.sh <apk> <release tag> <report file>
#
# Needs VIRUSTOTAL_API_KEY in the environment (a GitHub secret in CI; never printed). Always writes the report and
# exits 0 when it could write it: the report's SCAN STATUS line says what happened, and only "COMPLETED" with zero
# detections is called clean. An upload that failed, a rate limit that never cleared, a timeout, a missing key or an
# analysis with no engine results is reported as INCOMPLETE, never as clean.
#
# Optional: VT_API_BASE (tests), VT_POLL_SECONDS (default 20), VT_TIMEOUT_SECONDS (default 1200), VT_RETRIES (default 5).
set -u
set +x

apk=${1:?apk path}
tag=${2:?release tag}
report=${3:?report path}

base=${VT_API_BASE:-https://www.virustotal.com/api/v3}
poll=${VT_POLL_SECONDS:-20}
limit=${VT_TIMEOUT_SECONDS:-1200}
retries=${VT_RETRIES:-5}
rate_wait=${VT_RATE_WAIT_SECONDS:-60}
key=${VIRUSTOTAL_API_KEY:-}

version=${tag#CT}
status="INCOMPLETE"
reason=""
detections="n/a"
sha=""
size=""
url=""
stats_line=""
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

write_report() {
  {
    echo "Clash Trackers security report"
    echo "=============================="
    echo "App version : ${version} (release ${tag})"
    echo "File        : $(basename "$apk")"
    echo "File size   : ${size:-unknown} bytes"
    echo "SHA-256     : ${sha:-unknown}"
    echo "Scanned at  : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "Scanner     : VirusTotal (API v3)"
    echo
    echo "SCAN STATUS : ${status}"
    [ -n "$reason" ] && echo "Details     : ${reason}"
    echo "Detections  : ${detections}"
    [ -n "$stats_line" ] && echo "Engine stats: ${stats_line}"
    echo "Report URL  : ${url:-not available}"
    echo
    case "$status" in
      COMPLETED_CLEAN)
        echo "VirusTotal finished the analysis and no engine flagged this file. This is not a guarantee that it is safe." ;;
      COMPLETED_DETECTIONS)
        echo "VirusTotal finished the analysis and some engines flagged this file. Open the report URL to see which ones." ;;
      *)
        echo "The scan did NOT finish. Do not treat this file as scanned or clean. Check the report URL (if any) or run the scan again." ;;
    esac
  } > "$report"
  echo "VirusTotal: ${status} (${detections})${reason:+ - $reason}"
}

# curl with retries on rate limits (429) and server errors. Body goes to $2, the HTTP status is printed.
# Usage: vt METHOD OUTFILE URL [extra curl args...]
vt() {
  local method=$1 out=$2 target=$3; shift 3
  local n=0 code
  while :; do
    code=$(curl -sS -o "$out" -w '%{http_code}' --max-time 900 -X "$method" -H "x-apikey: $key" "$@" "$target" 2>"$work/curl.err") || code=000
    case "$code" in
      2??) echo "$code"; return 0 ;;
      429) n=$((n + 1)); [ "$n" -gt "$retries" ] && { echo "$code"; return 1; }; sleep "$rate_wait" ;;
      000|5??) n=$((n + 1)); [ "$n" -gt "$retries" ] && { echo "$code"; return 1; }; sleep $((n * 10)) ;;
      *) echo "$code"; return 1 ;;
    esac
  done
}

if [ ! -f "$apk" ]; then
  reason="APK not found"; write_report; exit 0
fi
sha=$(sha256sum "$apk" | cut -d' ' -f1)
size=$(stat -c %s "$apk")
url="https://www.virustotal.com/gui/file/${sha}"

if [ -z "$key" ]; then
  reason="VIRUSTOTAL_API_KEY is not set, so nothing was uploaded"; write_report; exit 0
fi

# Files over 32 MB must go to a one-time upload address.
upload_to="$base/files"
if [ "$size" -gt 33554432 ]; then
  code=$(vt GET "$work/upload_url.json" "$base/files/upload_url") || { reason="could not get an upload address (HTTP $code)"; write_report; exit 0; }
  upload_to=$(jq -r '.data // empty' "$work/upload_url.json")
  [ -n "$upload_to" ] || { reason="VirusTotal returned no upload address"; write_report; exit 0; }
fi

code=$(vt POST "$work/upload.json" "$upload_to" --form "file=@${apk}") || { reason="upload failed (HTTP $code)"; write_report; exit 0; }
analysis=$(jq -r '.data.id // empty' "$work/upload.json")
[ -n "$analysis" ] || { reason="upload answered without an analysis id"; write_report; exit 0; }

waited=0
while :; do
  code=$(vt GET "$work/analysis.json" "$base/analyses/$analysis") || { reason="could not read the analysis (HTTP $code)"; write_report; exit 0; }
  state=$(jq -r '.data.attributes.status // empty' "$work/analysis.json")
  if [ "$state" = "completed" ]; then break; fi
  if [ "$waited" -ge "$limit" ]; then
    reason="timed out after ${limit}s while VirusTotal was still analysing (last state: ${state:-unknown})"; write_report; exit 0
  fi
  sleep "$poll"; waited=$((waited + poll))
done

# The analysis must be about the file that was uploaded.
reported=$(jq -r '.meta.file_info.sha256 // empty' "$work/analysis.json")
if [ -n "$reported" ] && [ "$reported" != "$sha" ]; then
  reason="VirusTotal analysed a different file (SHA-256 mismatch)"; write_report; exit 0
fi

read -r malicious suspicious undetected harmless timeout failure unsupported < <(
  jq -r '.data.attributes.stats | [.malicious, .suspicious, .undetected, .harmless, .timeout, .failure, ."type-unsupported"] | map(. // 0) | @tsv' "$work/analysis.json"
)
malicious=${malicious:-0}; suspicious=${suspicious:-0}; undetected=${undetected:-0}; harmless=${harmless:-0}
timeout=${timeout:-0}; failure=${failure:-0}; unsupported=${unsupported:-0}
flagged=$((malicious + suspicious))
engines=$((malicious + suspicious + undetected + harmless))
detections="${flagged} / ${engines}"
stats_line="malicious ${malicious}, suspicious ${suspicious}, undetected ${undetected}, harmless ${harmless}, timeout ${timeout}, failure ${failure}, unsupported ${unsupported}"

if [ "$engines" -eq 0 ]; then
  reason="the analysis finished without any engine results"
elif [ "$flagged" -gt 0 ]; then
  status="COMPLETED_DETECTIONS"
else
  status="COMPLETED_CLEAN"
fi
write_report
exit 0
