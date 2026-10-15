# KCB 개발 검증

테스트 코드는 `src/test/java`의 JUnit으로 통일한다. 기본 실행은 KCB 전문, 설정 파싱, 공통 유틸리티와 외부 통신을 대체한 업무 분기를 검사한다. 셸 검증은 Linux에서 기본 실행하며 Windows에서는 명시적으로 선택한 경우에만 실행한다. LG U+, TCP 소켓·서버 기동, Python, Docker 검증은 제외한다.

## 실행 구분

```text
mvn -Pjava21 test
mvn "-Pjava21,db" -Dmobilecert.test.config=conf/sevice-dev.xml test
mvn "-Pjava21,all-tests" -Dmobilecert.test.config=conf/sevice-dev.xml test
mvn "-Pjava21,coverage" clean verify
```

첫 명령은 `db` 태그를 제외하므로 실제 DB에 접속하지 않는다. `db` 프로파일은 실제 개발 DB 테스트만, `all-tests`는 기본 테스트와 개발 DB 테스트를 함께 실행한다. 개발 DB 설정이 없거나 접속·검증·정리에 실패하면 DB 테스트는 실패한다. 기본 테스트 통과만으로 DB SQL과 실제 데이터 저장을 검증했다고 판단하지 않는다.

JDK 8에서는 `java21` 대신 `java8`을 선택한다. JDK를 바꾸면 먼저 `clean`을 실행한다. 폐쇄망에서는 기존 Maven 설정의 사내 Nexus에서 의존성과 플러그인을 받는다. 외부에서 의존성 JAR를 별도로 묶어 반입할 필요는 없다. Nexus 설정은 단말의 기존 설정을 사용한다. 로컬 캐시가 이미 완비된 경우에만 `mvn -o -Pjava21 test`로 오프라인 실행할 수 있다.

## Windows와 IntelliJ

Windows에서는 `.sh`를 실행할 필요 없이 IntelliJ에서 Maven 테스트를 실행한다. 프로젝트를 Maven으로 불러온 뒤 공유 실행 설정에서 `KCB 단위 테스트`를 선택하고 실행하면 된다. 개발 DB에 접근 가능한 단말에서는 `KCB 개발 DB 테스트`를 선택한다. 설정 파일과 사내 Nexus 설정은 기존 것을 사용한다.

공유 설정이 보이지 않으면 `Run | Edit Configurations`에서 Maven 설정을 만들고 아래 값을 입력한다.

| 항목 | 단위 테스트 | 실제 DB 테스트 |
|---|---|---|
| 프로젝트 위치 | 프로젝트 루트 (`pom.xml` 위치) | 동일 |
| Run | `-Pjava21 test` | `-Pjava21,db test` |
| Maven 실행 JRE | JDK 21 | JDK 21 |

JDK 8 환경은 JRE와 프로파일을 함께 `java8`로 맞춘다. 공유 설정은 개인의 Maven·Nexus·JDK 설치 경로를 저장하지 않고 IntelliJ의 Maven 설정을 상속한다. 한글 소스가 깨져 보이면 소스 파일 인코딩을 EUC-KR로 설정한다.

Windows의 기본 실행에서는 Java 테스트 34개가 실행되고 셸 테스트 10개는 `Skipped`로 표시된다. 이는 Linux 셸을 검증했다는 뜻이 아니다. Linux에서는 별도 옵션 없이 셸 테스트도 실행한다. 이 운영체제 조건은 JUnit 클래스에 있으므로 IntelliJ에서 해당 테스트를 직접 실행해도 동일하게 적용된다.

IntelliJ에서 전체 JUnit을 직접 실행하면 Maven의 `db` 태그 제외 설정은 적용되지 않는다. 기본 단위 검증에는 위 Maven 실행 설정을 사용한다.

Windows에서도 Git Bash로 셸을 확인하려면 Maven의 Run 항목에 다음처럼 실행 파일을 지정한다.

```text
-Pjava21 -Dtest=ShellScriptTest "-Dmobilecert.test.bash=C:/Program Files/Git/bin/bash.exe" test
```

JUnit 실행 설정에서는 같은 `-Dmobilecert.test.bash=...`를 VM options에 넣는다. `-Dmobilecert.test.shell=true` 또는 `MOBILECERT_TEST_BASH` 환경변수로도 Windows 셸 검증을 명시적으로 선택할 수 있다. 명시적으로 선택한 실행이나 Linux 실행에서 Bash가 없으면 실패한다. 실제 Linux의 권한·경로·기동은 배포 환경에서 별도로 확인한다.

## 로컬 개발 설정

기본 설정 경로는 사용자가 지정한 철자 그대로 `conf/sevice-dev.xml`이다. 다른 파일은 `-Dmobilecert.test.config=경로`로 지정한다. 이 파일과 `conf/service-dev.xml`은 Git에서 제외하며, 실제 주소·계정·비밀번호를 소스나 보고서에 복사하지 않는다.

DB 검증은 XML의 `DEFAULT_DB` 접속정보와 `MOBILE_CERT` 테이블 접두사를 사용한다. 테스트가 요구하는 기존 월별 테이블·시퀀스·부서 정보는 개발 DB에 있어야 한다. 새 스키마나 가상 DDL은 만들지 않는다. XML의 EUC-KR 선언과 실제 저장 인코딩을 일치시킨다.

실제 DB 검증에서는 DAO를 통해 거래번호를 받고 요청·결과를 저장한 뒤 별도 JDBC 조회로 결과를 대조한다. 테스트가 생성한 거래 키와 소유 표식을 확인한 행만 정리한다. 기존 업무 행·부서 정보·테이블 구조는 변경하지 않는다. DAO의 명시적 커밋 때문에 테스트 종료 시 단순 rollback만으로 정리하지 않는다. 생성 데이터 정리에 실패하면 테스트도 실패한다.

## 셸 검증

```sh
bash -n tools/tests/verify-mobilecert.sh
bash -n tools/run-mobilecert.sh
bash tools/tests/verify-mobilecert.sh unit
```

`ShellScriptTest`는 임시 디렉터리의 가짜 `mvn`과 `java`를 호출하여 인자, 프로파일 선택, 실패 코드 전달을 확인한다. 실제 서비스나 DB는 실행하지 않는다. Windows에서 셸 검증을 명시적으로 선택하면 Git Bash를 탐색하며, 다른 설치 위치는 `mobilecert.test.bash` 시스템 속성 또는 `MOBILECERT_TEST_BASH` 환경변수로 지정한다.

```text
mvn -Pjava21 "-Dmobilecert.test.bash=C:/Program Files/Git/bin/bash.exe" test
```

Linux 또는 셸 검증을 명시적으로 선택한 환경에서 Bash를 찾지 못하면 셸 테스트는 실패한다. 셸 검증을 수행할 수 없는 Linux 단말에서 Java 테스트만 선택하려면 `mvn -Pjava21 "-Dtest.excludedGroups=db | shell" test`를 사용하고 셸 미검증을 별도로 기록한다.

결과는 `target/surefire-reports/`이며 JaCoCo 결과는 `target/site/jacoco/`다. 각 실행은 같은 Worktree에서 순차 수행한다. 테스트 기본 실행에는 실제 KCB 인증 요청이나 서버 소켓 기동이 없다.

## 검증 범위의 한계

횟수 초과 테스트는 기존 조회 조건인 TREATE_MK='S' 이력을 준비하여 거절 분기를 검사한다. 실제 응답 저장은 기존 코드대로 '9'를 기록하므로, 반복 인증이 횟수 제한으로 이어짐을 입증하는 테스트는 아니다. 이 기존 동작과 결과 갱신 실패를 무시하는 기존 업무 경로는 이번 범위에서 수정하지 않는다.

IntelliJ 실행 항목은 [공식 Maven 실행 설정 안내](https://www.jetbrains.com/help/idea/run-debug-configuration-maven.html)를 따른다. JUnit 직접 실행의 별도 태그·JRE·작업 경로는 [공식 JUnit 실행 설정 안내](https://www.jetbrains.com/help/idea/run-debug-configuration-junit.html)를 참고한다.
