# 가상 Oracle 스키마 범위

`src/test/resources/oracle/schema.sql`은 **로컬 통합 테스트 전용 가상 DDL**이다. 운영 DDL을 복원하거나 제공받은 결과가 아니다. 사용자 요청에 따라 현재 `KCBDAO` SQL과 bean 필드로부터 테스트에 필요한 객체만 만들었다. 운영 코드나 운영 DB는 변경하지 않는다.

`KCBDAO`가 `SMS.`를 SQL에 직접 붙이므로 사용자 이름만 바꾸거나 `CURRENT_SCHEMA`를 지정하는 것으로 격리할 수 없다. 실행 도구는 기존 Oracle 컨테이너 안에 별도 `MCERTIT<hex>` PDB를 만들고, 그 PDB의 `SMS` 스키마를 사용한다. DDL 자체도 현재 PDB 이름과 스키마를 확인하고, 기존 객체를 삭제하거나 덮어쓰지 않는다. `MCERT_IT_MARKER`는 테스트가 전용 가상 스키마에 연결되었는지 확인하는 표식이다.

| 가상 객체 | 소스에서 확인한 용도 | 테스트 데이터 |
| --- | --- | --- |
| `SMS.DEP` | `selectDeptCode()`의 `DEP_ID`, `ACT_YN` 조회 | `TEST_ACTIVE`는 `Y`, `TEST_INACTIVE`는 `N` |
| `SMS.MBLPHN_SELF_CERT_SEQ` | `getSerno()`의 `NEXTVAL` 조회 | 1부터 증가하는 테스트 전용 시퀀스 |
| `SMS.KCB_CERT` | `insert()`, `selectCheck()`, `updateStep1()` | JUnit이 생성한 가상 요청 및 응답 |
| `SMS.KCB_INFO` | `updateStep1()`의 두 번째 INSERT | 가상 CI/DI 문자열 |
| `SMS.MCERT_IT_MARKER` | 테스트 연결의 대상 확인 | `SYNTHETIC_SCHEMA=MOBILECERT_ORACLE_IT_V1` |

`KCB_CERT`의 입력 컬럼은 `TRAN_DT`, `TRAN_SERNO`, `TRAN_TM`, `DEPT_CD`, `CERT_PRGRSS_PHASE_MK`, `TREATE_MK`, `CLERK_NO`, `TRAN_NO`, `MBL_COMM_MK`, `MBLPHN_NO`, `HLDR_SSN`, `CERT_NO_SMS_SND_FLG`, `CERT_NO`, `CALLBACK_NO`, `MRCHNT_NM`, `REAL_NM_CNFRM_IND`, `NM`이다. 응답 갱신에는 `CERT_TRAN_SERNO`, `CERT_TRAN_DTM`, `CERT_MODE`, `TRAN_RSPNS_CD`, `TRAN_RSPNS_MSG`, `TRAN_SVC_RSPNS_CD`, `TRAN_SVC_RSPNS_MSG`도 필요하다. `KCB_INFO`는 `TRAN_DT`, `TRAN_SERNO`, `ETC1`(CI), `ETC2`(DI)를 사용한다.

문자열 길이는 전문 bean에서 확인한 필드 길이를 참고했지만, 가상 DDL은 명시적으로 `CHAR` 길이 단위를 사용한다. 부서·직원 코드와 응답 설명 등 실제 DB 길이를 알 수 없는 컬럼에는 테스트용 여유 길이를 정했다. 한글 왕복 저장을 확인할 수 있으나, 운영 DB의 `BYTE/CHAR` 길이 제한·문자셋·인덱스·권한·트리거·기본값·제약조건 호환성까지 검증한 것은 아니다. 시퀀스 상한과 기본키 역시 테스트용 선택이다.

`KCB_INFO.CK_KCB_INFO_TEST_FAIL` 제약은 DI 역할의 가상 값이 `SYNTHETIC_FORCE_FAILURE`일 때 INSERT를 거부한다. 이는 `updateStep1()`에서 첫 UPDATE가 성공한 후 두 번째 INSERT가 실패하는 상황을 재현하기 위한 **의도적인 테스트 제약**이다. 운영에 동일한 제약이 있다는 뜻이 아니다. 별도 연결에서 남은 행을 조회하면 실제 Oracle/Hikari의 commit 동작과 DAO의 반환값을 함께 확인할 수 있다.

이 스키마는 `new KCBDAO("KCB_CERT", "KCB_INFO")`를 통한 DAO 통합 테스트에 사용한다. `Gateway`가 설정한 테이블명 뒤에 `yyyyMM`을 붙이는 월별 테이블 운영 및 월 전환, 실제 KCB·MCI 연결은 이 가상 DDL만으로 검증하지 않는다. 이전 JDBC 드라이버·풀의 동작과 비교하거나 운영 버전 Oracle을 검증하려면 해당 환경과 실제 운영 DDL을 별도로 확보해야 한다.
