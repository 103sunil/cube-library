import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { vi } from 'vitest'
import theme from '../theme'
import App from '../App'
import AuthProvider from '../auth/AuthProvider'
import NotifyProvider from '../components/NotifyProvider'

// Same codes as CUBEDB (MODULE:ACCESS).
export const EMPLOYEE_PERMS = ['KNOWLEDGE:CREATE', 'KNOWLEDGE:DOWNLOAD', 'KNOWLEDGE:SEARCH', 'KNOWLEDGE:VIEW']
export const MANAGER_PERMS = [
  'KNOWLEDGE:APPROVE',
  'KNOWLEDGE:CREATE',
  'KNOWLEDGE:DELETE',
  'KNOWLEDGE:DOWNLOAD',
  'KNOWLEDGE:SEARCH',
  'KNOWLEDGE:UPDATE',
  'KNOWLEDGE:VIEW',
]
export const ADMIN_USER_PERMS = [
  'AUTH:MANAGE_ACCESS',
  'AUTH:MANAGE_GROUPS',
  'AUTH:MANAGE_GROUP_ACCESS',
  'AUTH:MANAGE_MODULES',
  'AUTH:MANAGE_USERS',
  'AUTH:MANAGE_USER_ACCESS',
]

export const EMPLOYEE = { userId: 2, username: 'emp1', displayName: 'Priya Sharma', email: 'emp1@example.com', groupCode: 'EMPLOYEE' }
export const MANAGER = { userId: 1, username: 'mgr1', displayName: 'Meera Iyer', email: 'mgr1@example.com', groupCode: 'MANAGER' }
export const ADMIN_USER = { userId: 9, username: 'admin', displayName: 'System Admin', email: 'admin@example.com', groupCode: 'ADMIN' }

/** The access codes in CUBEDB, as returned by GET /admin/access-codes. */
export const ACCESS_CODES = [
  ['AUTH', 'MANAGE_USERS'],
  ['AUTH', 'MANAGE_GROUPS'],
  ['AUTH', 'MANAGE_MODULES'],
  ['AUTH', 'MANAGE_ACCESS'],
  ['AUTH', 'MANAGE_GROUP_ACCESS'],
  ['AUTH', 'MANAGE_USER_ACCESS'],
  ['KNOWLEDGE', 'CREATE'],
  ['KNOWLEDGE', 'APPROVE'],
  ['KNOWLEDGE', 'VIEW'],
  ['KNOWLEDGE', 'SEARCH'],
  ['KNOWLEDGE', 'DOWNLOAD'],
  ['KNOWLEDGE', 'UPDATE'],
  ['KNOWLEDGE', 'DELETE'],
].map(([moduleCode, code], i) => ({
  id: 54 + i,
  moduleCode,
  code,
  permission: `${moduleCode}:${code}`,
  description: `${code} description`,
  status: 'A',
}))

export function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => (name.toLowerCase() === 'content-type' ? 'application/json' : null) },
    json: async () => body,
    text: async () => JSON.stringify(body),
    blob: async () => new Blob([JSON.stringify(body)], { type: 'application/json' }),
  }
}

export function blobResponse(content, type = 'application/octet-stream') {
  return {
    ok: true,
    status: 200,
    headers: { get: (name) => (name.toLowerCase() === 'content-type' ? type : null) },
    json: async () => ({}),
    text: async () => content,
    blob: async () => new Blob([content], { type }),
  }
}

export function page(content, extra = {}) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, ...extra }
}

/**
 * Replaces fetch with a router over [method, path|RegExp, handler|value] entries.
 * The path is matched without the /cube prefix and without the query string
 * unless a RegExp is used. Every call is recorded in `calls`.
 */
export function mockApi(routes) {
  const calls = []
  const fetchMock = vi.fn(async (url, init = {}) => {
    const method = init.method || 'GET'
    const fullPath = String(url).replace(/^\/cube/, '')
    const path = fullPath.split('?')[0]
    const call = { method, path, fullPath, init }
    calls.push(call)
    for (const [m, matcher, handler] of routes) {
      const matches = typeof matcher === 'string' ? path === matcher : matcher.test(fullPath)
      if (m === method && matches) {
        const result = typeof handler === 'function' ? await handler(call) : handler
        return result && typeof result.ok === 'boolean' && result.headers ? result : jsonResponse(result)
      }
    }
    return jsonResponse({ message: `No mock for ${method} ${fullPath}` }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  return { fetchMock, calls }
}

export function sessionRoutes(user, permissions) {
  return [
    ['GET', '/api/v1/me', user],
    ['GET', '/api/v1/me/permissions', { username: user.username, groupCode: user.groupCode, permissions }],
  ]
}

/** Renders the whole app at `route`. Pass `user` to start signed in. */
export function renderApp(route = '/', { user } = {}) {
  if (user) localStorage.setItem('cube.token', 'test-token')
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <NotifyProvider>
            <App />
          </NotifyProvider>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>
  )
}

export function summary(overrides = {}) {
  return {
    knowledgeId: 11,
    title: 'How to configure VPN on Windows 11',
    summary: 'Step-by-step guide to install the client.',
    status: 'PUBLISHED',
    ownerId: 3,
    ownerUsername: 'rahul',
    ownerName: 'Rahul Verma',
    keywords: ['vpn', 'network'],
    attachmentCount: 2,
    createdAt: '2026-11-10T10:00:00',
    updatedAt: '2026-11-12T10:00:00',
    publishedAt: '2026-11-12T10:00:00',
    ...overrides,
  }
}

export function detail(overrides = {}) {
  return {
    ...summary(),
    description: 'Line one\nLine two',
    attachments: [
      {
        attachmentId: 5,
        knowledgeId: 11,
        fileName: 'guide.pdf',
        contentType: 'application/pdf',
        fileSize: 2048,
        createdAt: '2026-11-10T10:00:00',
      },
    ],
    attachmentCount: 1,
    reviewedBy: null,
    actions: {
      canEdit: false,
      canSubmit: false,
      canApprove: false,
      canReject: false,
      canArchive: false,
      canRestore: false,
      canDelete: false,
      canDownload: true,
      canManageAttachments: false,
    },
    ...overrides,
  }
}
