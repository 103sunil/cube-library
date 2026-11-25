-- =====================================================================
-- Cube - reference data for a NEW, EMPTY environment (after 01_schema.sql).
-- Same modules, access codes and groups as the existing CUBEDB. Run once.
-- First login: admin / Admin@123  (change it straight away).
-- =====================================================================

INSERT INTO CU_MODULE_MASTER (MODULE_CODE, MODULE_NAME, DESCRIPTION) VALUES
  ('AUTH', 'Auth & Access Administration', 'Manage users, groups, modules, access codes');
INSERT INTO CU_MODULE_MASTER (MODULE_CODE, MODULE_NAME, DESCRIPTION) VALUES
  ('KNOWLEDGE', 'Knowledge Management', 'Search/view/download/create/approve knowledge');

INSERT INTO CU_ACCESS_MASTER (MODULE_ID, ACCESS_CODE, DESCRIPTION)
SELECT m.MODULE_ID, x.CODE, x.DESCR FROM CU_MODULE_MASTER m, (
  SELECT 'MANAGE_USERS' CODE, 'Create/list/update users, assign groups' DESCR FROM DUAL UNION ALL
  SELECT 'MANAGE_GROUPS', 'CRUD on groups' FROM DUAL UNION ALL
  SELECT 'MANAGE_MODULES', 'CRUD on modules' FROM DUAL UNION ALL
  SELECT 'MANAGE_ACCESS', 'CRUD on access codes within a module' FROM DUAL UNION ALL
  SELECT 'MANAGE_GROUP_ACCESS', 'Set group -> access-code allow/deny' FROM DUAL UNION ALL
  SELECT 'MANAGE_USER_ACCESS', 'Set per-user access overrides' FROM DUAL) x
WHERE m.MODULE_CODE = 'AUTH';

INSERT INTO CU_ACCESS_MASTER (MODULE_ID, ACCESS_CODE, DESCRIPTION)
SELECT m.MODULE_ID, x.CODE, x.DESCR FROM CU_MODULE_MASTER m, (
  SELECT 'CREATE' CODE, 'Submit new knowledge for review' DESCR FROM DUAL UNION ALL
  SELECT 'APPROVE', 'Approve or reject pending knowledge' FROM DUAL UNION ALL
  SELECT 'VIEW', 'View a knowledge article' FROM DUAL UNION ALL
  SELECT 'SEARCH', 'Search published knowledge' FROM DUAL UNION ALL
  SELECT 'DOWNLOAD', 'Download an attachment' FROM DUAL UNION ALL
  SELECT 'UPDATE', 'Edit an existing knowledge item' FROM DUAL UNION ALL
  SELECT 'DELETE', 'Delete a knowledge item' FROM DUAL) x
WHERE m.MODULE_CODE = 'KNOWLEDGE';

INSERT INTO CU_GROUP_MASTER (GROUP_CODE, GROUP_NAME, DESCRIPTION) VALUES ('ADMIN', 'Administrator', 'Manages users, groups, and permissions');
INSERT INTO CU_GROUP_MASTER (GROUP_CODE, GROUP_NAME, DESCRIPTION) VALUES ('EMPLOYEE', 'Employee', 'Can search/view/download and submit for review');
INSERT INTO CU_GROUP_MASTER (GROUP_CODE, GROUP_NAME, DESCRIPTION) VALUES ('MANAGER', 'Manager', 'Reviews and approves/rejects submissions');

-- ADMIN: every AUTH access code
INSERT INTO CU_GROUP_ACCESS_TEMPLATE (GROUP_ID, ACCESS_ID, ALLOWED)
SELECT g.GROUP_ID, a.ACCESS_ID, 'Y' FROM CU_GROUP_MASTER g, CU_ACCESS_MASTER a, CU_MODULE_MASTER m
WHERE g.GROUP_CODE = 'ADMIN' AND a.MODULE_ID = m.MODULE_ID AND m.MODULE_CODE = 'AUTH';
-- EMPLOYEE: create, view, search, download
INSERT INTO CU_GROUP_ACCESS_TEMPLATE (GROUP_ID, ACCESS_ID, ALLOWED)
SELECT g.GROUP_ID, a.ACCESS_ID, 'Y' FROM CU_GROUP_MASTER g, CU_ACCESS_MASTER a, CU_MODULE_MASTER m
WHERE g.GROUP_CODE = 'EMPLOYEE' AND a.MODULE_ID = m.MODULE_ID AND m.MODULE_CODE = 'KNOWLEDGE'
  AND a.ACCESS_CODE IN ('CREATE', 'VIEW', 'SEARCH', 'DOWNLOAD');
-- MANAGER: every KNOWLEDGE access code
INSERT INTO CU_GROUP_ACCESS_TEMPLATE (GROUP_ID, ACCESS_ID, ALLOWED)
SELECT g.GROUP_ID, a.ACCESS_ID, 'Y' FROM CU_GROUP_MASTER g, CU_ACCESS_MASTER a, CU_MODULE_MASTER m
WHERE g.GROUP_CODE = 'MANAGER' AND a.MODULE_ID = m.MODULE_ID AND m.MODULE_CODE = 'KNOWLEDGE';

-- First administrator: admin / Admin@123
INSERT INTO CU_USER_MASTER (USERNAME, EMAIL, PASSWORD_HASH, FIRST_NAME, LAST_NAME, STATUS, CREATED_AT, UPDATED_AT)
VALUES ('admin', 'admin@example.com', '$2a$10$3VTTuUZYNopxCZpiEhKKoeU4gev62cG2hnpNTvxeZiocYylIoPoCW',
        'System', 'Admin', 'A', SYSTIMESTAMP, SYSTIMESTAMP);
INSERT INTO CU_USER_GROUP (USER_ID, GROUP_ID)
SELECT u.USER_ID, g.GROUP_ID FROM CU_USER_MASTER u, CU_GROUP_MASTER g
WHERE u.USERNAME = 'admin' AND g.GROUP_CODE = 'ADMIN';

COMMIT;
