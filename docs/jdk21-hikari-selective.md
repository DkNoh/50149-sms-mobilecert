# mobilecert JDK 21 · HikariCP 선별 전환

이 문서는 최초 JDK 21/Hikari 전환 기록이다. **후속 변경에서는 JVM 전체 EUC-KR 강제 검사와 실행 옵션을 제거했다.** 전문·소스 파일·명시적 표준 출력 인코딩은 유지한다. 아래 과거 실행 결과와 현재 결과는 [후속 검증 기록](auth-test-readiness.md#utf-8-기본-jvm-기동-변경)으로 구분한다.

## 작업 위치와 범위

- 위치: `/home/dk/mobilecert/모바일인증/50149-sms-mobilecert`
- 담당: Codex. 사용자의 요청에 따라 현재 main 작업 공간에 적용했다. 별도 Orca 작업 공간은 만들지 않았다.
- 변경 전 기준: `c1b28bb96a2d7580c01fe6ad0e717a92e0cf721f`.
- JDK 21 독립 소켓 리스너, HikariCP 7.0.2, EUC-KR 유지, KCB/LG U+ 모두 유지.
- 기존 JDK 21 작업본에서는 전문 기준 테스트만 재사용했다. DAO 트랜잭션 재설계는 가져오지 않았다.
- Qwen 3.8 Max 호출: 없음. 운영 서비스 실행·배포·외부 인증·운영 DB 연결: 없음.

## 적용 파일

| 파일 | 적용 내용 |
| --- | --- |
| `pom.xml` | `release=21`, 컴파일 실패를 정상 실패로 처리, Maven 의존성, 테스트 및 `target/lib` 생성 |
| `dao/DBFactory.java` | JDBCPOOL을 HikariCP로 교체. 기존 public 메서드, SQL helper, 연결 취득 실패 시 null 반환 유지 |
| `util/CertCharset.java` | EUC-KR 상수, 표준 출력·오류의 EUC-KR 지정. 후속 변경에서 JVM 기본 문자셋 검사 제거 |
| `util/CommonUtil.java` | 기본 문자셋에 의존하던 바이트 변환을 EUC-KR로 명시 |
| `bean/mci/MCIHeaderBean`, `MCITransBean`, `MCIParser` | MCI 생성자·응답 바이트 변환의 EUC-KR 지정 |
| `bean/kcb/KCBCertReqBean`, `bean/uplus/HeaderBean`, `XPayAuthBean` | 문자열 생성자·전문 길이 계산의 EUC-KR 지정 |
| `main/Communication`, `KCBCommunication`, `Gateway` | 송수신 변환, 반환 전문의 로그 변환, `lguPlus` 파일 읽기의 EUC-KR 지정 |
| `server/ServiceLauncher.java` | UTF-8 기본 JVM에서도 설정 로딩·기동 허용. 종료 시 기존 리스너 close 후 풀 종료 |
| `tools/run-mobilecert.sh` | classpath 제공. JVM 기본 문자셋을 강제하지 않으며 호출자의 작업 디렉터리 유지 |
| `src/test`, `tools/tests` | 가짜 JDBC·소켓을 이용한 회귀 테스트 및 Java 8/21 비교 도구 |

기존 Java 파일의 한글 바이트와 줄바꿈 형식은 유지했다. `DAO.java`, `KCBDAO.java`, `SocketManager.java`, `SocketService.java`, `ServiceListener.java`는 변경 전과 파일 바이트가 동일하다. SQL, 반환값 판정, 오류 코드, 소켓 읽기 알고리즘은 기존 소스를 유지했다.

## COMMIT은 있는데 ROLLBACK이 없는 이유

소스만으로 작성자의 의도는 알 수 없다. 확인한 동작은 다음과 같다.

1. 원본 DAO와 DBFactory에 `setAutoCommit(false)`와 `rollback()` 호출이 없다.
2. 공개 JDBCPOOL 0.99의 `createDriverConnection()`은 JDBC 연결을 그대로 생성한다. 풀은 자동 커밋을 끄지 않는다.
3. `PooledConnection.close()`는 풀에 연결을 반환한다. 풀의 반환 처리에도 rollback이 없다. 기본적으로 prepared statement를 캐시한다.
4. JDBC 드라이버의 기본 자동 커밋이 true라면 SQL 실행마다 반영되므로 마지막 `commit()`만으로 여러 SQL이 하나의 트랜잭션이 되지 않는다.
5. KCB `updateStep1()`은 첫 UPDATE의 처리 건수를 저장한 후 두 번째 INSERT를 실행한다. 두 번째 SQL이 실패해도 첫 처리 건수에 따라 true를 반환할 수 있다. 가짜 드라이버와 실제 공개 JDBCPOOL 0.99로 이 결과를 재현했다.

이번 변경은 `autoCommit=true`를 명시하고 기존 DAO 결과를 유지한다. 자동 커밋을 false로 바꾸고 rollback을 추가하면 부분 반영·실패 처리 방식이 달라진다. 원자적 트랜잭션 전환과 실패 반환값 수정은 별도 작업으로 검토해야 한다.

Oracle JDBC 12.1 이후에는 자동 커밋 상태에서 명시적 `commit()`을 호출하면 SQLException이 발생할 수 있다. 기존 호출을 유지하기 위해 연결 속성 `oracle.jdbc.autoCommitSpecCompliant=false`를 설정했다. 이것은 **자동 커밋을 끄는 설정이 아니다**. 근거: [Oracle JDBC 호환 속성 설명](https://docs.oracle.com/en/database/oracle/oracle-database/18/jajdb/oracle/jdbc/OracleConnection.html#CONNECTION_PROPERTY_AUTO_COMMIT_SPEC_COMPLIANT).

운영에서 사용한 실제 JAR와 JVM 옵션은 제공되지 않았다. 공개 배포본과 운영 바이너리의 동일성, 실제 운영 연결의 `getAutoCommit()` 값은 별도 확인이 필요하다.

## 풀 교체로 달라지는 동작

| 항목 | JDBCPOOL 0.99 원본 | 이번 HikariCP 설정 |
| --- | --- | --- |
| 최대 연결 수 | 기존 `DEFAULT_DB.connection` | 같은 설정 사용 |
| 자동 커밋 | 드라이버 기본값 유지 | true 명시 |
| 연결 생성 | 필요 시 생성 | 풀 설정 시 연결 없음. 첫 대여에서 초기화 |
| 유휴 연결 | 60초 조건, 300초 주기 검사 | `minimumIdle=0`, `idleTimeout=60000ms`; 실제 정리 시점은 Hikari 검사 주기에 따름 |
| 연결 취득 대기 | 풀이 꽉 차면 기한 없는 대기 | 기본 60초. `-Dscbk.cert.db.connectionTimeoutMs=...`로 지정 가능 |
| 대여된 연결의 60초 초과 | 기존 reaper의 회수 대상 | `leakDetectionThreshold=60000ms` 경고. 강제 회수하지 않음 |
| 15회 대여 후 반환 | 물리 연결 제거 | 동일한 횟수 기반 설정 없음. 복제하지 않음 |
| 연결 수명·keepalive | 같은 이름의 옵션 없음 | `maxLifetime=0`, `keepaliveTime=0` |
| 준비된 SQL 캐시 | 풀 자체 캐시 사용 | Hikari의 풀 자체 statement cache 없음 |
| 미완료 수동 트랜잭션 반환 | 정상 반환 경로에 rollback 없음 | Hikari가 추적하는 미커밋 변경은 반환 시 rollback. 현재 DAO는 수동 트랜잭션을 시작하지 않음 |

**기존 `checkoutTimeout=60`을 Hikari의 연결 취득 대기 시간으로 단순 환산한 것이 아니다.** 60초 취득 대기는 새 제한이다. 연결 장애·고갈·장시간 SQL에서의 동작과 성능은 실제 테스트 DB에서 확인해야 한다. 기본값 설명: [HikariCP 7.0.2 설정 문서](https://github.com/brettwooldridge/HikariCP/blob/HikariCP-7.0.2/README.md).

## 문자셋 및 의존성

- Java 원본 소스와 전문은 EUC-KR이다. MS949로 바꾸지 않았다.
- 전문의 명시적 변환은 JVM 기본값이 UTF-8이어도 기존 Java 8 EUC-KR 바이트와 일치한다.
- 현재 실행 스크립트는 `file.encoding`을 지정하지 않고, ServiceLauncher도 JVM 기본 문자셋을 제한하지 않는다. JDK 21 기본 UTF-8에서 전문 변환은 명시적 EUC-KR을 사용한다.
- 최초 환경과 달리 후속 macOS Temurin 21.0.11 검증에서는 EUC-KR 강제 옵션이 기본 문자셋에 반영되지 않았다. JDK 21 명세의 `file.encoding` 지원값은 UTF-8/COMPAT이며 현재 실행은 EUC-KR 강제 옵션에 의존하지 않는다. [Java 21 System 문서](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#getProperties()).
- 이 환경에서 `file.encoding=EUC-KR`이어도 stdout/stderr 기본값은 UTF-8이었다. 따라서 지원되는 `PrintStream` API로 두 스트림을 EUC-KR로 지정했다. [JDK 21 System 출력 문자셋](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#out).
- 기존 Log4j 2.17.1 버전은 유지했다. Hikari의 SLF4J 2.0.17 로그는 JDK 로깅을 통해 stderr로 출력한다. 실제 Log4j 파일 appender 설정·출력 위치·문자셋은 제공되지 않은 운영 conf와 함께 확인해야 한다.
- Oracle 드라이버는 `ojdbc11:23.26.3.0.0`을 빌드 후보로 고정했다. 운영 Oracle DB 버전과 문자셋이 없어 연결·한국어 저장·조회 호환성은 확정하지 않았다. [Oracle JDBC 지원 조합](https://www.oracle.com/database/technologies/faq-jdbc.html).

## 최초 전환 당시 실행한 검증

검증 환경: OpenJDK 8u492 / OpenJDK 21.0.11.

- 변경 전 Java 8 원본 34개 소스 직접 컴파일 및 JAR 생성 성공. 공개 JDBCPOOL 및 원본 버전 Log4j 사용.
- 기존 `ojdbc14.jar`가 없으므로 기존 POM 그대로의 Maven 빌드·실제 Oracle 드라이버 실행은 검증하지 못했다.
- 전문 기준 테스트 13개: Java 8/EUC-KR, Java 21/EUC-KR, Java 21/UTF-8 모두 통과.
- 통신 클래스 테스트 6개: KCB BSX1/CNSS, LG U+ STEP1/STEP2 정상·통신 실패. 원본과 변경본의 바이트 배열을 전체 비교했다. 시각에 따라 달라지는 LG U+ 요청 타임스탬프 14자리만 정규화했다.
- 비교 스냅샷 SHA-256: `7f3f2b3c9cc6181543c3914036b263699d19d8b528836dd6c9f473af714410f8`.
- JUnit 10개: 풀 재사용·종료, 최초 연결 장애와 복구, 풀 고갈 시간 초과, 설정 전 종료, 수동 트랜잭션 반환 정리, 설정값, 기존 DAO 부분 실패 결과, 전문 기준 테스트, 문자셋 시작 조건, stdout/stderr 실제 바이트 확인.
- JDK 21 전체 Maven 빌드·테스트·패키징 성공. `target/mobile-cert.jar`, `target/lib/` 생성.
- 기존 deprecated 생성자 관련 경고는 남아 있다. JDK 21 컴파일 오류는 아니다.

공개 JDBCPOOL 참고 파일은 [FreeBSD 아카이브의 0.99 패키지](https://archive.freebsd.org/ports/i386/packages-4.10-release/java/jdbcpool-0.99.tgz)에서 받았다. 레포지터리 또는 배포 JAR에 추가하지 않았다.

- 패키지 SHA-256: `47030a6489373adade573fb0ff2ca8ccf82bd87f46b3ad3d5e9d2ec8bae81a99`
- 내부 `jdbcpool.jar` SHA-256: `ff76a46ac5b744652f607298884a644abbb2449319144279f7a7c2267145a73f`

## 재실행

JDK 21을 JAVA_HOME/PATH에 지정한 뒤 프로젝트 루트에서:

```sh
mvn clean verify
python3 tools/tests/compare-runtimes.py \
  --java8-home /path/to/jdk8 \
  --java21-home /path/to/jdk21 \
  --legacy-pool-jar /path/to/authentic/jdbcpool-0.99.jar
```

`--legacy-pool-jar`를 생략하면 전문 비교는 실행하고 기존 풀 검증은 SKIP으로 표시한다. 비교 도구는 임시 디렉터리에서 실행하며 운영 conf를 읽지 않는다. 일반 Maven 테스트도 외부 연결을 사용하지 않는다.

실제 서비스 실행은 승인된 테스트 환경의 기존 `../conf` 배치와 작업 디렉터리를 준비한 후 실행 스크립트를 사용한다. 기존 `scbk.cert.server.type` 등 JVM 옵션은 스크립트 인수로 전달할 수 있다. 스크립트는 임의의 dev/prod 설정을 선택하거나 conf를 생성하지 않는다.

## 운영 전 미검증 항목과 복귀 기준

제공되지 않은 conf를 복원하거나 추정해 만들지 않았다. 실제 리스너 시작·종료, 기존 로그 설정, Oracle 인증 및 한국어 저장/조회, 연결 단절·장시간 처리, KCB/LG U+ 실연동은 승인된 테스트 환경에서 확인해야 한다. 합성 테스트 통과가 실제 인증 성공을 보장하지는 않는다.

운영 전환 시 기존 JDK 8 실행물·라이브러리·conf·실행 명령을 함께 보관한다. 소스 비교 기준은 위 커밋이다. DB 스키마·운영 데이터·설정은 변경하지 않았으므로 이번 작업에 따른 DB 복구 작업은 없다. 운영 반영은 수행하지 않았다.
