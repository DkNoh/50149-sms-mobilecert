# TO-BE 개발서버에서 KCB 운영계 수동 인증 테스트

AS-IS 운영서버는 현재 서비스를 유지하고 TO-BE 운영서버는 아직 기동하지 않는다. 이번 실행 장소는 KCB 운영계와 방화벽이 개방된 **TO-BE 개발서버**다.

기존 `mobile-cert.jar`에 `scbk.cert.test.KcbManualTest`를 포함한다. 동일한 JAR에서 서비스 main과 테스트 main을 각각 실행한다. 테스트 클래스가 개발서버 자신의 수신 포트로 요청하면 기존 서비스가 개발 DB 처리와 실제 KCB 운영계 통신을 수행한다. 본인 휴대폰으로 받은 인증번호를 테스트 터미널에 직접 입력한다.

```text
TO-BE 개발서버
  터미널 1: mobile-cert.jar / ServiceLauncher
  터미널 2: mobile-cert.jar / KcbManualTest
                → 127.0.0.1의 서비스 포트 → 기존 서비스 → KCB 운영계
```

## 빌드 및 반입

JDK 21에서 빌드한다. 테스트 클래스는 일반 `src/main/java`에 있으므로 기존 Maven 패키징으로 같은 JAR에 들어간다. 기존 업무 Java와 `pom.xml`은 변경하지 않는다.

```sh
# JDK 21
mvn -Pjava21 clean package
```

개발서버의 별도 테스트 디렉터리에 다음과 같이 배치한다. `lib`는 기존 서비스 실행 의존성이며 별도 송신기 JAR는 없다. 설정은 승인된 실제 개발 설정을 별도로 준비한다. 저장소나 반입 묶음에 비밀값을 넣지 않는다.

```text
mobilecert-kcb-test/
  bin/
    mobile-cert.jar                 ← target/mobile-cert.jar
    lib/                           ← target/lib 전체
  conf/
    service-dev.xml                ← 기존 개발 설정 별도 배치
    log4j2-kcb-manual.xml           ← tools/log4j2-kcb-manual.xml
```

## 기존 설정 확인

- `DEFAULT_DB`: TO-BE 개발 DB의 실제 접속 정보. 파일명이 `service-dev.xml`이라는 사실만으로 개발 DB임이 확인되지는 않는다.
- `XPAY_LEASED`: 이 테스트 서비스가 사용할 `serverPort`, `serverTimeout`. 기존 프로세스와 겹치지 않는 포트를 사용한다.
- `MOBILE_CERT`: 승인된 KCB **운영계** 목적지·기관정보·요청서버 정보·테이블 접두어·제한 시간.
- `MOBILE_CERT.step1Mode`, `step2Mode`: 모두 `N`. 기존 `Y`는 KCB 호출을 막지 않고 응답을 성공 값으로 덮어쓰므로 실제 결과 확인에 사용할 수 없다.
- 사용할 부서코드는 개발 DB에 등록돼 있어야 한다. 서비스는 부서 조회, 거래번호 생성, 요청 INSERT를 수행한 뒤 KCB에 연결한다. 기존 월별 테이블·시퀀스가 필요하며 임의 DDL은 만들지 않는다.
- 입력할 MCI 거래코드는 `conf/lguPlus`에 등록되지 않은 KCB 경로여야 한다. 테스트 클래스도 이를 확인한다.

설정을 바꿨으면 서비스를 다시 기동한다. 테스트 클래스는 로컬 설정을 읽지만 이미 실행 중인 JVM이 읽었던 설정까지 확인하지는 않는다. 기존 서비스는 수신 포트를 모든 인터페이스에 열므로 테스트 포트의 접근 범위는 개발서버 설정에 따른다. 테스트 클래스의 연결 목적지는 `127.0.0.1`로 고정돼 있다.

## 실행

두 터미널 모두 위 배치의 `bin` 디렉터리에서 실행한다. 아래 `TEST_PORT`에는 `service-dev.xml`의 수신 포트를 입력한다. 개인정보를 명령줄 인자나 환경변수로 전달하지 않는다.

터미널 1에서 설정을 읽기 검사한다. 검사 대상은 개발 모드, 설정 파일 형식, 수신 포트 일치, KCB 응답 모드 `N/N`, 라우팅 파일의 읽기 가능 여부다. 이 명령은 DB·KCB·서비스 포트에 접속하지 않으며 DB 접속 정보나 KCB 기관정보의 유효성을 보증하지 않는다. 입력한 거래코드의 경로는 `--send` 실행 중 추가 검사한다.

```sh
cd /path/to/mobilecert-kcb-test/bin
TEST_PORT=설정된_수신포트
java -Dscbk.cert.server.type=dev \
  -Dlog4j.configurationFile=../conf/log4j2-kcb-manual.xml \
  -cp 'mobile-cert.jar:lib/*' scbk.cert.test.KcbManualTest --check --port "$TEST_PORT"
```

설정 검사 성공 후 같은 터미널에서 기존 서비스를 기동한다.

```sh
java -Dscbk.cert.server.type=dev \
  -Dlog4j.configurationFile=../conf/log4j2-kcb-manual.xml \
  -cp 'mobile-cert.jar:lib/*' scbk.cert.server.ServiceLauncher
```

`event=service.started`를 확인한다. 터미널 2에서 **같은 JAR**의 테스트 클래스를 실행한다. 실제 입력을 받을 수 있는 SSH/터미널에서 실행한다.

```sh
cd /path/to/mobilecert-kcb-test/bin
TEST_PORT=설정된_수신포트
java -Dscbk.cert.server.type=dev \
  -Dlog4j.configurationFile=../conf/log4j2-kcb-manual.xml \
  -cp 'mobile-cert.jar:lib/*' scbk.cert.test.KcbManualTest --send --port "$TEST_PORT"
```

1. 실제 사용하는 MCI 거래코드·부서코드·사무소/직원코드·채널유형을 입력한다.
2. 통신사 코드, **본인 휴대폰 번호**, 생년월일 6자리와 주민번호 구분 숫자 1자리, 가입자 이름을 입력한다. 주민번호 전체는 받지 않는다. 이름은 기존 입력 필드의 EUC-KR 15바이트 이내여야 한다.
3. 화면 안내를 확인하고 `SEND`를 입력하면 STEP1을 한 번 전송한다.
4. 본인 휴대폰에 도착한 SMS 인증번호 6자리를 직접 입력한다. STEP1의 거래번호는 메모리에 유지돼 STEP2에 자동으로 연결된다.
5. STEP2 결과코드와 결과 상태를 확인한다. 민감한 입력·거래번호·원문 응답·CI/DI는 출력하지 않는다.

실제 전송 옵션 없이 `--help`로 사용법을 볼 수 있다. 응답 대기 시간 기본값은 요청당 30초이며 `--timeout-ms`로 1,000~120,000밀리초 범위에서 조정할 수 있다. 기존 서비스의 DB·KCB 처리 대기 시간을 고려해 선택한다.

자동 재시도와 반복 발송은 없다. STEP1 후 인증번호 입력을 취소하면 STEP2를 보내지 않는다. `UNKNOWN`은 KCB가 요청을 처리했는지 확정할 수 없다는 의미이므로 서버 이벤트와 수신 문자를 먼저 확인한다. 서비스 종료는 터미널 1에서 `Ctrl+C`로 수행한다.

기존 서비스는 STEP1에서 KCB `BSX ` 전문을 보낸다. 실제 수신 방식은 KCB 응답과 휴대폰에서 확인하며, 문자가 도착하지 않으면 서버의 `KCB_STEP1_RESULT` 이벤트에 기록된 `certMode`와 결과코드를 확인한다. 특정 인증 방식 코드를 임의로 성공 조건에 추가하지 않는다.

## 결과의 범위

`ACCEPTED`는 STEP1의 성공 응답, `SUCCESS`는 STEP2의 성공 응답이다. 성공 여부는 결과코드와 단계·거래번호 일치로 확인한다. 기존 서비스의 `procRsltDvCd=0` 또는 `mci.responseSent`만으로 인증 성공을 판정하지 않는다.

이 테스트는 기존 서비스 동작대로 개발 DB에 요청/결과 이력을 남긴다. 자동 정리나 삭제를 수행하지 않는다. 기존 Gateway는 일부 결과 UPDATE의 반환값을 무시하므로 인증 응답 성공과 DB 결과 저장 성공은 별도 확인 항목이다.

자동화 검증은 합성 데이터의 전문 길이·EUC-KR 바이트·응답 분할 수신·오류·취소·출력 제한을 검사한다. 실제 KCB, 실제 DB, 실제 휴대폰 인증은 사용자가 위 수동 절차를 실행했을 때 검증된다.
