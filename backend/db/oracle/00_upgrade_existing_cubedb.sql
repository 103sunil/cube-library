-- =====================================================================
-- Cube - upgrade the EXISTING CUBEDB schema (created by the previous version of Cube)
-- so the new application can use it. Run ONCE as CUBEDB.
--
-- What it does:
--   * ADDS columns that the new code needs (with defaults) and fills them from existing data
--   * CREATES two new tables (CU_REVOKED_TOKEN, CU_AUDIT_LOG)
-- What it does NOT do:
--   * it drops nothing, deletes nothing and changes no existing value
--
-- Re-running is harmless: every step checks first and skips work that is already done.
-- Oracle Text index CU_KNOWLEDGE_SEARCH_IDX already exists, so 03_oracle_text.sql is NOT needed here.
-- =====================================================================
SET SERVEROUTPUT ON

DECLARE
    PROCEDURE add_column(p_table VARCHAR2, p_column VARCHAR2, p_definition VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_definition || ')';
            DBMS_OUTPUT.PUT_LINE('added ' || p_table || '.' || p_column);
        ELSE
            DBMS_OUTPUT.PUT_LINE('exists ' || p_table || '.' || p_column);
        END IF;
    END;

    PROCEDURE run_if_missing(p_object VARCHAR2, p_ddl VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_OBJECTS WHERE OBJECT_NAME = p_object;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE('created ' || p_object);
        ELSE
            DBMS_OUTPUT.PUT_LINE('exists ' || p_object);
        END IF;
    END;
BEGIN
    -- Active / inactive flags and creation dates on the RBAC tables (existing rows become active)
    add_column('CU_GROUP_MASTER',          'STATUS',     q'[VARCHAR2(1 CHAR) DEFAULT 'A' NOT NULL]');
    add_column('CU_GROUP_MASTER',          'CREATED_AT', 'TIMESTAMP DEFAULT SYSTIMESTAMP');
    add_column('CU_USER_GROUP',            'STATUS',     q'[VARCHAR2(1 CHAR) DEFAULT 'A' NOT NULL]');
    add_column('CU_USER_GROUP',            'CREATED_AT', 'TIMESTAMP DEFAULT SYSTIMESTAMP');
    add_column('CU_MODULE_MASTER',         'STATUS',     q'[VARCHAR2(1 CHAR) DEFAULT 'A' NOT NULL]');
    add_column('CU_MODULE_MASTER',         'CREATED_AT', 'TIMESTAMP DEFAULT SYSTIMESTAMP');
    add_column('CU_ACCESS_MASTER',         'STATUS',     q'[VARCHAR2(1 CHAR) DEFAULT 'A' NOT NULL]');
    add_column('CU_ACCESS_MASTER',         'CREATED_AT', 'TIMESTAMP DEFAULT SYSTIMESTAMP');
    add_column('CU_GROUP_ACCESS_TEMPLATE', 'STATUS',     q'[VARCHAR2(1 CHAR) DEFAULT 'A' NOT NULL]');
    add_column('CU_GROUP_ACCESS_TEMPLATE', 'CREATED_AT', 'TIMESTAMP DEFAULT SYSTIMESTAMP');
    add_column('CU_USER_ACCESS',           'STATUS',     q'[VARCHAR2(1 CHAR) DEFAULT 'A' NOT NULL]');
    add_column('CU_USER_ACCESS',           'CREATED_AT', 'TIMESTAMP DEFAULT SYSTIMESTAMP');

    -- Knowledge: last change and publish dates
    add_column('CU_KNOWLEDGE', 'UPDATED_AT',   'TIMESTAMP');
    add_column('CU_KNOWLEDGE', 'PUBLISHED_AT', 'TIMESTAMP');

    -- Attachments: who uploaded the file
    add_column('CU_ATTACHMENT', 'UPLOADED_BY', 'NUMBER(19)');

    -- Logout (revoked tokens) and audit log
    run_if_missing('CU_REVOKED_TOKEN', q'[
        CREATE TABLE CU_REVOKED_TOKEN (
            TOKEN_ID    VARCHAR2(64)       NOT NULL,
            USERNAME    VARCHAR2(100 CHAR),
            EXPIRES_AT  TIMESTAMP          NOT NULL,
            REVOKED_AT  TIMESTAMP          NOT NULL,
            CONSTRAINT CU_REVOKED_TOKEN_PK PRIMARY KEY (TOKEN_ID))]');
    run_if_missing('CU_REVOKED_TOKEN_EXP_IX', 'CREATE INDEX CU_REVOKED_TOKEN_EXP_IX ON CU_REVOKED_TOKEN (EXPIRES_AT)');

    run_if_missing('CU_AUDIT_LOG', q'[
        CREATE TABLE CU_AUDIT_LOG (
            AUDIT_ID     NUMBER(19) GENERATED ALWAYS AS IDENTITY,
            ACTOR        VARCHAR2(100 CHAR),
            ACTION       VARCHAR2(50 CHAR)   NOT NULL,
            ENTITY_TYPE  VARCHAR2(50 CHAR),
            ENTITY_ID    VARCHAR2(100 CHAR),
            DETAILS      VARCHAR2(1000 CHAR),
            CREATED_AT   TIMESTAMP           NOT NULL,
            CONSTRAINT CU_AUDIT_LOG_PK PRIMARY KEY (AUDIT_ID))]');
    run_if_missing('CU_AUDIT_LOG_CREATED_IX', 'CREATE INDEX CU_AUDIT_LOG_CREATED_IX ON CU_AUDIT_LOG (CREATED_AT)');
END;
/

-- Fill the new columns from existing data (only where still empty).
UPDATE CU_KNOWLEDGE SET UPDATED_AT = COALESCE(REVIEWED_AT, CREATED_AT, SYSTIMESTAMP) WHERE UPDATED_AT IS NULL;
UPDATE CU_KNOWLEDGE SET PUBLISHED_AT = COALESCE(REVIEWED_AT, CREATED_AT) WHERE STATUS = 'PUBLISHED' AND PUBLISHED_AT IS NULL;
UPDATE CU_ATTACHMENT a
   SET UPLOADED_BY = (SELECT k.CREATED_BY FROM CU_KNOWLEDGE k WHERE k.KNOWLEDGE_ID = a.KNOWLEDGE_ID)
 WHERE UPLOADED_BY IS NULL;
COMMIT;

-- Check: should list the new columns and both new tables.
SELECT TABLE_NAME, COLUMN_NAME FROM USER_TAB_COLUMNS
 WHERE (COLUMN_NAME IN ('STATUS', 'CREATED_AT', 'UPDATED_AT', 'PUBLISHED_AT', 'UPLOADED_BY') AND TABLE_NAME LIKE 'CU\_%' ESCAPE '\')
    OR TABLE_NAME IN ('CU_REVOKED_TOKEN', 'CU_AUDIT_LOG')
 ORDER BY TABLE_NAME, COLUMN_NAME;
