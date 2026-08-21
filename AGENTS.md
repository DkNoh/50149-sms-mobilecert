# sms-mobilecert 작업 규칙

## 기준

- 이 저장소는 Java 8, EUC-KR, 고정 길이 TCP 전문을 사용하는 레거시 모바일 인증 게이트웨이다.
- 주요 시작점은 \`ServiceLauncher\`, \`Server\`, \`ServiceListener\`, \`SocketService\`, \`Gateway\`다.
- 구조 분석에는 \`tools/code-index/out/index.json\`을 우선 사용하고, 소스가 바뀌면 \`tools/code-index/refresh.sh\`로 갱신한다.
- 변경 전 대상 파일, 직접 연결된 코드와 가장 가까운 검증만 확인한다.

## 안전

- 전화번호, 인증번호, CI/DI, 원문 전문, DB 계정, 인증서, API 키와 배포 비밀값을 출력하거나 커밋하지 않는다.
- 운영 DB, KCB, LG U+ 또는 내부 MCI 주소로 실제 연결하지 않는다.
- \`String.getBytes()\`와 \`new String(byte[])\`의 기본 문자셋 동작을 임의로 바꾸지 않는다. 문자셋 변경은 바이트 단위 회귀 테스트와 함께 수행한다.
- 고정 길이 필드의 공백 패딩, 절단, 한글 바이트 길이와 전문 총길이를 보존한다.
- 누락된 \`libs/\` JAR, 외부 \`conf\` 파일, DB DDL과 전문 샘플을 추정해 만들지 않는다. 없으면 검증 불가로 보고한다.
- 배포 inventory의 내부 주소와 계정명은 비밀값처럼 취급하며 최종 답변에 그대로 나열하지 않는다.

## 작업 방식

- 메인 Worktree는 코디네이터와 통합 판단에만 사용한다. 구현은 Orca Task에 연결된 새 하위 Worktree에서 수행한다.
- 같은 파일을 수정하는 작업은 병렬화하지 않는다.
- 범위가 명확한 정적 분석에는 \`$legacy-java-analysis\`, 전문 변경에는 \`$protocol-regression\`, JDK 전환에는 \`$jdk21-migration\`을 사용한다.
- 로컬 Qwen 결과는 참고 증거로만 사용하고 Codex가 원본 코드와 대조한다.
- Qwen 3.8 Max는 보안, 데이터 손상, 프로토콜 호환성 또는 모델 간 결론 충돌 검토에만 작업당 1회 사용한다.
- 요청 범위를 벗어난 리팩터링과 기존 결함 수정은 하지 않는다.

## 검증

- 현재 \`pom.xml\`은 \`libs/\`의 시스템 범위 JAR를 요구한다. 의존성이 없으면 Maven 실패를 코드 회귀로 단정하지 않는다.
- \`src/main/java/scbk/cert/test\`는 자동화된 단위 테스트 디렉터리가 아니다.
- 가장 좁은 컴파일 또는 테스트부터 실행하고, 실행하지 못한 검증과 이유를 최종 보고한다.
- 완료 보고에는 담당 AI, Worktree, 변경 파일, 실행한 검증, 미검증 항목과 Qwen 3.8 Max 호출 여부를 포함한다.
