# CUBE – Knowledge Repository

| Folder | What | Deploys as |
|---|---|---|
| [backend/](backend/) | Spring Boot 2.7 API, Java 8, Oracle (schema `CUBEDB`) | `cube.war` → `/cube` |
| [frontend/](frontend/) | React 19 + MUI single-page app | `frontend.war` → `/frontend` |


## 1. Database (schema CUBEDB)

**CUBEDB already holds the data of the previous version** (91 items, 113 attachments, 6 users) and has been
upgraded for this version with `backend/db/oracle/00_upgrade_existing_cubedb.sql` (adds columns and two tables,
deletes nothing; safe to re-run). The Oracle Text index `CU_KNOWLEDGE_SEARCH_IDX` already exists.
**Do not run 01/02/03 or 99 on CUBEDB.**

Later additions, also additive and safe to re-run on CUBEDB (run each once in SQL Developer, F5, **before** starting
the new backend, or login fails):

- `04_reporting_manager.sql`: reporting manager, assigned reviewer and bookmarks (already run).
- `05_employee_details.sql`: employee ID, role/designation and "must change password".



**Roles (from the existing data):**

| Group | Permissions | Sees |
|---|---|---|
| ADMIN | `AUTH:MANAGE_USERS, MANAGE_GROUPS, MANAGE_MODULES, MANAGE_ACCESS, MANAGE_GROUP_ACCESS, MANAGE_USER_ACCESS` | Admin only |
| MANAGER | `KNOWLEDGE:CREATE, APPROVE, VIEW, SEARCH, DOWNLOAD, UPDATE, DELETE` | Discover, Submit, My uploads, Approvals |
| EMPLOYEE | `KNOWLEDGE:CREATE, VIEW, SEARCH, DOWNLOAD` | Discover, Submit, My uploads |

Change them in **Admin → Groups & permissions**. Users `manish.employee` and `suresh.manager` have no group yet,
so they can sign in but see nothing until one is assigned (Admin → Users → Edit).

**Old attachments:** 2 are stored inside the database and work as they are. The other 111 were saved on the disks of
the machines that ran the previous version.

**Onboarding teams (managers and employees):**

1. **Managers** are ordinary users in the `MANAGER` group. An administrator creates them in **Admin → Users → New user**
   (group *Manager*), or promotes an existing user by changing their group.
2. **Many people at once:** **Admin → Users → Import from CSV** (save the Excel sheet as CSV). Columns:
   `username, email, firstName, lastName, employeeId, designation, group, reportingManager`.
   `group` is EMPLOYEE / MANAGER / ADMIN; `reportingManager` is a manager's username, and that manager may be in the same
   file, so a whole team can be loaded in one go. If any row is wrong, nobody is created and every problem is listed.
3. **Managers add their own people** in **My team → Add employee** (Employee ID, Designation, Reports to). A manager
   sees everyone below them, including people under their team leads, and can make a new person report to themselves
   or to anyone in their team (e.g. a consultant under a team lead).
   **Levels (from Designation):** Senior VP › VP › Senior Manager/Director › Manager/Head › Team Lead › Senior Employee ›
   Employee/Consultant/Intern. People can only report to a **Team Lead or higher**, and never to someone below their
   own level. "Reports to" lists only valid people, and the server enforces the same rule (My team, Admin and CSV import).
   **Approval rule:** an upload goes to the nearest person above the author who has *Manager / VP* access. A team lead
   with employee access is skipped, so the manager above them approves.
4. Every new account (and every password reset) gets a **temporary password**. At first sign-in the user must choose their
   own. Anyone can change it later from the account menu → *Change password*.

5. **Someone leaves:** their manager clicks **Delete** in *My team*, or an admin uses the delete icon in **Admin → Users**.
   They can no longer sign in and disappear from the lists; their published knowledge stays. Anyone who reported to them
   moves up to their manager, along with uploads waiting for approval. Admins can bring an account back from
   **Admin → Users → Deleted users → Restore**.

*Role* (designation, e.g. Team Lead) is only a job title shown in the app. What a person may do comes from their **group**.

**Submitting knowledge:** title, description, at least one keyword and at least one attachment are required to submit or
publish. Drafts can be saved with just a title.

## 2. Configuration

Defaults are in [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties).
Override any of them with environment variables or `-D` JVM options (WebLogic: `JAVA_OPTIONS` in `setUserOverrides.sh/.cmd`).

| Setting | Default | Notes |
|---|---|---|
| `CUBE_DB_URL` | `db url` | Company database (TNS `schema`) |
| `CUBE_DB_USERNAME` / `CUBE_DB_PASSWORD` | `username` / `password` | Prefer setting the password on the server rather than keeping it in the file |
| `CUBE_JWT_SECRET` | *(unset → random per restart)* | Set 32+ random characters so logins survive restarts |
| `CUBE_STORAGE_TYPE` | `github` | `local` stores files on the server disk instead |
| `CUBE_GITHUB_OWNER` / `CUBE_GITHUB_REPO` / `CUBE_GITHUB_BRANCH` / `CUBE_GITHUB_TOKEN` | `CHANGE_ME_…` / `main` | Until set, uploads/downloads show “File storage is not configured yet” |
| `CUBE_LOCAL_STORAGE_DIR` | `<user home>/cube-files` | Only for `CUBE_STORAGE_TYPE=local` |
| `CUBE_LEGACY_FILES_DIR` | *(empty)* | Folder(s) holding the old version's `local-storage` files |
| `CUBE_SEARCH_MODE` | `oracle-text` | `like` if Oracle Text is not installed |

## 3. Build

```bash
cd backend  && mvn clean package     # -> backend/target/cube.war   (JDK 8)
cd frontend && npm install && npm run war   # -> frontend/frontend.war (Node 18.12+)
```

## 4. Deploy on WebLogic 14.1.1.0.0

1. **Server start arguments** (Admin Console → Environment → Servers → *server* → Server Start → Arguments, or `setUserOverrides`):
   ```
   -DCUBE_DB_URL=db url
   -DCUBE_DB_PASSWORD=db schema pass
   -DCUBE_JWT_SECRET=<32+ random characters>
   -DCUBE_GITHUB_OWNER=<owner> -DCUBE_GITHUB_REPO=<repo> -DCUBE_GITHUB_TOKEN=<token>
   ```
   Behind a corporate proxy also add `-Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port>` so the server can reach api.github.com.
   Restart the server after changing arguments.
2. **Deployments → Install** `cube.war` (context root `/cube` is set in its `weblogic.xml`) → *Install as application* → Finish → **Start → Servicing all requests**.
3. Same for `frontend.war` (context root `/frontend`).
4. Check: `http://<host>:<port>/cube/api/v1/health` returns `{"status":"UP"}`, then open `http://<host>:<port>/frontend/` and sign in as `admin`.

To update later: Deployments → select the app → **Update** → point to the new WAR.

### Moving attachments from the server disk to GitHub

1. Create a **private** GitHub repository (e.g. `cube-attachments`) with a `main` branch (tick "Add a README").
2. Create a **fine-grained personal access token** (GitHub → Settings → Developer settings → Fine-grained tokens):
   *Repository access* = only that repository, *Permissions → Contents* = **Read and write**. Prefer a company/service
   account over a personal one.
3. Add to the server start arguments (and remove `CUBE_STORAGE_TYPE=local` if it was set), then restart:
   `-DCUBE_STORAGE_TYPE=github -DCUBE_GITHUB_OWNER=<user or org> -DCUBE_GITHUB_REPO=cube-attachments -DCUBE_GITHUB_TOKEN=<token>`
4. Sign in as admin → **Admin → File storage**. It shows "GitHub connected" and how many files are still on the disk,
   on the old version's disks or in the database. Click **Copy … file(s) to GitHub**. Originals are kept. Run it again
   after fixing any files reported as missing; files already on GitHub are skipped.

New uploads go to GitHub from the restart onward. Files that haven't been copied yet still download from where they were.

## 5. Run locally

**Option A – against the company Oracle (CUBEDB):**

```bash
# Terminal 1 – backend on http://127.0.0.1:8080/cube
cd backend
set CUBE_DB_URL=jdbc:oracle:thin:@//db connection      # PowerShell: $env:CUBE_DB_URL="..."
set CUBE_STORAGE_TYPE=local                                         # until GitHub details are ready
mvn spring-boot:run

# Terminal 2 – frontend on http://localhost:5173/frontend/ (proxies /cube to the backend)
cd frontend
npm install
npm run dev
```
Sign in with your existing CUBEDB accounts 

**Option B – no database needed (in-memory demo data):**

```bash
cd backend  && mvn spring-boot:run -Pdev -Dspring-boot.run.profiles=dev
cd frontend && npm run dev
```
Demo logins, password `Passw0rd!`: `admin1` (administrator), `mgr1` (manager), `emp1`, `emp2` (employees), `inactive1` (blocked).

## Tests

```bash
cd backend  && mvn verify      # 17 tests: API, security, workflow, storage
cd frontend && npm test        # 26 tests: UI flows
cd frontend && npm run lint
```

API reference: [backend/README.md](backend/README.md).
