package scbk.cert.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;

/** 개발 XML과 기존 Oracle 테이블을 사용하는 KCB DAO 연동 검증이다. */
@Tag("db")
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("개발DB설정")
class KcbDatabaseTest {
    private DevelopmentDatabase database;

    @BeforeEach
    void 개발DB를_준비한다() {
        database = DevelopmentDatabase.open();
    }

    @AfterEach
    void 이번테스트데이터와_연결을_정리한다() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void 테스트코드1_XML설정으로_실제Oracle에연결한다() {
        // given
        DevelopmentDatabase configuredDatabase = database;

        // when
        boolean oracle = configuredDatabase.usesOracleConnection();

        // then
        assertTrue(oracle, "설정 파일로 실제 Oracle 연결을 생성해야 합니다.");
    }

    @Test
    void 테스트코드2_인증순번을_중복없이조회한다() {
        // given
        KCBDAO dao = database.dao();

        // when
        String first = dao.getSerno();
        String second = dao.getSerno();

        // then
        assertFalse(first.equals(second), "연속 조회한 인증 순번이 같으면 안 됩니다.");
    }

    @Test
    void 테스트코드3_한글인증이력을_정상등록한다() {
        // given
        KCBDAO dao = database.dao();
        String serial = dao.getSerno();
        KCBCertReqBean request = database.request();

        // when
        boolean inserted = dao.insert(serial, database.department(), "JUNIT", database.currentDtm(), request);

        // then
        assertTrue(inserted, "실제 DAO의 인증 이력 등록이 성공해야 합니다.");
        DevelopmentDatabase.Snapshot persisted = database.certificate();
        assertEquals("STEP1", persisted.phase());
        assertEquals("1", persisted.treatment());
        assertTrue(persisted.nameMatches(), "새 연결에서 등록된 한글 이름을 확인해야 합니다.");
        assertTrue(persisted.merchantMatches(), "새 연결에서 등록된 한글 상점명을 확인해야 합니다.");
        assertEquals(0, database.infoCount());
    }

    @Test
    void 테스트코드4_인증이력의_횟수와시각을조회한다() {
        // given
        KCBCertReqBean request = database.request();
        KCBDAO dao = database.dao();
        assertTrue(dao.insert(dao.getSerno(), database.department(), "JUNIT", database.currentDtm(), request),
                "조회할 인증 이력을 먼저 등록해야 합니다.");
        database.markLatestSuccessful();

        // when
        String[] check = dao.selectCheck(request);

        // then
        assertEquals("1", check[0], "성공한 두 번째 단계 이력이 한 건이어야 합니다.");
        assertEquals(database.currentDtm(), check[1], "등록 시각이 보존되어야 합니다.");
        assertNotNull(check[2], "DB 기준 경과 시간을 반환해야 합니다.");
        assertTrue(check[2].matches("[0-9]+"), "경과 시간은 음수가 아닌 숫자여야 합니다.");
    }

    @Test
    void 테스트코드5_활성부서를_정상조회한다() {
        // given
        String activeDepartment = database.department();

        // when
        boolean found = database.dao().selectDeptCode(activeDepartment);

        // then
        assertTrue(found, "기존 활성 부서를 실제 DAO로 조회해야 합니다.");
        assertEquals(0, database.ownedRowCount(), "부서 조회는 인증 이력을 생성하지 않아야 합니다.");
    }

    @Test
    void 테스트코드6_인증응답과부가이력을_정상저장한다() {
        // given
        KCBDAO dao = database.dao();
        String serial = dao.getSerno();
        assertTrue(dao.insert(serial, database.department(), "JUNIT", database.currentDtm(), database.request()),
                "응답을 저장할 인증 이력이 필요합니다.");
        KCBHeaderBean header = database.header();
        KCBCertResBean response = database.response();

        // when
        boolean updated = dao.updateStep1(serial, database.currentDtm(), header, response);

        // then
        assertTrue(updated, "인증 응답 저장이 성공해야 합니다.");
        DevelopmentDatabase.Snapshot persisted = database.certificate();
        assertEquals("9", persisted.treatment());
        assertEquals("0000", persisted.headerCode());
        assertEquals("0000", persisted.serviceCode());
        assertEquals(1, database.infoCount(), "부가 이력이 실제 테이블에 저장되어야 합니다.");
        assertTrue(database.infoMatchesSyntheticResponse(), "이번 테스트의 가상 인증 식별값이 저장되어야 합니다.");
    }
}