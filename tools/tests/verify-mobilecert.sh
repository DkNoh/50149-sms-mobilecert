#!/usr/bin/env bash
# 테스트 선택과 Maven 실행만 담당한다. 결과는 target/surefire-reports에 남는다.
set -euo pipefail
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
mode=${1:-unit}
if [ "$#" -gt 0 ]; then shift; fi
usage() {
  cat <<'HELP'
사용법: bash tools/tests/verify-mobilecert.sh [unit|db|all|coverage] [Maven 인자...]
  unit      KCB 전문, 설정, 공통 유틸리티와 셸 검증 (기본값, DB 접속 없음)
  db        기존 개발 DB를 사용하는 JUnit만 실행
  all       unit과 db를 함께 실행
  coverage  unit을 실행하고 JaCoCo 보고서 생성
  regression은 unit의 이전 이름으로 계속 사용할 수 있다.
필요 환경: Bash, Maven, JDK 8 또는 21
DB 설정: -Dmobilecert.test.config=conf/sevice-dev.xml
결과: target/surefire-reports/ (coverage: target/site/jacoco/)
Maven의 실패 종료 코드를 그대로 반환한다. 인자/환경 오류는 2를 반환한다.
HELP
}
case "$mode" in
  -h|--help|help) usage; exit 0 ;;
  unit|regression|db|all|coverage) ;;
  *) usage >&2; exit 2 ;;
esac
command -v mvn >/dev/null 2>&1 || { echo 'Maven을 찾을 수 없습니다.' >&2; exit 2; }
cd "$repo_root"
maven_version=$(mvn -version 2>&1)
if [[ "$maven_version" =~ Java\ version:\ 1\.8([.,[:space:]]|$) ]]; then
  runtime_profile=java8
elif [[ "$maven_version" =~ Java\ version:\ 21([.,[:space:]]|$) ]]; then
  runtime_profile=java21
else
  echo 'Maven에서 JDK 8 또는 21을 사용하도록 JAVA_HOME과 PATH를 설정하세요.' >&2
  exit 2
fi
profiles=$runtime_profile
goal=test
case "$mode" in
  db) profiles+=,db ;;
  all) profiles+=,all-tests ;;
  coverage) profiles+=,coverage; goal=verify ;;
esac
# 배열을 사용하여 공백이나 셸 기호가 있는 인자도 원문 그대로 전달한다.
maven_args=(-B -ntp "-P$profiles" "$@" "$goal")
exec mvn "${maven_args[@]}"
