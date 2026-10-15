# 현재 테스트 범위와 실행 결과

KCB JUnit만 유지한다. Python, Docker, LG U+, TCP 소켓·서버 기동 및 셸 테스트는 제거했다. 실행 방법은 [JUnit 검증 안내](development-testing.md), 필요한 파일만 옮기는 방법은 [변경분 반입 안내](kcb-junit-transfer.md)를 따른다.

| 실행 | 확인하는 내용 | 실제 연결 |
|---|---|---|
| 기본 `mvn -Pjava21 test` | 합성 데이터의 KCB 전문, EUC-KR, 설정, 업무 분기, 운영 설정 DB 진입 차단 | 없음 |
| `mvn "-Pjava21,config" test` | 선택한 개발 또는 운영 XML의 파싱과 필수 설정 | 없음 |
| `mvn "-Pjava21,db" test` | 개발 DB의 KCB DAO 조회·저장, 업무 결과 대조와 테스트 행 정리 | 개발 DB |
| `mvn "-Pjava21,all-tests" test` | 기본 검증, 개발 설정과 개발 DB 검증 | 개발 DB |
| `mvn "-Pjava21,coverage" clean verify` | 기본 검증의 JaCoCo 보고서 | 없음 |

테스트 메서드는 `@Test`, 한글 메서드명, `// given`, `// when`, `// then`으로 구성한다. 나머지 주석도 한글로 작성한다. 실제 KCB 통신은 대체하고 개발 DB 통합 테스트만 실제 JDBC를 사용한다. 운영 XML은 읽기 검사에만 사용하며 DB 통합 테스트에 지정하면 JDBC 초기화 전에 실패한다.

## 최신 검증

- 2026-10-15, Windows와 JDK 21에서 `mvn -B -ntp -o -Pjava21 clean test` 성공: 37개 통과, 실패·오류·건너뜀 0개. 삭제된 셸 테스트 클래스가 남지 않도록 clean을 포함했다.
- `mvn -B -ntp -o "-Pjava21,config" test` 성공: 제공된 실제 `conf/service-dev.xml` 파싱 검사 1개 통과. DB와 KCB에는 접속하지 않았다.
- 합성 XML을 임시 운영 파일명으로 저장하여 읽기 성공과 DB 진입 차단을 기본 JUnit에서 확인했다. 실제 운영 XML은 없으므로 그 값의 유효성은 미검증이다.
- 실제 DB 테스트는 DAO 6개와 업무 4개다. 이번 수정에서는 DB에 다시 접속하지 않았다. 앞선 개발 DB 연결 시도는 SQLState `08006`, Oracle 오류 `12170`으로 시간 초과되어 조회·쓰기·정리가 미검증이며 데이터 변경은 없었다.
- Linux JVM과 JDK 8에서 실행하지 않았다. IntelliJ 공유 실행 XML은 구조와 인자를 검사했으며 UI에서의 직접 실행은 미검증이다.
- Git 변경 검사, 테스트 명명과 단계 주석, 코드 인덱스 갱신을 확인했다. 과거 셸 테스트 성공 건수는 현재 결과에 포함하지 않는다.

## 작업 기록

- 담당 AI: Codex, 문서 수정 및 읽기 전용 검토 보조 에이전트.
- 구현 Worktree: `C:/Users/dk/.codex/worktrees/0016/50149-sms-mobilecert`, 브랜치 `codex/kcb-junit-only`.
- Orca Task 연결 도구는 세션에 없으므로 기존 하위 Worktree에서 구현했다. Task 연결을 수행한 것으로 기록하지 않았다.
- 이번 변경 파일: 테스트 Java 3개와 셸 테스트 Java 삭제 1개, POM, `.gitignore`, 설정 검사 실행 XML 2개, 문서 5개, 기존 테스트 셸·안내 삭제 2개. 개발 설정 파일은 Git 추적 없이 이름만 바로잡았다.
- 운영 Java는 이번에 변경하지 않았다. 이전 KCB JUnit 변경의 Gateway 대체 지점은 유지했다.
- Qwen 3.8 Max 호출 없음.
