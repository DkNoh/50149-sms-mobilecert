#!/usr/bin/env bash
# 개발단말에서 기존 개발 Oracle의 SMS 객체를 읽기 전용으로 점검합니다.
# Docker/PDB/테이블을 생성하거나 삭제하지 않으며, 인증 요청도 보내지 않습니다.
# 사용법과 전체 인증 테스트의 개발 범위: docs/development-testing.md
#
# 보안: 이 쉘에 접속정보를 적거나 export 하지 마세요. JSON 설정은 실행하지
# 않고 데이터로만 읽습니다. 계정/비밀번호는 Python이 터미널에서 숨김 입력받아
# Java의 표준입력 파이프로 전달합니다. 명령행·환경변수·파일에는 저장하지 않습니다.
# bash -x로 호출한 경우에도 이후 입력값이 추적되지 않도록 즉시 해제합니다.
set +x
set -euo pipefail
umask 077
# 비밀번호가 들어 있는 프로세스의 코어 덤프 파일을 만들지 않습니다.
ulimit -c 0

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
command -v python3 >/dev/null 2>&1 || {
  echo 'Python 3 is required.' >&2
  exit 2
}
# -I: PYTHONPATH/PYTHONSTARTUP 등 호출 환경에 의해 임의 모듈이 섞이지 않게 합니다.
# Java 추가 옵션도 Python 실행기에서 전달하지 않습니다.
exec python3 -I "$script_dir/development-oracle.py" "$@"
