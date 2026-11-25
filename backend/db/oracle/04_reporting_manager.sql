-- =====================================================================
-- Cube - reporting managers, review routing and bookmarks. Run as CUBEDB (or any Cube schema).
--   CU_USER_MASTER.REPORTING_MANAGER_ID  : who reviews this user's submissions
--   CU_KNOWLEDGE.ASSIGNED_REVIEWER_ID    : reviewer chosen when the item was submitted
-- Adds columns and indexes only; nothing is dropped or changed. Safe to re-run.
-- After running it, set each employee's reporting manager in Admin -> Users -> Edit.
-- =====================================================================
SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;

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

    PROCEDURE add_index(p_name VARCHAR2, p_ddl VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE('created ' || p_name);
        ELSE
            DBMS_OUTPUT.PUT_LINE('exists ' || p_name);
        END IF;
    END;
BEGIN
    add_column('CU_USER_MASTER', 'REPORTING_MANAGER_ID', 'NUMBER(19)');
    add_column('CU_KNOWLEDGE', 'ASSIGNED_REVIEWER_ID', 'NUMBER(19)');
    add_index('CU_USER_MASTER_MANAGER_IX', 'CREATE INDEX CU_USER_MASTER_MANAGER_IX ON CU_USER_MASTER (REPORTING_MANAGER_ID)');
    add_index('CU_KNOWLEDGE_REVIEWER_IX', 'CREATE INDEX CU_KNOWLEDGE_REVIEWER_IX ON CU_KNOWLEDGE (STATUS, ASSIGNED_REVIEWER_ID)');

    -- Bookmarks (saved items per user)
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'CU_BOOKMARK';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE CU_BOOKMARK (USER_ID NUMBER(19) NOT NULL, KNOWLEDGE_ID NUMBER(19) NOT NULL, '
            || 'CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP, CONSTRAINT CU_BOOKMARK_PK PRIMARY KEY (USER_ID, KNOWLEDGE_ID))';
        DBMS_OUTPUT.PUT_LINE('created CU_BOOKMARK');
    ELSE
        DBMS_OUTPUT.PUT_LINE('exists CU_BOOKMARK');
    END IF;
    add_index('CU_BOOKMARK_K_IX', 'CREATE INDEX CU_BOOKMARK_K_IX ON CU_BOOKMARK (KNOWLEDGE_ID)');
END;
/
