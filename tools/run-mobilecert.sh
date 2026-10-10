#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
java_bin=${JAVA_HOME:+${JAVA_HOME}/bin/}java
if [[ ! -f "$repo_root/target/mobile-cert.jar" || ! -d "$repo_root/target/lib" ]]; then
    echo 'Build first with the matching JDK: mvn -Pjava8 clean verify or mvn -Pjava21 clean verify' >&2
    exit 1
fi

# Retain the caller's working directory: deployment config lives at ../conf.
# Protocol and standard-stream encodings are explicitly EUC-KR in the code.
# Leave the JVM default charset unchanged (platform default on Java 8, UTF-8 on 21).
# Arguments before the main class are JVM options, e.g. -Dscbk.cert.server.type.
exec "$java_bin" "$@" \
    -cp "$repo_root/target/mobile-cert.jar:$repo_root/target/lib/*" \
    scbk.cert.server.ServiceLauncher
