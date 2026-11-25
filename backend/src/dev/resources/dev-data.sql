-- Demo data for the local "dev" profile (H2), same structure and codes as CUBEDB.
-- Every user's password is: Passw0rd!
--   admin1    Administrator (users, groups, permissions)
--   mgr1      Manager (approves, edits, deletes)
--   emp1/emp2 Employees
--   inactive1 Deactivated (cannot sign in)

INSERT INTO CU_MODULE_MASTER (MODULE_CODE, MODULE_NAME, DESCRIPTION, STATUS, CREATED_AT) VALUES
  ('AUTH', 'Auth & Access Administration', 'Manage users, groups, modules, access codes', 'A', CURRENT_TIMESTAMP),
  ('KNOWLEDGE', 'Knowledge Management', 'Search/view/download/create/approve knowledge', 'A', CURRENT_TIMESTAMP);

INSERT INTO CU_ACCESS_MASTER (MODULE_ID, ACCESS_CODE, DESCRIPTION, STATUS, CREATED_AT)
SELECT m.MODULE_ID, x.CODE, x.DESCR, 'A', CURRENT_TIMESTAMP FROM CU_MODULE_MASTER m, (VALUES
  ('MANAGE_USERS', 'Create/list/update users, assign groups'),
  ('MANAGE_GROUPS', 'CRUD on groups'),
  ('MANAGE_MODULES', 'CRUD on modules'),
  ('MANAGE_ACCESS', 'CRUD on access codes within a module'),
  ('MANAGE_GROUP_ACCESS', 'Set group -> access-code allow/deny'),
  ('MANAGE_USER_ACCESS', 'Set per-user access overrides')) AS x(CODE, DESCR)
WHERE m.MODULE_CODE = 'AUTH';

INSERT INTO CU_ACCESS_MASTER (MODULE_ID, ACCESS_CODE, DESCRIPTION, STATUS, CREATED_AT)
SELECT m.MODULE_ID, x.CODE, x.DESCR, 'A', CURRENT_TIMESTAMP FROM CU_MODULE_MASTER m, (VALUES
  ('CREATE', 'Submit new knowledge for review'),
  ('APPROVE', 'Approve or reject pending knowledge'),
  ('VIEW', 'View a knowledge article'),
  ('SEARCH', 'Search published knowledge'),
  ('DOWNLOAD', 'Download an attachment'),
  ('UPDATE', 'Edit an existing knowledge item'),
  ('DELETE', 'Delete a knowledge item')) AS x(CODE, DESCR)
WHERE m.MODULE_CODE = 'KNOWLEDGE';

INSERT INTO CU_GROUP_MASTER (GROUP_CODE, GROUP_NAME, DESCRIPTION, STATUS, CREATED_AT) VALUES
  ('ADMIN', 'Administrator', 'Manages users, groups, and permissions', 'A', CURRENT_TIMESTAMP),
  ('EMPLOYEE', 'Employee', 'Can search/view/download and submit for review', 'A', CURRENT_TIMESTAMP),
  ('MANAGER', 'Manager', 'Reviews and approves/rejects submissions', 'A', CURRENT_TIMESTAMP);

INSERT INTO CU_GROUP_ACCESS_TEMPLATE (GROUP_ID, ACCESS_ID, ALLOWED, STATUS, CREATED_AT)
SELECT g.GROUP_ID, a.ACCESS_ID, 'Y', 'A', CURRENT_TIMESTAMP FROM CU_GROUP_MASTER g, CU_ACCESS_MASTER a, CU_MODULE_MASTER m
WHERE g.GROUP_CODE = 'ADMIN' AND a.MODULE_ID = m.MODULE_ID AND m.MODULE_CODE = 'AUTH';
INSERT INTO CU_GROUP_ACCESS_TEMPLATE (GROUP_ID, ACCESS_ID, ALLOWED, STATUS, CREATED_AT)
SELECT g.GROUP_ID, a.ACCESS_ID, 'Y', 'A', CURRENT_TIMESTAMP FROM CU_GROUP_MASTER g, CU_ACCESS_MASTER a, CU_MODULE_MASTER m
WHERE g.GROUP_CODE = 'EMPLOYEE' AND a.MODULE_ID = m.MODULE_ID AND m.MODULE_CODE = 'KNOWLEDGE'
  AND a.ACCESS_CODE IN ('CREATE', 'VIEW', 'SEARCH', 'DOWNLOAD');
INSERT INTO CU_GROUP_ACCESS_TEMPLATE (GROUP_ID, ACCESS_ID, ALLOWED, STATUS, CREATED_AT)
SELECT g.GROUP_ID, a.ACCESS_ID, 'Y', 'A', CURRENT_TIMESTAMP FROM CU_GROUP_MASTER g, CU_ACCESS_MASTER a, CU_MODULE_MASTER m
WHERE g.GROUP_CODE = 'MANAGER' AND a.MODULE_ID = m.MODULE_ID AND m.MODULE_CODE = 'KNOWLEDGE';

INSERT INTO CU_USER_MASTER (USERNAME, EMAIL, PASSWORD_HASH, FIRST_NAME, LAST_NAME, STATUS, CREATED_AT, UPDATED_AT) VALUES
  ('admin1', 'admin1@example.com', '$2a$10$eK5RGn5/aL2ck8YqYF8xz.RcnC4Wp9texLCh2U3EliBN1N1IXR2DW', 'System', 'Admin', 'A', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('mgr1', 'mgr1@example.com', '$2a$10$eK5RGn5/aL2ck8YqYF8xz.RcnC4Wp9texLCh2U3EliBN1N1IXR2DW', 'Meera', 'Iyer', 'A', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('emp1', 'emp1@example.com', '$2a$10$eK5RGn5/aL2ck8YqYF8xz.RcnC4Wp9texLCh2U3EliBN1N1IXR2DW', 'Priya', 'Sharma', 'A', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('emp2', 'emp2@example.com', '$2a$10$eK5RGn5/aL2ck8YqYF8xz.RcnC4Wp9texLCh2U3EliBN1N1IXR2DW', 'Rahul', 'Verma', 'A', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('inactive1', 'inactive1@example.com', '$2a$10$eK5RGn5/aL2ck8YqYF8xz.RcnC4Wp9texLCh2U3EliBN1N1IXR2DW', 'Former', 'Employee', 'I', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO CU_USER_GROUP (USER_ID, GROUP_ID, STATUS, CREATED_AT)
SELECT u.USER_ID, g.GROUP_ID, 'A', CURRENT_TIMESTAMP FROM CU_USER_MASTER u, CU_GROUP_MASTER g
WHERE (u.USERNAME = 'admin1' AND g.GROUP_CODE = 'ADMIN')
   OR (u.USERNAME = 'mgr1' AND g.GROUP_CODE = 'MANAGER')
   OR (u.USERNAME IN ('emp1', 'emp2', 'inactive1') AND g.GROUP_CODE = 'EMPLOYEE');

-- Two published sample articles so Discover isn't empty.
INSERT INTO CU_KNOWLEDGE (TITLE, DESCRIPTION, STATUS, CREATED_BY, REVIEWED_BY, REVIEWED_AT, PUBLISHED_AT, SEARCH_TEXT, CREATED_AT, UPDATED_AT)
SELECT 'How to configure VPN on Windows 11',
  'Use this guide to connect to the corporate network from home.' || CHAR(10) || '1. Install the VPN client from the IT portal.' || CHAR(10) || '2. Import the company profile.' || CHAR(10) || '3. Sign in and approve the MFA prompt.',
  'PUBLISHED', u.USER_ID, u.USER_ID, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
  'How to configure VPN on Windows 11 connect corporate network install client profile MFA vpn network',
  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM CU_USER_MASTER u WHERE u.USERNAME = 'mgr1';

INSERT INTO CU_KNOWLEDGE (TITLE, DESCRIPTION, STATUS, CREATED_BY, REVIEWED_BY, REVIEWED_AT, PUBLISHED_AT, SEARCH_TEXT, CREATED_AT, UPDATED_AT)
SELECT 'Expense claim process',
  'Submit travel and meal expenses within 30 days with receipts attached. Claims above the limit need manager approval.',
  'PUBLISHED', u.USER_ID, u.USER_ID, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
  'Expense claim process travel meal receipts approval finance',
  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM CU_USER_MASTER u WHERE u.USERNAME = 'mgr1';

INSERT INTO CU_KEYWORD (KEYWORD_NAME) VALUES ('vpn'), ('network'), ('finance');
INSERT INTO CU_KNOWLEDGE_KEYWORD (KEYWORD_ID, KNOWLEDGE_ID)
SELECT kw.KEYWORD_ID, k.KNOWLEDGE_ID FROM CU_KEYWORD kw, CU_KNOWLEDGE k
WHERE (k.TITLE = 'How to configure VPN on Windows 11' AND kw.KEYWORD_NAME IN ('vpn', 'network'))
   OR (k.TITLE = 'Expense claim process' AND kw.KEYWORD_NAME = 'finance');

-- Reporting lines: emp1 and emp2 report to mgr1.
UPDATE CU_USER_MASTER SET REPORTING_MANAGER_ID = (SELECT USER_ID FROM CU_USER_MASTER WHERE USERNAME = 'mgr1')
 WHERE USERNAME IN ('emp1', 'emp2');
