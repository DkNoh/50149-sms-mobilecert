#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
java_bin=${JAVA_HOME:+${JAVA_HOME}/bin/}java
if [[ ! -f "$repo_root/target/mobile-cert.jar" || ! -d "$repo_root/target/lib" ]]; then
    echo '선택한 JDK로 먼저 빌드하세요: mvn -Pjava8 clean verify 또는 mvn -Pjava21 clean verify' >&2
    exit 1
fi

# 호출 위치를 유지한다. 실제 서비스 설정은 호출 위치의 ../conf에서 읽는다.
# 전문과 표준 출력의 EUC-KR 설정은 코드에서 처리하며 JVM 기본 문자셋은 유지한다.
# 전달받은 인자는 메인 클래스 앞에 JVM 옵션으로 배치한다.
exec "$java_bin" "$@" \
    -cp "$repo_root/target/mobile-cert.jar:$repo_root/target/lib/*" \
    scbk.cert.server.ServiceLauncher
