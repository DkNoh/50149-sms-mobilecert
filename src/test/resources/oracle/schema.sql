-- SYNTHETIC TEST DDL ONLY. This is not the production Oracle schema.
-- Inferred from KCBDAO and its request/response beans for local integration tests.
-- Run only inside a disposable MCERTIT<hex> PDB with CURRENT_SCHEMA = SMS.
-- Creates new objects; never drops or replaces pre-existing objects.
-- Object creation is auto-committed by Oracle. The caller owns PDB cleanup.
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET ECHO OFF
SET VERIFY OFF
SET DEFINE OFF

BEGIN
    IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'SMS'
       OR NOT REGEXP_LIKE(SYS_CONTEXT('USERENV', 'CON_NAME'), '^MCERTIT[0-9A-F]+$') THEN
        RAISE_APPLICATION_ERROR(-20001,
            'Synthetic DDL requires the SMS schema in a disposable MCERTIT PDB');
    END IF;
END;
/

CREATE TABLE MCERT_IT_MARKER (
    MARKER_KEY   VARCHAR2(32 CHAR) NOT NULL,
    MARKER_VALUE VARCHAR2(128 CHAR) NOT NULL,
    CONSTRAINT PK_MCERT_IT_MARKER PRIMARY KEY (MARKER_KEY)
);

COMMENT ON TABLE MCERT_IT_MARKER IS
    'Synthetic local mobilecert integration-test ownership marker; not production DDL';

CREATE TABLE DEP (
    DEP_ID VARCHAR2(32 CHAR) NOT NULL,
    ACT_YN VARCHAR2(1 CHAR) NOT NULL,
    CONSTRAINT PK_DEP PRIMARY KEY (DEP_ID),
    CONSTRAINT CK_DEP_ACT_YN CHECK (ACT_YN IN ('Y', 'N'))
);

COMMENT ON TABLE DEP IS
    'Synthetic fixture for KCBDAO.selectDeptCode; not production DDL';

CREATE SEQUENCE MBLPHN_SELF_CERT_SEQ
    START WITH 1 INCREMENT BY 1 MAXVALUE 2147483647 NOCACHE NOCYCLE;

CREATE TABLE KCB_CERT (
    TRAN_DT               VARCHAR2(8 CHAR) NOT NULL,
    TRAN_SERNO            NUMBER(10) NOT NULL,
    TRAN_TM               VARCHAR2(6 CHAR),
    DEPT_CD               VARCHAR2(32 CHAR),
    CERT_PRGRSS_PHASE_MK   VARCHAR2(5 CHAR),
    TREATE_MK             VARCHAR2(1 CHAR),
    CLERK_NO              VARCHAR2(32 CHAR),
    TRAN_NO               VARCHAR2(24 CHAR),
    MBL_COMM_MK           VARCHAR2(1 CHAR),
    MBLPHN_NO             VARCHAR2(12 CHAR),
    HLDR_SSN              VARCHAR2(13 CHAR),
    CERT_NO_SMS_SND_FLG    VARCHAR2(10 CHAR),
    CERT_NO               VARCHAR2(6 CHAR),
    CALLBACK_NO           VARCHAR2(12 CHAR),
    MRCHNT_NM             VARCHAR2(20 CHAR),
    REAL_NM_CNFRM_IND     VARCHAR2(1 CHAR),
    NM                    VARCHAR2(15 CHAR),
    CERT_TRAN_SERNO       NUMBER(10),
    CERT_TRAN_DTM         VARCHAR2(14 CHAR),
    CERT_MODE             VARCHAR2(1 CHAR),
    TRAN_RSPNS_CD         VARCHAR2(4 CHAR),
    TRAN_RSPNS_MSG        VARCHAR2(400 CHAR),
    TRAN_SVC_RSPNS_CD     VARCHAR2(4 CHAR),
    TRAN_SVC_RSPNS_MSG    VARCHAR2(256 CHAR),
    CONSTRAINT PK_KCB_CERT PRIMARY KEY (TRAN_DT, TRAN_SERNO)
);

COMMENT ON TABLE KCB_CERT IS
    'Synthetic KCBDAO request/result fixture; not production DDL or a monthly table';

CREATE TABLE KCB_INFO (
    TRAN_DT    VARCHAR2(8 CHAR) NOT NULL,
    TRAN_SERNO NUMBER(10) NOT NULL,
    ETC1       VARCHAR2(192 CHAR),
    ETC2       VARCHAR2(160 CHAR),
    CONSTRAINT PK_KCB_INFO PRIMARY KEY (TRAN_DT, TRAN_SERNO),
    -- Test-only sentinel forces the second statement in updateStep1 to fail.
    -- It is a deliberately invented value, never a real CI/DI or packet.
    CONSTRAINT CK_KCB_INFO_TEST_FAIL CHECK (ETC2 <> 'SYNTHETIC_FORCE_FAILURE')
);

COMMENT ON TABLE KCB_INFO IS
    'Synthetic CI/DI persistence fixture with deliberate failure injection; not production DDL';
COMMENT ON COLUMN KCB_INFO.ETC1 IS 'Synthetic value representing KCB response CI';
COMMENT ON COLUMN KCB_INFO.ETC2 IS 'Synthetic value representing KCB response DI';

INSERT INTO DEP (DEP_ID, ACT_YN) VALUES ('TEST_ACTIVE', 'Y');
INSERT INTO DEP (DEP_ID, ACT_YN) VALUES ('TEST_INACTIVE', 'N');
INSERT INTO MCERT_IT_MARKER (MARKER_KEY, MARKER_VALUE)
    VALUES ('SYNTHETIC_SCHEMA', 'MOBILECERT_ORACLE_IT_V1');
COMMIT;

PROMPT Synthetic mobilecert schema created.
