# Java 8 / Java 21 선택 빌드

`JAVA_HOME`과 `mvn -version`으로 Maven이 사용하는 JDK를 확인하고 하나의 프로파일을 선택한다.

```text
mvn -Pjava8 clean verify
mvn -Pjava21 clean verify
```

| 프로파일 | Maven JDK | 컴파일 대상 | HikariCP | Oracle JDBC |
|---|---|---|---|---|
| `java8` | 8 | Java 8 | 4.0.3 | ojdbc8 23.26.3.0.0 |
| `java21` | 21 | Java 21 | 7.0.2 | ojdbc11 23.26.3.0.0 |

Java 8에서는 `java8`이 자동 활성화되며 기본 설정은 Java 21이다. 두 프로파일을 동시에 지정하거나 실행 JDK와 맞지 않는 프로파일을 지정하면 실패한다. JDK를 바꿀 때 `clean`으로 이전 산출물을 제거한다.

산출물은 `target/mobile-cert.jar`와 `target/lib/`다. 같은 빌드에서 나온 JAR와 lib 전체를 함께 사용한다. `tools/run-mobilecert.sh`는 실제 서비스 실행용으로 유지하며 JUnit에서는 실행하지 않는다. 실제 서비스가 호출 위치의 `../conf`를 사용하는 기존 경로는 유지한다.

```text
mvn -Pjava21 test
mvn "-Pjava21,config" test
mvn "-Pjava21,config" -Dmobilecert.test.config=conf/service-prod.xml test
mvn "-Pjava21,db" test
mvn "-Pjava21,all-tests" test
mvn "-Pjava21,coverage" clean verify
```

Java 8에서는 위 명령의 `java21`을 `java8`로 바꾼다. 기본 검증은 합성 데이터를 사용한다. `config`는 기본 `conf/service-dev.xml` 또는 명시한 XML의 파싱과 필수값만 확인하며 DB에 접속하지 않는다. `db`와 `all-tests`의 실제 DB 검증에는 파일명이 `service-dev.xml`인 개발 설정이 필요하다. 운영 XML을 지정하면 JDBC 연결 전에 실패한다. 상세 범위와 IntelliJ 사용법은 [JUnit 검증 안내](development-testing.md)를 따른다.

폐쇄망에서는 기존 Maven 설정의 사내 Nexus에서 의존성과 플러그인을 받는다. 의존성 JAR를 별도로 반입하지 않으며 Nexus 설정을 새로 추정해 만들지 않는다. 로컬 캐시가 이미 완비된 경우에만 `mvn -o -Pjava21 test`로 실행한다. 테스트에 Bash·Python·Docker는 필요하지 않다.

결과는 `target/surefire-reports/`, 커버리지는 `target/site/jacoco/`다. 두 JDK에서 동일한 JUnit을 각각 실행해 결과를 확인하며, 실제 KCB 통신이나 TCP 처리를 검증한 것으로 해석하지 않는다.
