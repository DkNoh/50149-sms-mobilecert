# 기존 개발 Oracle 검증

기존 개발 DB와 로컬 `conf/service-dev.xml`을 JUnit에서 사용한다. 가상 DDL이나 별도 컨테이너는 만들지 않는다. 준비와 실행 구분은 [JUnit 검증 안내](development-testing.md)에 있다.

```text
mvn "-Pjava21,db" test
```

다른 위치의 개발 XML은 `-Dmobilecert.test.config=경로/service-dev.xml`로 지정한다. DB 쓰기 검증은 파일명이 `service-dev.xml`인 개발 설정만 허용하며 운영 XML은 JDBC 연결 전에 거부한다.

운영 XML의 형식과 필수값만 확인할 때는 DB 프로파일 대신 다음 명령을 사용한다. 실제 운영 파일은 로컬에 별도로 준비하며 이 명령은 DB와 KCB에 연결하지 않는다.

```text
mvn "-Pjava21,config" -Dmobilecert.test.config=conf/service-prod.xml test
```

DB 검증은 `DEFAULT_DB`로 연결하며 `MOBILE_CERT` 테이블 접두사와 DB 기준 월을 사용한다. 기존 시퀀스·월별 테이블·조회 가능한 부서 정보가 필요하다. 실제 Oracle JDBC와 KCB DAO로 요청 및 결과를 저장하고 별도 JDBC 조회로 값과 처리 결과를 확인한다. 테이블이나 제약조건을 생성·삭제하지 않는다.

테스트는 새 거래 키를 확보하고 기존 행이 없는지 확인한 후 저장한다. 정리는 해당 실행에서 생성한 거래 키와 소유 표식을 확인한 행에 한정한다. DAO가 내부에서 커밋하므로 정리 SQL을 명시적으로 실행하며 정리 실패도 테스트 실패다. 부서 정보는 조회만 한다.

설정 파일은 Git에서 제외하며 콘솔·보고서에 설정 원문이나 JDBC 비밀값을 출력하지 않는다. `db` 프로파일의 성공은 실제 저장·조회에 대한 증거이며 KCB 네트워크 인증이나 TCP 서버 동작의 검증은 아니다.
