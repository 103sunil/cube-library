-- =====================================================================
-- Cube - employee ID, role/designation and "must change password". Run as CUBEDB.
--   EMPLOYEE_CODE         : company employee ID (unique when filled in)
--   DESIGNATION           : job title shown in the app (Employee, Team Lead, ...). Not a permission.
--   MUST_CHANGE_PASSWORD  : Y = user must set their own password at next login
-- Adds columns and an index only; nothing is dropped or changed. Safe to re-run.
-- =====================================================================
SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'CU_USER_MASTER' AND COLUMN_NAME = 'EMPLOYEE_CODE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CU_USER_MASTER ADD (EMPLOYEE_CODE VARCHAR2(30 CHAR))';
        DBMS_OUTPUT.PUT_LINE('added CU_USER_MASTER.EMPLOYEE_CODE');
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'CU_USER_MASTER' AND COLUMN_NAME = 'DESIGNATION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CU_USER_MASTER ADD (DESIGNATION VARCHAR2(100 CHAR))';
        DBMS_OUTPUT.PUT_LINE('added CU_USER_MASTER.DESIGNATION');
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'CU_USER_MASTER' AND COLUMN_NAME = 'MUST_CHANGE_PASSWORD';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE CU_USER_MASTER ADD (MUST_CHANGE_PASSWORD VARCHAR2(1 CHAR) DEFAULT 'N' NOT NULL)]';
        DBMS_OUTPUT.PUT_LINE('added CU_USER_MASTER.MUST_CHANGE_PASSWORD');
    END IF;

    -- Employee IDs must be unique (NULLs are allowed for users without one)
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'CU_USER_MASTER_EMPCODE_UX';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX CU_USER_MASTER_EMPCODE_UX ON CU_USER_MASTER (UPPER(EMPLOYEE_CODE))';
        DBMS_OUTPUT.PUT_LINE('created CU_USER_MASTER_EMPCODE_UX');
    END IF;
END;
/
