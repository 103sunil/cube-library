# Cube – Knowledge Repository API

Spring Boot 2.7 / Java 8 WAR for WebLogic, Oracle database, JWT auth.

## Setup

1. **Database** (as the schema owner, e.g. `CUBEDB`), in order:
   - `db/oracle/01_schema.sql` – tables and sequences (all `CU_`, no foreign keys)
   - `db/oracle/02_seed.sql` – modules, access codes, EMPLOYEE/MANAGER groups, user `admin` / `Admin@123` (**change it after first login**)
   - `db/oracle/03_oracle_text.sql` – search index. A DBA must first run `GRANT CTXAPP TO CUBEDB;`
2. **Configuration** – environment variables or `-D` JVM properties (WebLogic `JAVA_OPTIONS`):

   | Variable | Required | Notes |
   |---|---|---|
   | `CUBE_DB_PASSWORD` | yes | |
   | `CUBE_JWT_SECRET` | yes | at least 32 characters, random |
   | `CUBE_DB_URL`, `CUBE_DB_USERNAME` | no | default `//10.20.5.10:1521/FCUBS147X`, `CUBEDB` |
   | `CUBE_STORAGE_TYPE` | no | `github` (default) or `local` |
   | `CUBE_GITHUB_OWNER`, `CUBE_GITHUB_REPO`, `CUBE_GITHUB_TOKEN`, `CUBE_GITHUB_BRANCH` | for github | **placeholders until the account is decided**; until then uploads/downloads return 503 |
   | `CUBE_LOCAL_STORAGE_DIR` | for local | default `~/cube-files` |
   | `CUBE_SEARCH_MODE` | no | `oracle-text` (default) or `like` if Oracle Text isn't installed |
   | `CUBE_CORS_ALLOWED_ORIGINS` | dev only | e.g. `http://localhost:5173`; leave empty when the frontend is on the same origin |

   GitHub token: a fine-grained token limited to the one **private** repo, permission *Contents: Read and write*. Behind a proxy add `-Dhttps.proxyHost=… -Dhttps.proxyPort=…`.
3. **Build**: `mvn clean verify` → `target/cube.war` (tests use in-memory H2).

## Roles and permissions

Permission = `MODULE:ACCESS`. Effective set = group defaults (`CU_GROUP_ACCESS_TEMPLATE`) + unexpired per-user overrides (`CU_USER_ACCESS`; `allowed=N` removes). Re-read from the DB on every request, so changes apply immediately.

| Permission | EMPLOYEE | MANAGER | Meaning |
|---|---|---|---|
| KNOWLEDGE:VIEW | ✓ | ✓ | browse/search published |
| KNOWLEDGE:DOWNLOAD | ✓ | ✓ | download/preview attachments |
| KNOWLEDGE:SUBMIT | ✓ | ✓ | create and submit |
| KNOWLEDGE:APPROVE | | ✓ | review queue, approve/reject/archive; own submissions auto-publish |
| KNOWLEDGE:EDIT | | ✓ | edit any item |
| KNOWLEDGE:DELETE | | ✓ | delete any item |
| ADMIN:MANAGE | | ✓ | `/api/v1/admin/**` |

Lifecycle: `DRAFT → PENDING → PUBLISHED ⇄ ARCHIVED`, `PENDING → REJECTED → (owner edits) → PENDING`.
Visibility: PUBLISHED to anyone with VIEW; anything else only to the owner and reviewers (others get 404).

## API (all under `/api/v1`, `Authorization: Bearer <jwt>` except login/health)

**Auth**
| | |
|---|---|
| `POST /auth/login` `{username,password}` | → `{token, expiresIn, username, groupCode, permissions[]}`; 401 bad credentials, 403 inactive, 429 locked (5 failures → 15 min) |
| `POST /auth/logout` | revokes the token server-side |
| `GET /me` | profile + group |
| `GET /me/permissions` | `{permissions[], modules{KNOWLEDGE:[…]}}` – gate the UI with this |
| `GET /health` | public |

**Knowledge**
| | |
|---|---|
| `POST /knowledge/submit` (multipart) | fields `title`, `description`, `keywords` (repeat or comma-separated), `files` (repeat), optional `draft=true` |
| `POST /knowledge/draft` (multipart) | same fields, always DRAFT (description optional) |
| `GET /knowledge/published?page&size` | newest published first |
| `GET /knowledge/mine?status&page&size` | caller's own items |
| `GET /knowledge?status=PENDING\|REJECTED\|ARCHIVED\|DRAFT\|PUBLISHED` | approval tabs (reviewers) |
| `GET /knowledge/search?q&page&size` | published only, all words must match |
| `GET /knowledge/{id}` | detail incl. keywords, attachments and an `actions` block (`canEdit`, `canSubmit`, `canApprove`, `canDownload`, …) |
| `PUT /knowledge/{id}` JSON `{title, description, keywords[]}` | owner while DRAFT/REJECTED; managers any time. Status unchanged |
| `POST /knowledge/{id}/submit` | owner submits a DRAFT or **resubmits a REJECTED** item |
| `POST /knowledge/{id}/approve` · `/reject {reason}` · `/archive` · `/restore` | reviewers |
| `DELETE /knowledge/{id}` | managers; owners may delete their own DRAFT. Removes keywords, attachments and stored files |
| `GET /knowledge/{id}/attachments` · `POST` (multipart `files`) | list / add |
| `GET /attachments/{attachmentId}/download` · `/preview` | binary; fetch with the header and read as a blob |
| `DELETE /attachments/{attachmentId}` | owner until published, or reviewer |

Paged responses: `{content[], page, size, totalElements, totalPages}` (page is 0-based, size ≤ 100).

**Admin** (`ADMIN:MANAGE`)
`/admin/users` (GET list `?q`, POST, GET/PUT `/{id}`, PUT `/{id}/password`, GET `/{id}/permissions`, GET/PUT `/{id}/overrides`, DELETE `/{id}/overrides/{module}/{access}`) ·
`/admin/groups` (CRUD, GET/PUT `/{id}/grants`, DELETE `/{id}/grants/{module}/{access}`) ·
`/admin/modules`, `/admin/access-codes` (CRUD) · `GET /admin/audit?actor&page&size`.
Grant/override body: `{moduleCode, accessCode, allowed, expiryDate?}`.

**Errors** – always `{timestamp, status, error, message, path, fieldErrors?}`. Show `message` to the user; on 401 redirect to login.

Limits: 10MB per file, 20 files per request, 20 keywords (≤ 50 chars), title ≤ 300 chars.
