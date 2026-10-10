# 로컬 Oracle 통합 검증

사용자 요청에 따라 Docker Oracle에 전용 테스트 PDB와 가상 테이블을 구성해 실제 `ojdbc11`·HikariCP·KCB DAO SQL을 실행한다. DDL은 소스에서 추정한 테스트용이며 운영 DDL의 복원이 아니다. `src/main`의 SQL·트랜잭션·문자셋 처리는 변경하지 않는다.

## 실행

JDK 21, Maven, Python 3, 실행 중인 로컬 Docker가 필요하다. 기본 대상은 기존 `sms-oracle` 컨테이너이며 로컬에 게시된 Oracle 포트를 사용한다.

```sh
# 매번 임시 PDB를 만들고 실제 Oracle 검증 후 정리
tools/tests/verify-oracle.sh

# 테스트 스키마를 남겨 재실행·점검
tools/tests/verify-oracle.sh --keep

# 정상·호환성 검증만 실행
tools/tests/verify-oracle.sh --mode oracle

# 기존 결함의 업무 기대값을 검증: 현재 코드에서는 실패할 수 있음
tools/tests/verify-oracle.sh --mode oracle-readiness

# 이 도구가 남긴 전용 PDB만 삭제
tools/tests/verify-oracle.sh --cleanup
```

다른 로컬 Oracle 컨테이너는 `--container NAME`으로 지정한다. 유지한 PDB가 있으면 도구가 상태·컨테이너·표식을 검증한 뒤 재사용한다. 기존 업무 PDB나 기존 테이블을 초기화하지 않는다. 동일 프로젝트에서 다른 Maven/테스트 작업을 동시에 실행하지 않는다.

테스트가 실패하면 쉘 종료 코드도 실패로 유지된다. 결과 화면:

- `target/test-runs/oracle/index.html`: 실제 Oracle 정상·호환성 검증
- `target/test-runs/oracle-readiness/index.html`: 장애 처리·SQL 입력 검증
- 같은 디렉터리의 `summary.json`, Surefire XML 및 Maven 로그
- `target/oracle-environment.txt`: Oracle 버전·DB 문자셋

`target/oracle-test-state.json`은 새 테스트 계정의 접속정보를 담는 권한 0600의 로컬 파일이다. Git에서 제외되는 `target`에만 보관하며 보고서·콘솔·커밋에 값을 노출하지 않는다.

## 별도 PDB를 사용하는 이유

기존 KCB SQL은 스키마 이름을 고정해서 사용한다. 다른 계정으로 연결하거나 `CURRENT_SCHEMA`만 바꿔서는 SQL의 명시적 소유자를 바꿀 수 없다. 따라서 전용 `MCERTIT<임의값>` PDB 안에 필요한 스키마를 만들어 원래 SQL을 그대로 실행한다. 기존 업무 PDB의 동일한 이름을 가진 테이블과는 별개다.

가상 객체는 인증 요청·결과 테이블, 부가 결과 테이블, 부서 테이블, 거래 시퀀스, 테스트 소유권 표식이다. 자세한 컬럼 추론은 [스키마 매핑](oracle-schema-map.md)과 [DDL](../src/test/resources/oracle/schema.sql)에 기록했다. 테스트 전용 CHECK 제약으로 두 번째 저장 실패를 유도한다.

## 검증 범위

- 실제 Oracle 드라이버와 Hikari 연결, KCB 거래 시퀀스 및 부서 조회
- KCB 요청·응답 저장 및 별도 물리 연결에서의 한글 조회
- 자동 커밋 상태의 기존 명시적 `commit()` 호환성
- 풀 반환 시 미완료 수동 트랜잭션 rollback과 연결 상태 복구
- 풀 고갈 대기 제한과 반환 후 복구
- 중복 키와 두 번째 SQL 실패 시 실제 Oracle 예외·영속 저장 상태
- 합성 부서 입력의 따옴표가 SQL 구조를 변경하는지에 대한 업무 기대값 검증

정상 시험의 `legacyCharacterization...`은 기존의 잘못된 부분 성공 동작이 유지되는지 기록한다. 그것이 업무상 올바르다는 뜻은 아니다. 같은 경로를 정상 실패로 처리해야 한다는 요구는 별도 `oracle-readiness` 시험에서 확인한다.

## 실행 결과

2026-10-10, 로컬 Docker Oracle **23.26.2.0.0** / `AL32UTF8` / JDK **21.0.11** / `ojdbc11 23.26.3.0.0` / HikariCP **7.0.2**에서 실행했다.

| 구분 | 결과 | 의미 |
|---|---|---|
| `oracle` | **12개 성공, 실패·skip 0개** | 실제 JDBC·Hikari와 합성 스키마에서 정상·호환성 검증 성공 |
| `oracle-readiness` | **2개 실패, skip 0개** | 아래 기존 결함을 실제 Oracle에서 재현 |

1. `updateStep1`의 두 번째 INSERT에 CHECK 제약 위반을 발생시키면 첫 UPDATE는 별도 물리 연결에서도 보이는 상태로 반영되고, DAO가 true를 반환한다.
2. 존재하지 않는 합성 부서코드에 SQL 조건을 붙이면 `selectDeptCode`가 true를 반환한다. 문자열을 SQL에 직접 연결하는 문제가 실제 Oracle에서도 확인되었다.

쉘 전체 실행은 readiness 실패를 숨기지 않고 종료 코드 **1**을 반환했다. 테스트용 PDB 생성·접속·DDL 실행·테스트·자동 삭제를 모두 확인했고, 실행 전후 기존 사용자 PDB 수는 **2개로 동일**했다. 테스트 행은 각 테스트 후 정리되며 기본 실행에서는 새 PDB와 접속 상태파일도 삭제된다.

이 결과로 가짜 JDBC만 사용했던 이전 검증에서 **실제 Oracle 드라이버·SQL·commit·한글 저장에 관한 공백을 일부 해소**했다. 이 Oracle 실행 당시에는 기동 문자셋 실패와 KCB 네트워크 장애 처리 문제가 별도로 남아 있었다. 이후 JVM 전체 EUC-KR 강제를 제거한 결과는 [후속 기동 검증](auth-test-readiness.md#utf-8-기본-jvm-기동-변경)에 기록한다.

이 검증은 현재 로컬 Oracle 버전과 합성 DDL에 대한 증거다. 운영 Oracle 버전·DB 문자셋·BYTE/CHAR 길이·인덱스·권한·트리거·월별 테이블 전환·동시 부하와 동일하다는 보장은 없다. 실제 KCB를 호출하거나 전체 Gateway 흐름을 실행하지 않는다. 서버 기동은 UTF-8 기본 JVM 및 합성 XML을 사용하는 별도 회귀 테스트로 검증한다. 운영 Oracle 19c와 기존 스키마는 유지된다는 사용자 확인이 있으며, 이 실행 결과는 로컬 23 계열에 한정한다.

## 작업 기록

Codex와 DDL/테스트 서브에이전트가 `worktrees/oracle-integration` (`codex/mobilecert-oracle-integration`)에서 작업했다. Qwen 호출은 없다. [작업 계획](../tools/orca/plans/oracle-integration.json)의 Orca ID는 연결 도구 부재로 null이며 실제 Orca Task 생성으로 보고하지 않는다. 이번 사용자 요청이 가상 DDL 생성을 명시적으로 허용하므로 기존 작업 규칙의 DDL 추정 금지와 구분한다.
