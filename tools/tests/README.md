# KCB JUnit 실행

JUnit과 Maven으로 KCB 전문·설정·업무 분기를 검증한다. 셸은 Linux에서 기본 검증하고 Windows에서는 명시적으로 선택한 경우에만 검증한다. Python, Docker, LG U+ 및 소켓 검증 도구는 제거했다. `tools/tests/`에는 이 안내와 기존 Maven 실행 셸만 둔다.

```sh
# 기본 검증: 실제 DB와 인증 서버를 사용하지 않는다.
bash tools/tests/verify-mobilecert.sh unit

# 기존 개발 DB 검증: 실제 조회·저장 후 이번 테스트 행만 정리한다.
bash tools/tests/verify-mobilecert.sh db -Dmobilecert.test.config=conf/sevice-dev.xml

# 기본 검증과 개발 DB 검증을 함께 실행한다.
bash tools/tests/verify-mobilecert.sh all -Dmobilecert.test.config=conf/sevice-dev.xml

# 기본 검증의 커버리지
bash tools/tests/verify-mobilecert.sh coverage
```

셸은 Maven의 JDK 8/21을 판별하며 `regression`은 `unit`의 별칭이다. 실패 종료 코드는 그대로 반환한다. 결과는 `target/surefire-reports/`, 커버리지는 `target/site/jacoco/`에서 확인한다.

셸 없이 실행하는 명령과 설정 설명은 [개발 검증 안내](../../docs/development-testing.md)를 따른다. Windows와 IntelliJ에서는 Maven의 `test`를 직접 실행하면 Bash 없이 진행할 수 있다. `ShellScriptTest`를 명시적으로 선택하려면 `-Dmobilecert.test.shell=true` 또는 Bash 경로를 지정한다. 셸 검증 자체는 Bash가 필요하고, 임시 가짜 `mvn`/`java`로 인자 전달·종료 코드·문법만 검사한다. 실제 Maven을 재귀 실행하거나 서비스를 기동하지 않는다.
