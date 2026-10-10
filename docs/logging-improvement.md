# 모바일인증 로그 정비 구현과 검증 기준

운영 로그 코드는 `worktrees/safe-logging`에서 수정해 메인 작업 트리에 통합했다. 회귀와 coverage 실행에서 각각 42개가 모두 통과했으며 readiness는 기존 장애 사례 10개가 실패했고 패키징은 성공했다. 이전 개발 DB 사전 점검 작업에서는 설계만 했고, 이번 후속 작업에서 `src/main` 16개 파일에 레벨 분류·민감값 출력 제거·요청 추적을 적용했다. 이 결과로 배포 준비 완료를 판단하지 않는다.

## 1. 구현한 변경과 유지한 범위

| 대상 | 반영한 동작 |
|---|---|
| `util/Log.java` | DEBUG/INFO/WARN/ERROR API, 요청 ThreadLocal, 정제된 예외 진단 정보 |
| `main/KCBCommunication.java`, `Communication.java` | 전문·CI/DI 출력 제거. 바이트 길이·허용된 결과코드·소요시간만 기록 |
| `server/ServiceListener.java` | MCI 전문 출력 제거. 수신·전송·실패를 고정 이벤트로 구분 |
| `main/Gateway.java` | 공급자 선택·응답 코드만 기록. 라우팅 파일 실패는 JVM 최초 1회 WARN, 이후 DEBUG |
| `dao/DBFactory.java`, `KCBDAO.java`, `DAO.java` | SQL·바인딩·원문 예외 메시지 대신 고정 작업명과 정제된 ERROR |
| `util/XMLFactory.java`, `XMLHandler.java`, `CreateParser.java`, `CommonUtil.java` | 설정 처리·마스킹 실패 레벨 구분. 예외 원문 출력 제거 |
| `server/Server.java` | 요청마다 서버 생성 ID를 설정하고 `finally`에서 제거 |
| `server/SocketService.java`, `SocketManager.java`, `ServiceLauncher.java` | 표준 출력/stack trace 대신 고정 이벤트. 정상 종료와 실행 중 장애 구분 |

기존 `Log.write()`는 수동 도구·문자셋 호환성 시험을 위해 **기존 ERROR 레벨과 전달된 문자열을 유지**한다. 활성 서비스 호출은 새 API를 사용한다. `write()`는 정제 API가 아니므로 신규 서비스 로그에 원문을 넘겨서는 안 된다. 모든 호출을 INFO로 하향하거나 전문을 DEBUG로 옮기는 방식은 사용하지 않았다.

SQL·트랜잭션·반환값·Hikari 설정·대기 시간·전문 필드·EUC-KR 변환은 이번 변경 대상이 아니다. `CommonUtil.getExceptionMessage()` 구현과 기존 마스킹 변환 동작은 호환성을 위해 유지했지만, 활성 경로에서 원문 예외 문자열이나 마스킹된 전문을 로그에 전달하지 않는다. 기존 부분 저장·장애 응답·소켓 정리·스레드 제한 문제는 이 작업으로 수정하지 않았다.

## 2. 실제 레벨과 이벤트

| 레벨 | 현재 이벤트 예시 | 의미 |
|---|---|---|
| DEBUG | `KCB_REQUEST_SENT`, `KCB_RESPONSE_RECEIVED`, `KCB_RESPONSE_PARSED`, `mci.requestReceived`, `mci.responseWritten` | 전문 본문 없이 단계·바이트 길이·결과코드 확인 |
| DEBUG | `server.listenerClosed`, `mci.inputEndedOrIncomplete`, 후속 라우팅 파일 실패 | 정상 종료 또는 빈/불완전 입력 진단. 정상 인증 성공을 뜻하지 않음 |
| INFO | `service.started`, `service.stopped`, `mci.responseSent` | 기동·종료 또는 MCI 응답 전송 완료 |
| WARN | `mci.inputTimeout`, `mci.inputReadFailed`, 마스킹 실패, 최초 `PROVIDER_ROUTE_FILE_UNAVAILABLE` | 수신 지연·읽기 실패·진단 이상. 파일 실패 시 기존 KCB fallback 유지 |
| ERROR | `KCB_COMMUNICATION_FAILED`, `mci.responseFailed`, `mci.inputFailed`, `DB_OPERATION_FAILED`, 설정/풀 초기화 실패 | 통신·응답·DB·설정의 처리 실패 |

**`mci.responseSent`는 응답 전송 완료이며 본인인증 성공이 아니다.** 응답이 업무 거절이나 실패를 담을 수도 있다. 인증번호 불일치·만료 등 업무 결과를 별도 INFO 감사 이벤트로 집계하는 기능은 아직 추가하지 않았다. 실제 결과 의미는 최종 응답과 승인된 업무 규격으로 판단한다.

KCB 통신 예외는 `outcome=UNKNOWN`을 기록한다. 공급자가 이미 접수했는지 확정할 수 없음을 나타내며, 이 로그 변경은 자동 재전송이나 기존 장애 응답 처리를 바꾸지 않는다.

LGU 라우팅 파일이 없어도 기존대로 KCB를 선택한다. 해당 실패는 JVM 내 최초 1회만 WARN과 정제된 예외 정보를 남기고 이후에는 DEBUG로 기록한다. 파일 읽기 자체나 fallback 조건은 변경하지 않았다. 최초 이후 다른 원인으로 파일 읽기가 실패해도 추가 WARN은 발생하지 않으므로 이를 지속적인 설정 상태 감시로 해석하지 않는다.

## 3. 기록하는 정보와 제외하는 정보

다음은 구현된 메시지 형식의 설명용 예시이며 실제 수집 로그가 아니다. 시각·레벨·카테고리 표시는 외부 Log4j appender 설정에 달려 있다.

```text
INFO  event=mci.responseSent durationMs=120 requestId=<server-generated>
DEBUG event=KCB_RESPONSE_PARSED headerCode=0000 bodyCode=0000 elapsedMs=30 requestId=<server-generated>
ERROR event=KCB_COMMUNICATION_FAILED outcome=UNKNOWN requestId=<server-generated> exceptionType=java.net.SocketTimeoutException
ERROR event=DB_OPERATION_FAILED operation=KCBDAO.selectDeptCode requestId=<server-generated> exceptionType=java.sql.SQLException sqlState=42000 vendorCode=942
```

호출부는 고정 이벤트와 숫자·허용된 코드만 넘긴다. `Log.code()`는 대문자 영문/숫자 1~4자만 허용하고 다른 값은 `INVALID`로 기록한다. 메시지 본문은 최대 512자로 제한하고 제어문자·줄바꿈을 치환한다. **이 처리는 임의 문자열의 개인정보를 제거하는 일반 마스킹 기능이 아니다.**

전화번호, 주민번호, 인증번호, 이름, CI/DI, 고객/기관 원문 전문, DB 계정/URL/비밀번호, Vault 토큰, SQL문·바인딩 값은 활성 서비스의 새 로그 메시지에서 제외했다. 요청 ID에도 개인정보나 인증 거래번호를 사용하지 않는다. 데이터 최소화 기준은 [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)를 참고한다.

## 4. 예외 정제와 요청 ID

`warn/error(category, safeMessage, Throwable, ref)`는 원본 Throwable을 Log4j에 전달하지 않는다. message·`toString()`·cause/suppressed의 원문 메시지·stack frame 파일명을 출력하지 않고 다음 정보만 선택한다.

- 예외 클래스명과 제한된 클래스/메서드명·줄번호
- 정규화된 5자리 SQLState와 숫자 vendor code
- 중복/순환을 검사한 최대 4개 예외, 예외당 최대 6개 stack frame

원인·suppressed·다음 SQLException은 위 한도에서 진단 메타데이터만 읽는다. 로그 레벨이 비활성화돼 있으면 예외 정보를 포맷팅하지 않는다. 원본 예외 객체와 기존 catch/return/throw 흐름은 유지한다. 로그 내부 RuntimeException은 고정된 진단 실패 문구를 JVM당 한 번 출력하도록 처리한다.

`Server.serve()` 진입 시 프로세스별 무작위 접두사와 AtomicLong으로 요청 ID를 만들고 ThreadLocal에 저장한다. 동기 실행되는 서버·Gateway·DAO 로그에 같은 ID가 붙고, 소켓 종료가 실패해도 요청 경계의 `finally`에서 제거한다. 기존 인증 거래번호/TID 생성과 전문 필드는 변경하지 않는다. MDC 또는 별도 비동기 작업으로의 자동 전파는 구현하지 않았다.

같은 장애가 하위 통신/DB 계층과 상위 응답 계층에서 각각 기록될 수 있다. 원인 제거·중복 알림 통합은 요청 ID와 이벤트별로 판단하며, 예외 전파나 업무 실패 판정을 로그 정비에 포함해 바꾸지 않았다.

## 5. 배포 설정과 남은 제한

실제 배포 Log4j2 설정이 제공되지 않아 logger/appender의 임계값, 파일 회전·보관·권한, 동기/비동기 여부를 검증하지 않았다. 운영 반영 전 다음을 확인해야 한다.

1. `log.day`와 `log.root`의 logger 및 appender 임계값이 INFO/WARN을 필요한 출력 대상으로 전달하는지 확인한다. 기존 ERROR 전용 필터가 남으면 새 정상 요약·경고가 누락될 수 있다.
2. 기존 메시지·4자리 로그 ID를 기준으로 만든 알림/수집 parser를 새 이벤트와 `requestId` 형식에 맞춘다. `mci.responseSent`를 인증 성공 지표로 집계하지 않는다.
3. DEBUG는 기본 비활성으로 두고 필요한 진단 범위에서만 사용한다. 실제 로그 인코딩·수집기의 문자셋과 로그 포함 상태의 출력량·지연·디스크 사용을 확인한다.
4. 비동기 appender를 사용한다면 큐 한도, 포화 시 차단/유실, 종료 flush를 별도로 확인한다. 이번 작업에서 appender나 스레드 풀을 변경하지 않았다.

**Hikari의 SLF4J→JUL 로그는 이번 정제 API 적용 범위 밖이다.** `pom.xml`의 `slf4j-jdk14` 경로를 유지했으며 Hikari/JDBC 원문 예외나 설정 출력까지 정제했다고 주장하지 않는다. Hikari DEBUG 설정 덤프를 켜지 말고, 별도 JUL 수집 경로·레벨·민감정보 가능성을 확인해야 한다. 프레임워크 통합이나 필터 도입은 별도 검증 대상으로 남는다.

## 6. 추가한 시험과 최종 확인

기본 테스트 설정은 Log4j가 꺼져 있지만 새 로그 시험은 실제 Log4j bridge에 캡처 appender를 연결한다. `safe-logging` 회귀와 통합 후 메인 coverage 실행에서 각각 **42개 모두 통과**했으며 기존 22개와 로그 검증 20개를 포함한다. coverage는 줄 **39.6%**, 분기 **27.5%**다. 이 값은 전체 소스 측정값이며 Sonar New Code 커버리지나 Quality Gate 통과 결과가 아니다. readiness는 기존 장애 사례 10개가 실패했고 `mvn -DskipTests package`는 성공했다.

| 시험 | 확인 범위 |
|---|---|
| `LogSafetyTest` | 레벨/기존 write 호환, 예외 원문·원인·suppressed·SQL 다음 예외·파일명 제외, 코드 검증, 메시지 길이, 순환 예외, 비활성 레벨 |
| `LogSafetyTest`, `ServerLoggingTest` | 동시 요청 ID 분리, 요청/소켓 정리 실패 시 ThreadLocal 제거, 빈 입력·수신 실패의 레벨 |
| `ProviderLoggingTest` | 합성 전문·이름·전화번호·인증번호·CI/DI 미출력, 정제된 오류, 송수신 바이트 불변 |
| `DaoLoggingTest` | 합성 SQL 예외 원문 미출력, 고정 작업명, 기존 실패 반환과 자원 정리 유지 |
| 기존 회귀 시험 | 명시적 EUC-KR 전문, 기동·Hikari·DAO·소켓의 기존 동작 |

별도 JDK 21 비교에서 로그 변경 전/후 Provider 6개 경로의 **6,038바이트가 동일**했고 Protocol 13개도 통과했다. 이는 로그 변경 전후 비교이며 기존 Java 6/8 런타임 실행물과 비교한 결과는 아니다.

프로젝트 루트에서 `tools/tests/verify-mobilecert.sh regression`을 실행한다. 결과는 `target/test-runs/regression/index.html`과 `summary.json`으로 확인한다. 전체 회귀가 통과해도 기존 `readiness` 결함, 개발 Oracle 19c 실접속, KCB 실연동, 실제 Log4j/JUL 배포 설정, 전체 인증 흐름·부하 검증이 해소되는 것은 아니다.

작업: Codex 및 구현/검토 서브에이전트, `worktrees/safe-logging`. 실제 개발 Oracle·KCB·내부 MCI 접속과 Qwen 3.8 Max 호출은 수행하지 않았다. 메인 통합·패키징을 완료했다. 기존 readiness 실패 10개는 해결되지 않았으며 최신 결과는 `target/test-runs/readiness/`에 남겼다.

## 7. 변경 이력과 복구 사본

메인에 반영하기 전 원본 파일과 전후 SHA-256은 `target/logging-rollback/` 및 `manifest.json`에 저장했다. 새 파일은 `before_sha256: null`로 표시한다. 복구 시 manifest에 포함된 이번 파일만 대상으로 삼고, 이후 수정이 없는지 `after_sha256`을 확인한 다음 원본을 복원하고 다시 빌드한다. 기존 JDK/Hikari 및 다른 작업 변경까지 되돌리는 전체 저장소 복원 명령을 사용하지 않는다. DB 변경은 발생하지 않았다.

실제 실행 명령·미검증 범위는 [로컬 작업 기록](../tools/orca/plans/safe-logging.json)에 기록했다. 메인 최신 정상 검증/커버리지 결과는 `target/test-runs/coverage/index.html`, `target/site/jacoco/index.html`에 있다. 작업 Worktree의 regression 42개 결과와 메인의 coverage 42개 결과를 구분한다. 메인에서 regression 명령을 재실행하면 해당 regression 보고서가 갱신된다.
