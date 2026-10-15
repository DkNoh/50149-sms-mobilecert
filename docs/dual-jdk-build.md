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

산출물은 `target/mobile-cert.jar`와 `target/lib/`다. 같은 빌드에서 나온 JAR와 lib 전체를 함께 사용한다. `tools/run-mobilecert.sh`는 실제 서비스 실행용이며 기본 테스트에서는 실행을 가짜 Java로 대체한다. 실제 실행 시 호출 위치의 `../conf`를 사용하는 기존 설정 경로는 유지한다.

```text
mvn -Pjava21 test
mvn "-Pjava21,db" -Dmobilecert.test.config=conf/sevice-dev.xml test
mvn "-Pjava21,all-tests" -Dmobilecert.test.config=conf/sevice-dev.xml test
mvn "-Pjava21,coverage" clean verify
```

Java 8에서는 위 명령의 `java21`을 `java8`로 바꾼다. 기본 테스트는 실제 DB를 사용하지 않고 `db`는 개발 DB 검증만 선택한다. `all-tests`는 둘 다 실행한다. 자세한 DB 쓰기·정리 범위와 Bash 설정은 [개발 검증 안내](development-testing.md)를 따른다.

폐쇄망에서는 기존 Maven 설정의 사내 Nexus에서 의존성과 플러그인을 받는다. 의존성 JAR를 별도로 묶어 반입하지 않으며 Nexus 설정을 새로 추정해 만들지 않는다. 로컬 캐시가 이미 완비된 경우에만 `mvn -o -Pjava21 test`로 실행할 수 있다. 테스트용 Python이나 Docker는 필요하지 않다. Bash를 사용할 때 기존 `tools/tests/verify-mobilecert.sh`가 JDK에 맞는 Maven 프로파일을 선택한다.

결과는 `target/surefire-reports/`, 커버리지는 `target/site/jacoco/`다. 두 JDK에서 동일한 JUnit을 각각 실행해 결과를 확인하며, 현재 테스트 범위에 없는 실제 KCB 통신이나 TCP 처리를 검증한 것으로 해석하지 않는다.
