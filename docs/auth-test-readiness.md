# 휴대폰 본인인증 테스트 준비 및 정적 검토

**최신 결과: 로그 개선 후 작업 Worktree에서 regression 42/42 통과(기존 22개 + 신규 로그 검증 20개).** 메인 통합 후 검증 결과는 아래 로그 개선 절에 기록한다. JVM 전체 EUC-KR 강제 제거 당시의 22/22, 최초 검토의 19/20 결과는 이전 이력이다.

후속 검증: 사용자 요청으로 전용 로컬 Oracle PDB와 가상 테이블을 생성해 **실제 Oracle 정상 검증 12개 통과, 기존 결함 검증 2개 실패**를 확인했다. [Oracle 통합 검증 결과](oracle-integration.md)를 함께 참조한다. 아래의 Oracle 미검증 표기는 최초 회귀 검토 당시의 범위이며, 운영 Oracle은 기존과 동일한 19c라는 사용자 확인을 반영한다. 로컬 검증은 23 계열이므로 운영과 같은 19c의 새 JDBC/Hikari 조합 및 KCB 실연동은 별도 확인 대상이다.

현재 JDK 21/HikariCP 스냅샷을 대상으로 격리 테스트와 실행용 쉘·로컬 HTML 결과 화면을 추가했다. 최초 테스트 추가 작업에서는 `src/main`을 수정하지 않았으며, 후속 UTF-8 기동 변경은 아래에 따로 기록했다. 기존 작업물에 포함된 운영 소스 변경은 그대로 가져왔으므로 `git diff main` 전체를 이번 작업의 변경으로 해석하면 안 된다.

**현재 LG U+는 사용하지 않는다는 사용자 확인을 반영했다.** 운영 판단은 KCB와 공통 TCP·DB/Hikari 경로를 기준으로 한다. LG U+ 직접 통신 테스트에는 `inactive-lgu` 태그를 붙여 기본 실행에서 제외했으며, 기존 소스 분석 중 LG U+ 전용 항목은 보관 코드의 참고 사항으로 분류한다. 공통 바이트/DAO 호환성 시험에 남아 있는 LG U+ 합성 fixture는 실제 공급자 연동을 의미하지 않는다.

**현재 상태를 배포 가능으로 판정하지 않는다.** 기존 EUC-KR 강제 기동 조건은 아래 후속 변경에서 제거했다. 통신 실패 처리·DB 부분 저장 등 기존 동작에 대한 준비도 검증은 계속 필요하다. 아래 Sonar 규칙은 소스 검토에 대응하는 후보이며, SonarQube 스캔 결과나 사내 프로파일의 예상 건수가 아니다.

## 실행 방법과 결과 화면

Maven이 JDK 21을 사용하도록 `JAVA_HOME`/`PATH`를 설정하고 `mvn -version`으로 확인한다. Maven, Python 3가 필요하며 최초 실행에서는 공개 Maven 의존성을 다운로드할 수 있다. 테스트는 합성 전문·기록용 JDBC 드라이버·루프백 소켓을 사용한다. 기동 테스트는 별도 JVM에서 임시 합성 XML로 실제 런처의 시작·종료를 검증한다. 배포 설정 XML을 읽거나 운영 Oracle/KCB/LG U+에 연결하지 않는다.

```sh
tools/tests/verify-mobilecert.sh regression
tools/tests/verify-mobilecert.sh readiness
tools/tests/verify-mobilecert.sh coverage
```

| 모드 | 확인 범위 | 결과 |
|---|---|---|
| `regression` | 기존 바이트 기준, JDK 기동 조건, Hikari 동작, JDBC/소켓 회귀 | `target/test-runs/regression/index.html`, `summary.json` |
| `readiness` | 장애 시 요구되는 동작을 직접 assert | `target/test-runs/readiness/index.html`, `summary.json` |
| `coverage` | regression 실행 및 JaCoCo 측정 | `target/test-runs/coverage/index.html`, `summary.json`; `target/site/jacoco/index.html`, `jacoco.xml` |
| `legacy-lgu` | 현재 미사용 LG U+ 보관 코드의 선택 진단 | `target/test-runs/legacy-lgu/index.html`, `summary.json`; 운영 KCB 판정과 분리 |
| `compare` | 실제 Java 8 원본과 Java 21 현재 소스의 바이트 비교 | 콘솔 결과; 비교용 임시 파일은 실행 후 삭제 |

HTML 파일은 브라우저에서 직접 연다. 별도 웹 서버는 필요 없다. 쉘은 테스트·빌드 실패를 비정상 종료 코드로 전달하고, coverage 모드는 테스트 실패가 있어도 수집 가능한 JaCoCo 보고서 생성을 시도한다. 기록과 최신 실행 환경은 각 결과 디렉터리에서 확인한다.

`@Tag("readiness")` 테스트는 기본 `mvn test`에서 제외하고 별도 실행한다. **기존 결함이 있으면 의도적으로 실패하며, 이 실패를 skip 또는 정상 성공으로 취급하지 않는다. 배포 승인에는 regression과 readiness, 아래 통합 검증이 모두 필요하다.** 정상 동작을 요구하는 assertion과 기존 동작을 기록하는 회귀 기준을 구별하기 위한 분리다.

기본 `mvn test`와 `mvn -Preadiness test`는 미사용 LG U+ 직접 통신을 제외한다. 보관 코드 진단이 필요할 때만 `tools/tests/verify-mobilecert.sh legacy-lgu` 또는 `mvn -Plegacy-lgu test`를 실행한다. 이 선택 진단의 실패를 운영 KCB 장애로 계산하지 않는다.

실제 두 런타임 비교는 Java 8을 준비한 뒤 실행한다.

```sh
tools/tests/verify-mobilecert.sh compare \
  --java8-home /path/to/jdk8 \
  --java21-home /path/to/jdk21
```

원본 전체 컴파일과 구형 풀의 JDBC 동작 비교에는 실제 `jdbcpool 0.99` JAR를 `--legacy-pool-jar`로 지정해야 한다. 공급받지 않은 JAR·DDL·운영 전문을 만들어 검증한 것처럼 취급하지 않는다. 추가 설명은 [테스트 안내](../tools/tests/README.md), 기존 전환 결정은 [JDK 21/Hikari 전환 문서](jdk21-hikari-selective.md)를 참조한다.

## 로그 개선 후속 검증

인증 전문의 EUC-KR 변환·길이, SQL·트랜잭션·반환값, 풀·스레드 구조를 보존하고 애플리케이션 로그를 수정했다. 정상 상세는 DEBUG, 응답 전송 완료는 INFO, 수신 타임아웃·입력 I/O 오류는 WARN, 시스템·DB·공급자 통신 예외는 ERROR다. 인증 실패/성공을 판단하는 새 로직은 넣지 않았다. 실제 이벤트·제약·배포 로그 설정 확인은 [로그 변경 기록](logging-improvement.md)을 참조한다.

신규 JUnit 20개는 공통 로그 8개, 실제 KCB 통신 클래스 5개, 서버 수신 경계 5개, 실제 DAO/DBFactory 오류 경로 2개다. 원문 전문·CI/DI·인증정보·SQL·예외 메시지 미노출, 정상 송수신 바이트 보존, 8개 스레드에서 64개 요청 ID 구분과 정리, 기존 오류 반환/예외 보존을 검사한다. 통신/DAO 데이터는 합성이며 실계정이나 실제 인증 요청을 사용하지 않는다.

작업 Worktree `safe-logging`의 `tools/tests/verify-mobilecert.sh regression`: **42 PASS, 0 FAIL, 0 SKIP**. 메인 통합 후 `coverage`에서도 **42 PASS, 0 FAIL, 0 SKIP**를 확인했다. 최신 HTML 결과는 `target/test-runs/coverage/index.html`이며, JaCoCo 줄 커버리지는 **39.6% (1221/3080)**, 분기는 **27.5% (134/488)**다. 자식 JVM 기동은 이 커버리지 집계에 포함되지 않고, 전체 인증 업무 검증률이나 Sonar Quality Gate 통과율을 뜻하지 않는다. `readiness`는 **0 PASS, 10 FAIL, 0 SKIP**로 기존 장애 사례가 그대로 재현됐으며 이를 성공 처리하지 않았다. `mvn -DskipTests package`도 성공했다. ServiceLauncher를 합성 설정으로 기동·종료하는 JUnit은 위 42개에 포함돼 있다. 이번 로그 변경 후 별도의 실제 실행 쉘 smoke 검사는 실행하지 않았다. 이번 로그 개선은 기존 장애 처리를 고치는 작업이 아니며 회귀 통과로 실제 발송·Oracle 19c 업무 통합이나 운영 부하가 검증된 것은 아니다.

로그 변경 전후를 모두 JDK 21/UTF-8에서 독립 컴파일한 추가 비교에서도 KCB/LGU 합성 6경로의 송신·반환 **6,038바이트가 동일**했고, 전문 기준 13개가 양쪽 모두 통과했다. 실제 Java 8 실행 비교는 아니다. DAO/유틸 7개 파일은 주석과 로그 호출을 제외한 Java 토큰이 동일하며, 전문 bean 8개 파일도 바이트 그대로다. 근거는 메인 `target/logging-audit/`에 보관했다.

작업: Codex 및 검토/테스트 서브에이전트, `worktrees/safe-logging` (`codex/mobilecert-safe-logging`). Orca 도구가 제공되지 않아 로컬 계획의 Task ID는 null이며, Qwen 3.8 Max 호출은 0회다. [변경 범위·검증 기록](../tools/orca/plans/safe-logging.json).

## UTF-8 기본 JVM 기동 변경

사용자 요청으로 `CertCharset.requireRuntimeCharset()`와 `ServiceLauncher`의 두 호출, `tools/run-mobilecert.sh`의 `-Dfile.encoding=EUC-KR`을 제거했다. EUC-KR 전문 바이트 변환, Maven 소스 인코딩, stdout/stderr의 명시적 EUC-KR 출력은 유지한다. Java 8 비교 도구의 후보 실행은 Java 21/UTF-8로 변경했으며, 원본 Java 8/EUC-KR 기준은 유지한다.

macOS / Temurin 21.0.11에서 핵심 전문·출력·기동 테스트 **5개 통과**, 전체 회귀 **22개 통과, 실패·skip 0개**를 확인했다. 기존 20개 중 1개 실패하던 EUC-KR 강제 조건을 제거했고, UTF-8 JVM의 표준 출력 2조건과 설정 파일 문자셋 2조건을 검증한다. 새 기동 테스트는 임시 합성 설정과 명시적 로그 appender를 사용하며 실제 운영 설정의 복원이 아니다. 운영 설정·로그 수집기·19c·KCB 실연동, JDK 8 직접 비교는 이번 변경의 검증 범위 밖이다.

작업: Codex 및 테스트/검토 서브에이전트, `worktrees/utf8-runtime` (`codex/mobilecert-utf8-runtime`). 변경 파일과 검증 명령은 [로컬 계획](../tools/orca/plans/utf8-runtime.json)에 기록했다. Orca connector가 없어 실제 Task ID는 없으며 Qwen 3.8 Max는 호출하지 않았다.

메인 작업 공간에서도 regression과 coverage를 각각 실행해 **22개 성공, 실패·skip 0개**를 확인하고 HTML/JSON/XML 보고서를 갱신했다. `mvn -DskipTests package`로 패키지를 생성한 뒤, 실제 `tools/run-mobilecert.sh`를 임시 EUC-KR 설정·포트 0으로 실행해 `ServiceLauncher.main()` 기동과 종료를 확인했다. 실행 중 `file.encoding=UTF-8`도 확인했다. 결과는 `target/charset-launch-smoke.json`에 남겼다. 운영 DB·공급자 요청은 발생하지 않았다.

이번 JaCoCo 줄 커버리지는 **36.8% (1110/3015)**, 분기는 **22.1% (94/426)**다. 자식 JVM의 기동 검증은 기본 JaCoCo 에이전트 집계에 포함되지 않으며 이 수치는 전체 인증 업무 검증률이 아니다. 기존 readiness 결함은 관련 운영 소스를 변경하지 않았으므로 그대로 남아 있고 이번 작업에서 재실행하지 않았다. Java 8 JDK가 없어 비교 도구는 Python 문법·도움말과 후보 경로 변경만 확인했다.

통합 전 파일 사본은 작업 Worktree의 `target/charset-rollback/`에 보관했다. `src/main` 변경은 `CertCharset`과 `ServiceLauncher`에 한정되며, 모든 전문·DAO·풀 구현과 `pom.xml`은 작업 시작 시점의 바이트와 동일하다.

## 최초 회귀 검토 당시 실행 결과와 한계

- 기존 JUnit 10개를 macOS / Temurin 21.0.11에서 실행해 9개 성공, 1개 실패를 확인했다.
- 실패는 `ProtocolRegressionTest.productionJvmOptionsAndBothStandardStreamsUseEucKr`다. 동일 JDK의 별도 문자셋 probe에서도 `-Dfile.encoding=EUC-KR`로 실행했을 때 속성값은 `EUC-KR`지만 `native.encoding`과 `Charset.defaultCharset()`은 `UTF-8`이었다. `-Dfile.encoding=COMPAT` 역시 해당 환경에서는 `UTF-8`이었다. 요구한 기본 문자셋이 설정되지 않아 당시 기동 검사가 거절했다. 전체 JDK 21 배포판에서 동일하다고 일반화하지 않으며, 실제 배포 JDK·OS에서도 별도 확인해야 한다. 최초 테스트 추가 작업에서는 해당 검사를 유지했으며, 위 후속 변경에서 제거했다.
- 확장 회귀 테스트: **20개 중 19개 성공, 1개 실패**. 기존 기동 문자셋 시험이 실패했다. JaCoCo를 연결한 `coverage` 모드에서도 같은 결과였으며, 실패 상태를 유지하면서 HTML/XML 커버리지 보고서를 생성했다. 풀 고갈/복구, 8개 스레드 × 25회 대여·반납, KCB BSX1/CNSS 정상 통신은 통과했다. 미사용 LG U+ 직접 통신은 기본 집계에서 제외했다.
- 장애 처리 `readiness`: **10개 실행 모두 실패**, skip 0개. 이 수는 독립 결함 10개라는 뜻이 아니라 아래 원인들을 여러 입력/호출 경로로 재현한 실행 사례 수다. 집계 원본은 모드별 `summary.json`과 Surefire XML이다.
- 전체 운영 소스의 JaCoCo 줄 커버리지는 **36.9% (1115/3020)**, 분기 커버리지는 **22.2% (95/428)**였다. `Gateway`는 **0/287줄 (0%)**로 실제 인증 업무 흐름 전체를 검증하지 못했다. DAO/통신 클래스의 부분 실행률이 높아도 DB 스키마·바인딩·Oracle 제약이나 전체 인증 성공을 보장하지 않는다. 분모에는 보관 중인 LG U+ 소스도 포함된다. 이는 전체 소스 측정값이며 Sonar의 New Code 커버리지나 Quality Gate 결과가 아니다.
- Java 8이 설치되어 있지 않아 이번 작업에서는 두 런타임 직접 비교를 실행하지 못했다. Java 8 기준 바이트를 사용하는 테스트와 실제 Java 8 실행은 서로 다른 증거다.
- 선택 진단 `legacy-lgu`: 7개 중 4개 성공, 3개 실패(EOF 빈 코드 2개, 소켓 미종료 1개). 미사용 공급자이므로 운영 KCB 결과에 합산하지 않았다.
- Oracle 통합, 실제 공급자 개발계, 운영과 동등한 부하·장시간 검증 및 SonarQube 스캔은 실행하지 않았다.

| 재현된 장애 원인 | 실행 사례 | 실제 관찰 |
|---|---:|---|
| KCB 두 번째 DB 저장 실패 | 1 | 첫 저장은 반영되고 DAO는 true 반환 |
| DB 풀 고갈 시 SQL helper | 2 | SQLException 대신 NPE |
| 응답 없이 종료된 KCB 연결 | 1 | 실패 코드 대신 빈 코드 반환 |
| KCB 읽기 타임아웃 | 1 | `certResBean` null 역참조 |
| KCB 통신 완료 후 소켓 정리 | 1 | 반환 후 500ms 내 peer EOF 미수신; 소스에도 close 없음 |
| 고정 길이 수신 중 조기 EOF | 2 | IOException 없이 0 패딩 배열 반환 |
| 입력 스트림 close 실패 | 1 | 출력 스트림·소켓 정리 생략 |
| `recvAll()`의 `available()==0` | 1 | 뒤에 읽을 바이트가 있어도 응답 절단 |

마지막 `recvAll()`은 현재 운영 provider 통신 경로가 아니라 레거시 테스트 클라이언트에서 사용된다. `connect(byte[], int)`의 정리 실패 시험도 저수준 helper를 대상으로 한다. 실패의 업무 영향은 실제 호출 경로와 함께 판단해야 한다. 테스트마다 실패를 없애기 위해 운영 로직을 자동 수정하지 않았다.

JaCoCo XML은 SonarQube가 읽을 수 있는 별도 테스트 산출물이다. Sonar 자체가 테스트를 실행해 커버리지를 만들어 주는 것은 아니다. 사내 분석에서는 `target/site/jacoco/jacoco.xml`을 `sonar.coverage.jacoco.xmlReportPaths`에 지정할 수 있다. [공식 Java 커버리지 안내](https://docs.sonarsource.com/sonarqube-server/10.7/analyzing-source-code/test-coverage/java-test-coverage)

## 최초 검토에서 식별한 결함과 후속 상태

P1/P2는 이번 검토의 업무 우선순위이며 Sonar 심각도와 다르다. 아래 항목은 새 전환으로 발생했다고 입증한 회귀가 아니라 현재 소스에서 확인한 위험이다.

| 우선순위 | 근거 | 영향과 가장 작은 검증 | Sonar 대응 후보 |
|---|---|---|---|
| P1 | [KCBCommunication:57–63](../src/main/java/scbk/cert/main/KCBCommunication.java#L57) | 연결·읽기 실패 후 `certResBean`은 null인데 응답 조립에서 역참조한다. 통신 오류 전문 대신 NPE가 발생할 수 있다. 루프백 연결 거절/타임아웃/잘린 응답을 주입해 오류 응답의 형식과 반환을 검사한다. | [S2259](https://rules.sonarsource.com/java/RSPEC-2259/) 계열. 필드 상태 분석 범위에 따라 누락 가능 |
| P1 | [KCBCommunication:41](../src/main/java/scbk/cert/main/KCBCommunication.java#L41), [Communication:64](../src/main/java/scbk/cert/main/Communication.java#L64) | 외부 소켓을 성공·실패 경로에서 명시적으로 닫지 않는다. 반복 요청에서 자원 고갈 가능성이 있다. 서버 측 EOF와 반복 실행 후 열린 소켓 수를 검사한다. DB 풀 교체만으로 해결되지 않는다. | [S2095](https://rules.sonarsource.com/java/RSPEC-2095/) 관련. 사용자 정의 `SocketManager` 래퍼 때문에 자동 탐지 보장 불가 |
| P1 | [KCBDAO:190–209](../src/main/java/scbk/cert/dao/KCBDAO.java#L190), [DBFactory:72](../src/main/java/scbk/cert/dao/DBFactory.java#L72), [Gateway:429](../src/main/java/scbk/cert/main/Gateway.java#L429) | autoCommit=true에서 첫 UPDATE 후 두 번째 INSERT가 실패해도 첫 변경은 남을 수 있고, DAO가 true를 반환할 수 있다. Gateway는 저장 결과를 사용하지 않고 공급자 응답을 반환한다. 두 번째 SQL만 실패시켜 부분 저장·반환값을 검증한다. | [S1854](https://rules.sonarsource.com/java/RSPEC-1854/)가 결과값 미사용을 지적할 수 있으나 업무 정합성은 별도 검증 대상 |
| P1/P2 | [DBFactory:196](../src/main/java/scbk/cert/dao/DBFactory.java#L196), [DBFactory:215](../src/main/java/scbk/cert/dao/DBFactory.java#L215) | 풀 고갈 시 연결 획득은 null을 반환하지만 SQL helper는 즉시 역참조한다. SQLException 대신 NPE가 발생한다. 실제 Hikari 풀 2개를 점유하고 250ms 대기 후 두 helper의 예외 형태와 쓰기 0건을 검사한다. | S2259 계열 및 장애 계약 검증 |
| P1 | [KCBDAO:269](../src/main/java/scbk/cert/dao/KCBDAO.java#L269), [DAO:244](../src/main/java/scbk/cert/dao/DAO.java#L244), [DAO:286](../src/main/java/scbk/cert/dao/DAO.java#L286) | 요청의 부서코드·거래번호를 SQL 문자열에 직접 연결한다. `prepareStatement` 호출 자체는 파라미터 바인딩이 아니다. 기록용 드라이버로 따옴표를 포함한 합성 값이 SQL 구조에 삽입되는지 확인한다. | [S2077](https://rules.sonarsource.com/java/RSPEC-2077/). 설정 기반 월별 테이블명 연결과 외부 입력 연결은 구분 |
| P1 → 애플리케이션 로그 수정 | [KCBCommunication:56](../src/main/java/scbk/cert/main/KCBCommunication.java#L56), [Communication:70](../src/main/java/scbk/cert/main/Communication.java#L70), [Gateway:505](../src/main/java/scbk/cert/main/Gateway.java#L505) | 최초 검토의 CI/DI·원문 전문 출력 경로는 후속 로그 변경에서 제거했고 합성 값으로 검증했다. Hikari/JUL 및 외부 배포 로그 설정은 별도 확인 대상이다. | 민감정보 검토. 사용자 정의 로그와 CI/DI의 의미를 기본 규칙이 인식한다고 보장할 수 없음 |
| P1/P2 | [SocketManager:117–136](../src/main/java/scbk/cert/server/SocketManager.java#L117), [ServiceListener:43–49](../src/main/java/scbk/cert/server/ServiceListener.java#L43) | 조기 EOF에도 0으로 채운 요청 길이 배열을 반환하며, 수신 길이의 상한 검사 없이 메모리를 할당한다. 잘린 전문 거절·분할 수신 정상 처리·과대 길이의 할당 전 거절을 검증한다. | 기본 규칙만으로 검출된다고 보장하기 어려운 프로토콜 결함 |
| P2 / LGU 참고 | [Communication:77–82](../src/main/java/scbk/cert/main/Communication.java#L77), [Gateway:338–355](../src/main/java/scbk/cert/main/Gateway.java#L338) | LG U+ 전용 `dev` 기본값·테스트 응답 대체는 현재 미사용 코드로 분류한다. KCB 경로에도 별도의 step mode 응답 변경 분기가 있으므로 KCB 운영 설정은 확인이 필요하다. 실제 배포 설정은 미제공이다. | 업무 보안 검증 대상; LGU 전용 분기는 운영 KCB 결함으로 집계하지 않음 |
| P2 | [CreateParser:37–40](../src/main/java/scbk/cert/util/CreateParser.java#L37) | SAX 파서의 외부 엔티티/DTD 접근 제한이 명시되지 않았다. 설정 XML 신뢰 경계와 DTD 사용 여부를 확인하고 비민감 로컬 fixture로 검증한다. | [S2755](https://rules.sonarsource.com/java/RSPEC-2755/) 계열 |
| P2 | [CommonUtil:241–257](../src/main/java/scbk/cert/util/CommonUtil.java#L241) | 부분 문자열 범위 계산과 null/빈 배열 패딩 분기에 예외 경로가 있다. null, 빈 배열, 끝 경계 입력으로 검증한다. | null/범위 검사 계열 |
| P2 | [SocketService:21–77](../src/main/java/scbk/cert/server/SocketService.java#L21) | 공유 종료 플래그의 가시성, 연결별 무제한 스레드, 종료 시 무기한 대기 위험이 있다. 느린 연결·동시 요청·종료 반복을 확인한다. | 동시성 분석 및 별도 부하 검증 |

추가 유지보수 후보는 빈 catch(`S108`), 자원 정리 방식(`S2093`), 직접 표준 출력(`S106`), static 필드를 변경하는 인스턴스 메서드(`S2696`), 미사용 변수·필드·인자와 주석 처리 코드다. 이들을 인증 중단이나 데이터 정합성 문제와 같은 우선순위로 취급하지 않는다.

공식 규칙도 버전에 따라 분류와 분석 엔진이 달라진다. 예를 들어 [SonarJava 8.44.0.48651의 S2077](https://raw.githubusercontent.com/SonarSource/sonar-java/8.44.0.48651/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/S2077.json)은 `VULNERABILITY`이며 `former-hotspot` 태그가 있다. 과거 설치에서는 Hotspot으로 표시될 수 있다. [현재 설명](https://raw.githubusercontent.com/SonarSource/sonar-java/8.44.0.48651/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/S2077.html)도 S2077의 동적 SQL 탐지와 S3649의 taint 분석을 구분한다. S2259/S2095/S2755는 [공식 7.31 규칙 자료](https://github.com/SonarSource/sonar-java/tree/7.31.0.34839/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java)로 의미를 대조했으며, 이를 사내 설치 버전의 활성 규칙이라고 가정하지 않는다.

## 배포 전 검증 표

| 단계 | 시나리오 | 통과 기준 / 남은 증거 |
|---|---|---|
| 런타임 | 배포와 같은 JDK 21 배포판·OS·기동 옵션 | UTF-8 기본 JVM에서 명시적 EUC-KR 전문·출력, 설정 로딩·기동/종료 성공. 합성 설정 검증 이후 실제 배포 설정으로 확인 |
| 전문 회귀 | 실제 Java 8 원본과 Java 21 비교; 한글·공백·최대 길이 | 입력/출력 바이트·필드 위치·전문 길이 동일. 기존 합성 기준 외 승인된 비식별 샘플로 추가 확인 |
| 통신 장애 | 운영 KCB STEP1·STEP2, 거절·타임아웃·EOF·분할 수신 | 정해진 제한 시간과 오류 전문, 예외 유출 없음, 모든 소켓 정리. LGU는 재사용 검토 시 별도 수행 |
| DB 풀 | 풀 최대치·대기 제한·반납·유효하지 않은 연결·DB 재기동 | 제한 시간 내 실패 또는 복구, 활성 연결 수 복원, 요청 스레드 누적 없음. fake JDBC 이후 승인된 Oracle 테스트 인스턴스에서 확인 |
| DB 정합성 | 첫 SQL 성공 후 두 번째 SQL 실패, 0건 UPDATE, 재시도 | 부분 저장·거짓 성공·중복 부작용에 대한 승인된 요구사항 만족. DDL·권한·드라이버 실제 조합 검증 |
| 인증 업무 | 정상, 잘못된 인증값, 만료, 횟수 초과, 미등록 부서, 공급자 실패 | MCI 오류코드/길이 및 DB 상태 일치. 실패를 성공으로 덮어쓰는 설정 조합 없음 |
| 개인정보 | 정상·오류·디버그 경로의 로그 | 합성 민감값과 인증값 미노출; 로그 결과 화면에도 값 미포함 |
| 운영성 | 예상 동시성, 느린 공급자, 장시간 반복, 종료·재기동 | 합의한 지연/실패율·소켓/스레드/풀 한도 충족, 정상 종료와 복구 |
| 정적 분석 | 사내 SonarQube에서 빌드 산출물·JaCoCo XML과 분석 | 실제 활성 규칙과 결과 검토, 신규/기존 결함 구별, 업무상 P1 항목의 처리 근거 확보 |

승인된 테스트 환경에서 위 증거를 확보한 뒤 배포 여부를 판단한다. 배포 시에는 동일 설정의 이전 실행물과 JDK/의존성 복귀 절차를 준비하고, 오류율·응답 지연·풀 대기·열린 소켓을 관찰한다. 중단/복귀 기준 수치는 운영 담당자의 기존 기준과 연결한다.

## 작업 기록

- 담당 AI: Codex 및 분석/테스트 서브에이전트. Qwen 3.8 Max 호출 없음.
- Worktree: `모바일인증/worktrees/test-readiness`, 브랜치 `codex/mobilecert-test-readiness`.
- 작업 범위: `pom.xml`, `src/test/`, `tools/tests/`, 이 문서 및 [로컬 작업 계획](../tools/orca/plans/auth-test-readiness.json). 운영 소스 수정 없음.
- Orca connector를 사용할 수 없어 실제 Orca Task는 생성하지 못했다. 계획의 `orca_task_id`는 `null`이며 실제 작업 생성으로 보고하지 않는다.
- 원래 프로젝트에 통합한 뒤 모든 쉘 모드를 다시 실행했고, 각 검증 실패 시 종료 코드 1이 유지됨을 확인했다.
- 테스트 건수·성공 여부는 최신 결과 화면과 위 실행 결과 항목을 함께 확인한다. 운영 DB·공급자와의 실제 연동 및 Sonar 스캔은 미검증이다.
