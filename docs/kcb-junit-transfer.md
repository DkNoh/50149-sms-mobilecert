# JUnit 변경분 반입

전체 저장소를 다시 옮길 필요는 없다. 같은 최초 소스(`1b6aa1f`)를 기준으로 KCB JUnit 실행에 필요한 파일만 옮길 수 있다. 이미 직전 전달본(`59ced22`)을 반영했다면 아래의 이번 수정 파일만 덮어써도 된다.

## 이번 수정

- 테스트 Java 3개: `ServiceTestConfig.java`, `DevelopmentDatabase.java`, `ServiceConfigTest.java`.
- Maven 설정: `pom.xml`의 기본 제외 태그와 설정 검사 전용 `config` 프로파일.
- 로컬 설정: 기존 `conf/sevice-dev.xml`의 이름을 `conf/service-dev.xml`로 수정했다. 내용과 EUC-KR 인코딩은 유지한다.
- 삭제: `src/test/java/scbk/cert/support/ShellScriptTest.java`, `tools/tests/verify-mobilecert.sh`, `tools/tests/README.md`.
- 선택적으로 복사: IntelliJ의 개발·운영 설정 검사 실행 파일 2개와 갱신한 안내 문서.
- `.gitignore`에 운영 설정 제외 경로를 추가했다.

이번 수정에는 운영 Java 변경이 없다. 앞선 KCB JUnit 작업까지 포함하면 운영 Java 변경은 `src/main/java/scbk/cert/main/Gateway.java` 1개이며 최초 소스 대비 30줄 추가·15줄 삭제다. 기존 생성과 공급자 선택 부분을 테스트에서 대체할 수 있게 분리한 변경이다. 다른 운영 Java 파일은 옮길 필요가 없다.

## 최초 소스에 적용하는 경우

반입용 ZIP에는 필요한 `Gateway.java`, `pom.xml`, 현재 `src/test` 전체, `.gitignore`, 공유 IntelliJ 실행 설정, 개발 XML과 안내 문서만 들어 있다. 기존 Maven·Nexus 설정, 전체 운영 소스, 의존성 JAR, `target`, Python·Docker·셸 테스트 도구는 포함하지 않는다. ZIP의 경로 구조를 프로젝트 루트에 맞춰 복사한다. 대상에 별도 수정이 있다면 덮어쓰기 전에 해당 파일을 비교한다.

`DELETE_FILES.txt`는 이전 버전의 불필요한 테스트 파일 목록이다. 대상에 존재하는 파일만 삭제한다. 현재 테스트 폴더만 덮어쓰면 이전 테스트 파일이 남으므로 이 삭제 목록도 적용한다. 마지막에 `mvn -Pjava21 clean test`로 오래된 테스트 클래스까지 정리하고 실행한다. 운영 실행용 `tools/run-mobilecert.sh`는 이 반입 범위에서 제외하며 기존 파일을 유지한다.

## 설정 파일

- `conf/service-dev.xml`: 사용자가 제공한 개발 설정이다. 실제 주소와 인증정보가 있으므로 Git에 추가하지 않는다.
- `conf/service-prod.xml`: 운영 설정값은 제공되지 않아 ZIP에 포함하지 않았다. 기존 실제 운영 XML을 이 위치에 둔다. 개발 설정을 운영 설정으로 복제하지 않는다.
- 개발·운영 XML의 형식 검사는 `config` 프로파일로 실행하며 DB 연결은 없다.
- DB 조회·저장 테스트는 개발 설정만 사용한다. 실경로 파일명이 `service-dev.xml`인지 검사하므로 운영 설정을 다른 이름으로 바꿔 넣지 않는다. 파일명만으로 접속 대상의 실제 환경을 판별할 수는 없다.

## 실행

```text
mvn -Pjava21 clean test
mvn "-Pjava21,config" test
mvn "-Pjava21,config" -Dmobilecert.test.config=conf/service-prod.xml test
mvn "-Pjava21,db" test
```

처음 세 명령은 DB에 연결하지 않는다. 마지막 명령은 개발 DB에 접근 가능한 사내 단말에서 실행한다. JDK 8 환경에서는 `java21` 대신 `java8`과 해당 JDK를 사용한다. IntelliJ에서는 공유 Maven 실행 설정을 선택한다. Windows와 Linux를 나누는 셸은 필요하지 않다.
