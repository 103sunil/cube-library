-- =====================================================================
-- Cube - permanently remove users that were deleted in the app (STATUS = 'D').
-- Run as CUBEDB in SQL Developer (F5). Review the output, then COMMIT is done at the end.
--
-- Their knowledge items are NOT deleted: authorship moves to the user named in v_new_owner
-- (must be an active user, normally a manager so they can still edit or archive the items).
--   CU_KNOWLEDGE.CREATED_BY        -> new owner
--   CU_ATTACHMENT.UPLOADED_BY      -> new owner
--   CU_KNOWLEDGE.REVIEWED_BY       -> NULL  (approver no longer exists)
--   CU_KNOWLEDGE.ASSIGNED_REVIEWER -> NULL  (waiting items go to the shared queue)
--   bookmarks, group membership, permission overrides, revoked tokens -> deleted
--   CU_AUDIT_LOG                   -> kept (history; stores the username as text)
-- Safe to re-run: when no STATUS = 'D' users are left it does nothing.
-- =====================================================================
SET SERVEROUTPUT ON

DECLARE
    v_new_owner  CONSTANT VARCHAR2(100) := 'suresh.p';
    v_owner_id   NUMBER;
    v_users      NUMBER;
BEGIN
    SELECT USER_ID INTO v_owner_id FROM CU_USER_MASTER WHERE USERNAME = v_new_owner AND STATUS = 'A';

    SELECT COUNT(*) INTO v_users FROM CU_USER_MASTER WHERE STATUS = 'D';
    DBMS_OUTPUT.PUT_LINE('deleted users to purge: ' || v_users);
    FOR u IN (SELECT USERNAME FROM CU_USER_MASTER WHERE STATUS = 'D' ORDER BY USERNAME) LOOP
        DBMS_OUTPUT.PUT_LINE('  - ' || u.USERNAME);
    END LOOP;
    IF v_users = 0 THEN
        RETURN;
    END IF;

    UPDATE CU_KNOWLEDGE SET CREATED_BY = v_owner_id
     WHERE CREATED_BY IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');
    DBMS_OUTPUT.PUT_LINE('knowledge items moved to ' || v_new_owner || ': ' || SQL%ROWCOUNT);

    UPDATE CU_ATTACHMENT SET UPLOADED_BY = v_owner_id
     WHERE UPLOADED_BY IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');
    DBMS_OUTPUT.PUT_LINE('attachments moved: ' || SQL%ROWCOUNT);

    UPDATE CU_KNOWLEDGE SET REVIEWED_BY = NULL
     WHERE REVIEWED_BY IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');
    DBMS_OUTPUT.PUT_LINE('reviewed-by cleared: ' || SQL%ROWCOUNT);

    UPDATE CU_KNOWLEDGE SET ASSIGNED_REVIEWER_ID = NULL
     WHERE ASSIGNED_REVIEWER_ID IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');
    DBMS_OUTPUT.PUT_LINE('pending reviews returned to shared queue: ' || SQL%ROWCOUNT);

    UPDATE CU_USER_MASTER SET REPORTING_MANAGER_ID = NULL
     WHERE REPORTING_MANAGER_ID IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');

    DELETE FROM CU_BOOKMARK    WHERE USER_ID IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');
    DELETE FROM CU_USER_ACCESS WHERE USER_ID IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');
    DELETE FROM CU_USER_GROUP  WHERE USER_ID IN (SELECT USER_ID FROM CU_USER_MASTER WHERE STATUS = 'D');
    DELETE FROM CU_REVOKED_TOKEN WHERE USERNAME IN (SELECT USERNAME FROM CU_USER_MASTER WHERE STATUS = 'D');

    DELETE FROM CU_USER_MASTER WHERE STATUS = 'D';
    DBMS_OUTPUT.PUT_LINE('users permanently removed: ' || SQL%ROWCOUNT);

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('done');
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('Nothing changed: active user ' || v_new_owner || ' not found. Edit v_new_owner at the top.');
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('Nothing changed: ' || SQLERRM);
        RAISE;
END;
/

-- Check: only active users remain
SELECT USERNAME, STATUS FROM CU_USER_MASTER ORDER BY USERNAME;
