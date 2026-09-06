#!/usr/bin/env bash
set -uo pipefail
log_file="${RUNNER_TEMP:-/tmp}/vidbox-gradle-$$.log"
./gradlew --continue --no-daemon --console=plain "$@" 2>&1 | tee "$log_file"
status=${PIPESTATUS[0]}
python3 scripts/report-verification.py "$status" "$log_file"
exit "$status"
