# 현재 테스트 범위와 판단 기준

현재 검증은 KCB에 한정한다. 이전의 Python 보고서·비교 실행기, Docker Oracle, LG U+와 TCP 소켓 검증은 제거했다. 실행 명령과 로컬 XML 설정은 [개발 검증 안내](development-testing.md)를 따른다.

| 실행 | 확인하는 내용 | 확인하지 않는 내용 |
|---|---|---|
| 기본 `mvn -Pjava21 test` | KCB 고정 길이 전문과 EUC-KR, 설정 파싱, 대체 의존성을 사용한 업무 분기, Linux 또는 명시적 선택 시 셸 인자·종료 코드 | 실제 DB 저장, 실제 KCB 통신, TCP 처리 |
| `mvn "-Pjava21,db" test` | 기존 개발 DB의 KCB DAO 조회·저장과 업무 결과 대조, 테스트 생성 행 정리 | 실 공급자 인증, 서비스 TCP 송수신·기동, 장애·부하 시험 |
| `mvn "-Pjava21,all-tests" test` | 위 두 범위의 합계 | 운영 환경 전체 인증과 성능 보증 |
| `mvn "-Pjava21,coverage" clean verify` | 기본 테스트의 JaCoCo 실행률 | 테스트에서 실행하지 않은 동작의 정확성 |

테스트 메서드는 한국어 설명과 `given / when / then` 구간으로 구성한다. KCB 전문은 바이트 길이·필드 위치·공백·한글 인코딩을 확인하며, 공통 코드는 KCB가 사용하는 범위만 포함한다. 기존 결함을 테스트 통과를 위해 운영 코드에서 임의로 수정하지 않는다.

`db` 테스트는 기본 실행에서 제외된다. DB가 없는 단말에서도 기본 검증을 실행하고, 개발 DB 접근이 가능한 환경에서 실제 저장을 별도로 확인하기 위한 구분이다. 설정 누락이나 DB 연결 실패를 성공 또는 건너뜀으로 처리하지 않는다. 과거 도구에서 얻은 성공 건수나 커버리지 수치는 현재 실행 결과로 재사용하지 않는다.

최신 결과는 해당 실행의 `target/surefire-reports/`에서 확인한다. 이 변경 자체는 실제 개발 DB·KCB 연결, TCP 처리 및 운영 부하를 검증했다는 의미가 아니다.

## 이번 작업의 실행 결과

- 담당 AI: Codex와 구현·읽기 전용 검토 보조 에이전트.
- 구현 Worktree: `C:/Users/dk/.codex/worktrees/0016/50149-sms-mobilecert`, 브랜치 `codex/kcb-junit-only`.
- Orca: 세션에 호출 가능한 연결 도구가 없어 실제 Task 연결은 하지 못했다. Task ID를 임의로 만들지 않았다.
- 변경 범위: KCB 전문·설정·업무·실제 DB·셸 JUnit, Maven 실행 설정, 기존 테스트 셸과 문서. 운영 변경은 Gateway의 의존성 생성·분기 선택 연결부로 제한했다.
- 2026-10-15, JDK 21: `mvn "-Pjava21,coverage" clean verify` 성공. 기본 테스트 44개 통과, 실패·건너뜀 0개. JAR 패키징과 JaCoCo 보고서 생성 성공.
- 실제 DB 연결 검사 1개: XML을 읽고 JDBC 연결을 시도했으나 SQLState `08006`, Oracle 오류 `12170`으로 실패했다. 연결 시간 초과로 DB 조회·쓰기·정리 검증은 진행하지 못했고 데이터 변경은 없었다. 실패를 건너뜀으로 바꾸지 않았다.
- 실제 DB 테스트는 DAO 6개와 업무 4개가 구현되어 있다. 개발 DB에 접근 가능한 단말에서 `mvn "-Pjava21,db" test`로 실행한다.
- JDK 8 런타임 검증은 설치된 JDK가 없어 실행하지 못했다. KCB 실통신과 서비스 소켓 시험은 요청에 따라 제외했다.
- 처음 오프라인 커버리지 실행은 로컬 JaCoCo 플러그인 캐시 부족으로 중단됐다. 의존성을 받은 후 위 전체 검증에 성공했다. 폐쇄망에서는 사내 Nexus를 사용한다.
- `git diff --check`, 셸 문법, 테스트 메서드명·단계 주석과 코드 인덱스 갱신을 확인했다.
- Qwen 3.8 Max 호출 없음.
## Windows와 IntelliJ 실행 조정

- 같은 하위 Worktree에서 ShellScriptTest의 클래스 실행 조건, 공유 Maven 실행 설정 2개, 실행 안내를 변경했다. POM과 운영 소스·DB 설정은 변경하지 않았다.
- Windows 기본 Maven 실행: Java 테스트 34개 통과, Linux용 셸 테스트 10개 건너뜀. Git Bash 경로를 명시한 셸 실행: 10개 통과. 존재하지 않는 Bash 경로를 명시한 경우: 예상대로 1개 실패.
- WSL Ubuntu의 실제 Bash로 기존 셸 2개의 문법 검사 통과. Linux JDK가 없어 Linux JVM의 JUnit 실행은 수행하지 못했다. Windows의 `os.name`을 바꾸어 Linux 검증으로 간주하지 않았다.
- 공유 IntelliJ 설정은 공식 Maven 직렬화 형식을 대조하고 XML·프로파일·작업 경로를 검사했다. IntelliJ UI에서의 직접 로딩·실행은 미검증이다.
- 실제 DB는 이번 운영체제 조정 검증에서 접속하지 않았다. 앞서 확인한 개발 DB 연결 시간 초과는 그대로 미해결이다.
- 담당 AI: Codex와 읽기 전용 검토·실행 설정 보조 에이전트. Orca 연결 도구 없음, Qwen 3.8 Max 호출 없음.