# KCB JUnit 검증

테스트는 `src/test/java`의 JUnit으로 실행한다. 기본 검증은 KCB 전문, 설정 파싱, 공통 유틸리티와 외부 통신을 대체한 업무 분기를 검사한다. 셸 테스트와 테스트 실행 wrapper는 제거했으며 Windows와 Linux에서 같은 Maven 명령을 사용한다. LG U+, TCP 소켓·서버 기동, Python, Docker 검증은 포함하지 않는다.

## 실행 구분

```text
mvn -Pjava21 test
mvn "-Pjava21,config" test
mvn "-Pjava21,config" -Dmobilecert.test.config=conf/service-prod.xml test
mvn "-Pjava21,db" test
mvn "-Pjava21,all-tests" test
mvn "-Pjava21,coverage" clean verify
```

| 선택 | 검증 범위 | 실제 DB 접속 |
|---|---|---|
| 기본 | 합성 데이터로 KCB 전문·설정·업무 분기 검증 | 없음 |
| `config` | 선택한 로컬 XML의 파싱과 필수 설정 검증 | 없음 |
| `db` | 기존 개발 DB의 조회·저장·테스트 생성 행 정리 | 있음 |
| `all-tests` | 기본 검증·개발 XML 설정 검사·개발 DB 검증 | 있음 |
| `coverage` | 기본 검증과 JaCoCo 보고서 | 없음 |

기본 실행은 `db`와 `config` 태그를 제외하므로 로컬 서비스 XML이나 실제 DB가 없어도 실행할 수 있다. `config` 또는 `db`를 선택하면 필요한 파일이 없거나 검증에 실패한 경우 테스트가 실패한다. 기본 테스트의 성공은 실제 DB 저장을 검증한 결과가 아니다.

JDK 8에서는 `java21` 대신 `java8`을 선택한다. JDK를 바꾸면 먼저 `clean`을 실행한다. 폐쇄망에서는 기존 Maven 설정의 사내 Nexus를 사용하며 외부 의존성 JAR를 별도로 묶어 반입하지 않는다. 로컬 캐시가 이미 완비된 경우에만 `mvn -o -Pjava21 test`로 오프라인 실행할 수 있다.

## IntelliJ 실행

프로젝트를 Maven으로 불러온 뒤 공유 실행 설정에서 필요한 항목을 선택하고 실행한다.

| 공유 실행 설정 | 실행 내용 |
|---|---|
| `KCB 단위 테스트` | 외부 파일·DB 없이 기본 JUnit 실행 |
| `KCB 개발 설정 검사` | `conf/service-dev.xml` 파싱·필수값 검사 |
| `KCB 운영 설정 검사` | `conf/service-prod.xml` 파싱·필수값 검사, 접속 없음 |
| `KCB 개발 DB 테스트` | 기존 개발 DB 조회·저장 검증 |

공유 설정이 보이지 않으면 `Run | Edit Configurations`에서 Maven 설정을 만들고 프로젝트 위치를 `pom.xml`이 있는 루트로 지정한다. Run에는 위 명령에서 `mvn`을 뺀 인자를 입력하고 Maven 실행 JRE는 JDK 21을 선택한다. 개인의 Maven·Nexus·JDK 설치 경로는 기존 IntelliJ 설정을 사용한다. 한글 소스가 깨져 보이면 파일 인코딩을 EUC-KR로 설정한다.

IntelliJ에서 전체 JUnit을 직접 실행하면 Maven의 태그 제외 설정은 적용되지 않는다. 기본 단위 검증에는 공유 Maven 실행 설정을 사용하거나 필요한 테스트 클래스만 실행한다. [IntelliJ Maven 실행 설정](https://www.jetbrains.com/help/idea/run-debug-configuration-maven.html), [JUnit 실행 설정](https://www.jetbrains.com/help/idea/run-debug-configuration-junit.html)

## 개발·운영 설정 선택

기본 경로는 `conf/service-dev.xml`이다. 다른 파일은 `-Dmobilecert.test.config=경로`로 지정한다. `config` 프로파일은 개발·운영 XML 모두 읽을 수 있으며 파싱과 필수값만 확인한다. DB나 KCB에는 연결하지 않는다. 운영 XML은 제공된 실제 파일을 로컬에 준비하며 테스트가 추정해 생성하지 않는다.

`db`와 `all-tests`의 DB 쓰기 검증은 파일명이 `service-dev.xml`인 개발 설정만 허용한다. `service-prod.xml`을 지정하면 JDBC 연결 전에 실패한다. 실제 주소·계정·비밀번호가 담긴 설정은 Git과 보고서에 포함하지 않는다. XML의 EUC-KR 선언과 실제 저장 인코딩을 일치시킨다.

DB 검증은 `DEFAULT_DB` 접속정보와 `MOBILE_CERT` 테이블 접두사를 사용한다. 기존 월별 테이블·시퀀스·부서 정보가 필요하며 새 스키마나 가상 DDL은 만들지 않는다. DAO로 거래번호를 받고 요청·결과를 저장한 뒤 별도 JDBC 조회로 결과를 대조한다. 정리는 이번 테스트의 거래 키와 소유 표식을 확인한 행만 대상으로 한다. 기존 업무 행·부서 정보·테이블 구조는 변경하지 않는다. DAO가 내부에서 커밋하므로 정리 SQL을 명시적으로 실행하며 정리 실패도 테스트 실패다.

결과는 `target/surefire-reports/`, 커버리지는 `target/site/jacoco/`에 남는다. 같은 Worktree에서는 순차 실행한다.

## 검증 범위의 한계

횟수 초과 테스트는 기존 조회 조건인 TREATE_MK='S' 이력을 준비하여 거절 분기를 검사한다. 실제 응답 저장은 기존 코드대로 '9'를 기록하므로, 반복 인증이 횟수 제한으로 이어짐을 입증하는 테스트는 아니다. 이 기존 동작과 결과 갱신 실패를 무시하는 기존 업무 경로는 이번 범위에서 수정하지 않는다.
