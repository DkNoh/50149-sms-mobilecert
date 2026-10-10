# Java 8 / Java 21 선택 빌드

동일 소스를 사내 개발단말에서 선택한 JDK로 빌드한다. 외부에서 만든 JAR를 반입할 필요는 없다. JDK와 Maven, 아래 의존성·플러그인을 제공하는 사내 Maven 저장소가 필요하다.

## 실행 명령

먼저 `JAVA_HOME`을 사용할 JDK 설치 경로로 설정하고 `mvn -version`의 Java version을 확인한다. IDE 설정과 Maven이 사용하는 JDK가 다를 수 있다. Bash, CMD, PowerShell에서 Maven 명령은 동일하다.

```text
# JAVA_HOME이 JDK 8을 가리킬 때
mvn -Pjava8 clean verify

# JAVA_HOME이 JDK 21을 가리킬 때
mvn -Pjava21 clean verify
```

`verify`는 컴파일, 격리 JUnit, JAR 생성과 런타임 의존성 복사를 실행한다. 기존 `readiness` 장애 검증은 별도 프로파일이므로 기본 `verify`의 성공을 배포 승인으로 해석하지 않는다.

| 프로파일 | Maven 실행 JDK | 컴파일 대상 | HikariCP | Oracle JDBC |
|---|---|---|---|---|
| `java8` | 8 | Java 8 / class 52 | 4.0.3 | ojdbc8 23.26.3.0.0 |
| `java21` | 21 | Java 21 / class 65 | 7.0.2 | ojdbc11 23.26.3.0.0 |

Java 8에서는 `java8` 프로파일이 자동 활성화되고, 기본 설정은 Java 21이다. 재현 가능한 작업 기록을 위해 위처럼 프로파일을 명시한다. 두 프로파일을 동시에 지정하거나 JDK와 맞지 않는 프로파일을 선택하면 빌드 초기 단계에서 실패한다. 이 검사를 건너뛰지 않는다.

두 명령의 출력 위치는 모두 `target/mobile-cert.jar`와 `target/lib/`다. 버전을 바꿀 때 반드시 `clean`부터 실행하고, 배포 시 JAR와 **같은 빌드의 lib 전체**를 한 묶음으로 사용한다. 이전 lib 디렉터리에 새 파일을 덧붙이면 Hikari/JDBC 버전이 섞일 수 있다. 두 결과를 보관하려면 다음 `clean` 전에 저장소 밖의 버전별 디렉터리에 각각 복사한다. `target/`은 소스 반입 대상이 아니다.

`tools/run-mobilecert.sh`는 `JAVA_HOME`의 Java를 사용한다. 이 스크립트는 실제 서비스 실행용이므로 개발 환경 설정을 준비한 뒤 해당 JAR와 맞는 JDK에서 실행한다. 빌드 검증 과정에서는 실행하지 않는다.

## 같은 테스트 실행

```sh
# 먼저 선택한 JDK의 clean verify를 완료한 뒤, 같은 JDK에서 순차 실행한다.
tools/tests/verify-mobilecert.sh regression
tools/tests/verify-mobilecert.sh readiness
tools/tests/verify-mobilecert.sh coverage
```

쉘은 Maven의 JDK 8/21을 판별해 해당 프로파일을 선택한다. Bash·Python 3가 필요하다. 쉘 없이 Maven만 사용할 때는 다음처럼 프로파일을 조합한다.

```text
mvn -Pjava8,coverage clean verify
mvn -Pjava8,readiness test

mvn -Pjava21,coverage clean verify
mvn -Pjava21,readiness test
```

`coverage` 결과는 `target/site/jacoco/`, JUnit 결과는 `target/surefire-reports/`에 생성된다. 별도 쉘의 결과 화면은 `target/test-runs/<mode>/index.html`이다. 서로 다른 모드의 결과와 실행한 JDK를 구분하고 같은 checkout에서 동시에 Maven을 실행하지 않는다.

`verify-dev-oracle.sh`와 Docker 전용 `verify-oracle.sh`는 이번 변경 대상이 아니며 **여전히 JDK 21 전용**이다. 개발 DB 사전 점검은 Java 21 프로파일의 ojdbc11을 사용한다. Java 8 서비스의 실제 Oracle/KCB 업무 연동은 별도로 검증해야 한다.

## 호환성 범위

운영 소스 변경은 표준 출력의 `PrintStream`과 라우팅 파일의 reader 생성자를 Java 8에서도 제공하는 API로 바꾼 두 파일이다. 둘 다 명시적인 EUC-KR을 유지한다. 인증 전문·SQL·트랜잭션·풀 설정·소켓 처리 정책은 변경하지 않는다. 테스트의 record·가상 스레드·Java 9 이후 편의 API는 Java 8에서도 실행되는 동등한 테스트 코드로 변경했다.

Java 8 빌드에도 Hikari가 들어간다. 과거 JDBCPOOL 기반 운영 배포본을 복원하는 작업은 아니다. [Hikari 공식 안내](https://github.com/brettwooldridge/HikariCP#artifacts)의 Java 8용 4.0.3은 deprecated 계열이므로 Java 8 호환 빌드의 의존성으로 구분해 관리한다. Oracle JDBC는 [공식 호환성 안내](https://www.oracle.com/database/technologies/faq-jdbc.html)를 함께 확인한다. Compiler Plugin 3.13.0은 JDK 8에서 `release` 값을 source/target으로 처리한다([공식 설명](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)).

원본 Java 8 전문과 현재 두 버전의 비교 명령:

```sh
python3 tools/tests/compare-runtimes.py \
  --java8-home /path/to/jdk8 \
  --java21-home /path/to/jdk21
```

원본 Java 8/EUC-KR, 현재 Java 8/EUC-KR·UTF-8, 현재 Java 21/UTF-8을 비교한다. 운영 설정과 실제 네트워크를 사용하지 않는다. 기존 JDBCPOOL JAR가 없으면 원본 전체 빌드/풀 비교는 SKIP되며, 전문 비교 성공과 별도로 기록된다.

## 검증 기록

2026-10-11, 담당 Codex. Task에 연결된 `87bf/50149-sms-mobilecert` Worktree / `codex/mobilecert-dual-jdk`에서 구현 후 메인에 통합했다. Orca 연결 도구가 없어 실제 Orca Task ID는 없으며 Codex Task 연결과 로컬 계획을 사용했다. Qwen 3.8 Max 호출은 없다.

| 실행 | 결과 |
|---|---|
| 실제 JDK 8u504 + java8/coverage clean verify | 43개 통과, 패키징 성공, class 52 |
| 메인 통합 후 JDK 21.0.11 + java21/coverage clean verify | 43개 통과, 패키징 성공, class 65 |
| 원본/현재 두 JDK·문자셋 4개 구성 | 구성별 전문 13개 검사와 공급자 6개 바이트 사례 일치 |
| 두 JDK의 readiness | 같은 기존 장애 사례 10개 실패, 실패 상태 유지 |
| 개발 DB 점검 도구의 오프라인 시험 | 기존 37개 통과, 실제 DB 접속 없음 |
| 잘못된 JDK·동시 프로파일 | 빌드 초기 거부 확인 |

43개는 기존 회귀 42개와 실제 선택된 Oracle JDBC 클래스를 접속 없이 로드하는 시험 1개다. Java 8은 공급자 SHA-256 검증을 마친 임시 Zulu JDK, Java 21은 설치된 Temurin으로 실행했다. 시스템 기본 JDK는 변경하지 않았다. 원본 JDBCPOOL이 없어 원본 전체 빌드/풀 비교는 SKIP됐으며, 실제 개발 Oracle/KCB 및 사내 실행 환경은 미검증이다.

최종 실행 결과와 미검증 범위는 [작업 기록](../tools/orca/plans/dual-jdk-build.json)에 기록한다. 실제 개발 Oracle/KCB 통신, 사내 OS·JDK 배포판, SonarQube Quality Gate는 이번 로컬 빌드 검증에 포함하지 않는다.
