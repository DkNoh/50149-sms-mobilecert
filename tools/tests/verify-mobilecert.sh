#!/usr/bin/env bash
# Isolated tests only. Startup tests use their own temporary configuration.
# Never reads deployment ../conf or contacts real DB/provider endpoints.
# regression/coverage에는 로그 레벨·합성 민감값 미노출·요청 ID 분리 검증도 포함한다.
# readiness는 기존 장애 처리 결함을 드러내는 검사이며, 실패를 정상으로 간주하면 안 된다.
set -euo pipefail
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
mode=${1:-regression}
if [ "$#" -gt 0 ]; then shift; fi
usage() {
  cat <<'EOF'
Usage: tools/tests/verify-mobilecert.sh [regression|readiness|coverage|legacy-lgu|compare]
  regression  JUnit protocol/startup/JDBC/pool/socket and safe-logging tests
  readiness   Desired failure-handling tests; known production defects FAIL
  coverage    Regression + JaCoCo HTML/XML, including when JUnit fails
  legacy-lgu  Optional unused LG U+ diagnostics; not an active-provider release gate
  compare     Java 8/21 byte comparison; forwards options to compare-runtimes.py
Requirements: JDK 8 or 21, Maven, Python 3. The matching runtime profile is selected.
Use mvn -Pjava8 clean verify or mvn -Pjava21 clean verify when switching runtimes.
Initial Maven dependency downloads may occur.
No deployment configuration, Oracle, KCB or LG U+ endpoints are used.
LG U+ transport checks are excluded from regression/readiness/coverage by default.
Reports: target/test-runs/<mode>/index.html (local browser, no server needed)
Exit: 0 passed, 1 tests/build/coverage failed, 2 invalid arguments or environment.
All gates, including readiness and the approved integration tests, are required
before release. A green regression run alone does not establish readiness.
EOF
}
case "$mode" in
  -h|--help|help) usage; exit 0 ;;
  regression|readiness|coverage|legacy-lgu|compare) ;;
  *) usage >&2; exit 2 ;;
esac
if [ "$mode" != compare ] && [ "$#" -ne 0 ]; then usage >&2; exit 2; fi
for command in python3 mvn; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing command: $command" >&2; exit 2; }
done
cd "$repo_root"
if [ "$mode" = compare ]; then exec python3 "$script_dir/compare-runtimes.py" "$@"; fi
maven_version=$(mvn -version 2>&1)
runtime_profile=$(printf '%s\n' "$maven_version" | python3 -c 'import re,sys; text=sys.stdin.read(); print("java8" if re.search(r"Java version: 1\.8(?:[.,\s])",text) else "java21" if re.search(r"Java version: 21(?:[.,\s])",text) else "unsupported")')
if [ "$runtime_profile" = unsupported ]; then
  echo 'Maven must use JDK 8 or 21. Set JAVA_HOME and PATH, then check mvn -version.' >&2
  exit 2
fi
mkdir -p target
lock=target/.mobilecert-test-lock
if ! mkdir "$lock" 2>/dev/null; then
  echo "Another verification run may be active ($lock)." >&2
  exit 2
fi
trap 'rmdir "$lock"' EXIT
report_dir="target/test-runs/$mode"
mkdir -p "$report_dir"
# These are generated files only. Remove stale XML so an interrupted Maven run
# cannot accidentally reuse a previous PASS report.
python3 - "$report_dir" <<'PY'
from pathlib import Path
import sys
for parent in [Path('target/surefire-reports'), Path(sys.argv[1])]:
    for path in parent.glob('TEST-*.xml'):
        path.unlink()
PY
printf '%s\n' "$maven_version" > "$report_dir/environment.txt"
maven_args=(-B -ntp "-P$runtime_profile")
if [ "$mode" = readiness ]; then maven_args+=(-Preadiness); fi
if [ "$mode" = legacy-lgu ]; then maven_args+=(-Plegacy-lgu); fi
if [ "$mode" = coverage ]; then
  maven_args+=(-Pcoverage)
  rm -f target/jacoco.exec
  python3 - <<'PY'
from pathlib import Path
import shutil
report = Path('target/site/jacoco')
if report.exists():
    shutil.rmtree(report)
PY
fi
set +e
mvn "${maven_args[@]}" test 2>&1 | tee "$report_dir/maven.log"
build_status=${PIPESTATUS[0]}
set -e
coverage_status=0
if [ "$mode" = coverage ]; then
  # report is a separate invocation so assertion failures still yield coverage.
  set +e
  mvn -B -ntp -Pcoverage jacoco:report 2>&1 | tee "$report_dir/coverage.log"
  coverage_status=${PIPESTATUS[0]}
  set -e
  if [ ! -s target/site/jacoco/jacoco.xml ]; then coverage_status=1; fi
fi
status=0
if [ "$build_status" -ne 0 ] || [ "$coverage_status" -ne 0 ]; then status=1; fi
set +e
python3 "$script_dir/test-report.py" "$mode" "$status"
report_status=$?
set -e
if [ "$report_status" -ne 0 ]; then status=1; fi
printf '\nReport: %s/%s/index.html\n' "$repo_root" "$report_dir"
exit "$status"
