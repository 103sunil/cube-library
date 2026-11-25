# CUBE frontend

React 19 + Material UI, built with Vite. Served from `/frontend/` and talks to the backend at `/cube` on the same origin.
Works with **Node 18.12 or newer** (including the Node 18 already on this machine).

```bash
npm install
npm run dev      # http://localhost:5173/frontend/  (proxies /cube -> http://127.0.0.1:8080)
npm test         # unit + UI flow tests (Vitest, Testing Library, jsdom)
npm run lint
npm run war      # production build packaged as frontend.war for WebLogic
```

`VITE_API_BASE_URL` (default `/cube`) and `CUBE_BACKEND_URL` (dev proxy target) are described in `.env.example`.

## Screens

| Route | Who | What |
|---|---|---|
| `/login` | everyone | Sign in |
| `/` | KNOWLEDGE:VIEW | Discover: published items + search |
| `/knowledge/:id` | viewer | Detail, attachments (preview/download), and the actions the backend allows (`actions` block) |
| `/submit` | KNOWLEDGE:SUBMIT | Title, description, keyword chips, drag-and-drop files (10 MB each); save draft or submit |
| `/knowledge/:id/edit` | owner (draft/rejected) or editor | Edit, add/remove files, save and (re)submit |
| `/mine` | KNOWLEDGE:SUBMIT | My uploads by status, with rejection reasons |
| `/approvals` | KNOWLEDGE:APPROVE | Pending / Approved / Rejected / Archived; approve, reject with reason, archive, restore |
| `/admin` | ADMIN:MANAGE | Users (create, edit, reset password, personal overrides), groups & permissions, audit log |

Menus and buttons are driven by `/me/permissions` and the per-item `actions` returned by the API.
A 401 from any call ends the session and returns to the login page.

## Layout

```
src/
  api/        client.js (fetch, auth header, errors), cube.js (every endpoint)
  auth/       AuthProvider, useAuth, RequireAuth
  components/ AppLayout (top bar), KnowledgeCard, KeywordInput, FileDropZone, AttachmentList, dialogs
  pages/      Login, Discover, KnowledgeDetail, KnowledgeForm (submit/edit), MyUploads, Approvals, admin/*
  test/       Vitest suites and helpers
public/WEB-INF/ web.xml (SPA 404 fallback) and weblogic.xml (context root /frontend)
```
