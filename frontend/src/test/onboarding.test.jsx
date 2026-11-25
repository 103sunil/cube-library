import { describe, expect, it } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  ADMIN_USER,
  ADMIN_USER_PERMS,
  EMPLOYEE,
  EMPLOYEE_PERMS,
  MANAGER,
  MANAGER_PERMS,
  mockApi,
  page,
  renderApp,
  sessionRoutes,
} from './helpers'

describe('Passwords', () => {
  it('makes a user with a temporary password choose their own before using the app', async () => {
    let mustChange = true
    const { calls } = mockApi([
      ['GET', '/api/v1/me', () => ({ ...EMPLOYEE, mustChangePassword: mustChange })],
      ['GET', '/api/v1/me/permissions', { username: EMPLOYEE.username, groupCode: 'EMPLOYEE', permissions: EMPLOYEE_PERMS }],
      [
        'POST',
        '/api/v1/me/password',
        () => {
          mustChange = false
          return { status: 204 }
        },
      ],
      ['GET', '/api/v1/knowledge/published', page([])],
    ])
    renderApp('/', { user: EMPLOYEE })
    const user = userEvent.setup()

    expect(await screen.findByRole('heading', { name: 'Choose your own password' })).toBeTruthy()
    expect(screen.queryByRole('navigation', { name: 'Main' })).toBeNull()

    await user.type(screen.getByLabelText(/Current password/), 'Temp1234!')
    await user.type(screen.getByLabelText(/^New password/), 'short')
    await user.click(screen.getByRole('button', { name: 'Save and continue' }))
    expect(screen.getByText('At least 8 characters')).toBeTruthy()
    expect(screen.getByText('Passwords do not match')).toBeTruthy()

    await user.clear(screen.getByLabelText(/^New password/))
    await user.type(screen.getByLabelText(/^New password/), 'MyOwnPass1')
    await user.type(screen.getByLabelText(/Confirm new password/), 'MyOwnPass1')
    await user.click(screen.getByRole('button', { name: 'Save and continue' }))

    expect(await screen.findByRole('heading', { name: 'Discover' })).toBeTruthy()
    const post = calls.find((c) => c.path === '/api/v1/me/password')
    expect(JSON.parse(post.init.body)).toEqual({ currentPassword: 'Temp1234!', newPassword: 'MyOwnPass1' })
  })

  it('lets anyone change their password from the account menu', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/published', page([])],
      ['POST', '/api/v1/me/password', { status: 204 }],
    ])
    renderApp('/', { user: EMPLOYEE })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Account menu' }))
    await user.click(screen.getByRole('menuitem', { name: 'Change password' }))
    await user.type(screen.getByLabelText(/Current password/), 'Passw0rd!')
    await user.type(screen.getByLabelText(/^New password/), 'Another123')
    await user.type(screen.getByLabelText(/Confirm new password/), 'Another123')
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('Password changed')).toBeTruthy()
    expect(calls.some((c) => c.path === '/api/v1/me/password')).toBe(true)
  })
})

describe('Admin onboarding and storage', () => {
  const adminRoutes = [
    ...sessionRoutes(ADMIN_USER, ADMIN_USER_PERMS),
    ['GET', '/api/v1/admin/users', page([])],
    ['GET', '/api/v1/admin/groups', []],
    ['GET', '/api/v1/admin/users/options', []],
  ]

  it('imports managers and teams from CSV and shows the temporary passwords', async () => {
    const { calls } = mockApi([
      ...adminRoutes,
      [
        'POST',
        '/api/v1/admin/users/import',
        {
          created: 2,
          errors: [],
          users: [
            { username: 'asha.manager', employeeCode: 'M1', group: 'MANAGER', reportingManager: null, temporaryPassword: 'Tmp-AAA111' },
            { username: 'ravi.k', employeeCode: 'E1', group: 'EMPLOYEE', reportingManager: 'asha.manager', temporaryPassword: 'Tmp-BBB222' },
          ],
        },
      ],
    ])
    renderApp('/admin', { user: ADMIN_USER })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Import from CSV' }))
    const csv = new File(['username,email,group\n'], 'team.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText('CSV file'), { target: { files: [csv] } })
    await user.click(screen.getByRole('button', { name: 'Import' }))

    expect(await screen.findByText(/2 users created/)).toBeTruthy()
    expect(screen.getByText('Tmp-BBB222')).toBeTruthy()
    const post = calls.find((c) => c.path === '/api/v1/admin/users/import')
    expect(post.init.body.get('file').name).toBe('team.csv')
  })

  it('shows import problems and creates nobody', async () => {
    mockApi([
      ...adminRoutes,
      ['POST', '/api/v1/admin/users/import', { created: 0, errors: ["Line 3: username 'emp1' already exists"], users: [] }],
    ])
    renderApp('/admin', { user: ADMIN_USER })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Import from CSV' }))
    fireEvent.change(screen.getByLabelText('CSV file'), { target: { files: [new File(['x'], 'a.csv')] } })
    await user.click(screen.getByRole('button', { name: 'Import' }))
    expect(await screen.findByText(/Nothing was imported/)).toBeTruthy()
    expect(screen.getByText("Line 3: username 'emp1' already exists")).toBeTruthy()
  })

  it('shows where files are stored and copies them to GitHub', async () => {
    let counts = { github: 0, local: 5, legacyDisk: 3, database: 1 }
    const { calls } = mockApi([
      ...adminRoutes,
      ['GET', '/api/v1/admin/storage/status', () => ({ storageType: 'github', githubConfigured: true, counts, notOnGitHub: 9 - counts.github })],
      [
        'POST',
        '/api/v1/admin/storage/migrate',
        () => {
          counts = { github: 8, local: 0, legacyDisk: 1, database: 0 }
          return { copied: 8, skipped: 0, errors: ['Attachment 5 (old.pdf, item 2): file not found where it was stored'] }
        },
      ],
    ])
    renderApp('/admin?tab=storage', { user: ADMIN_USER })
    const user = userEvent.setup()
    expect(await screen.findByText('GitHub connected')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Copy 9 file(s) to GitHub' }))
    expect(await screen.findByText(/file not found where it was stored/)).toBeTruthy()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Copy 1 file(s) to GitHub' })).toBeTruthy())
    expect(calls.some((c) => c.path === '/api/v1/admin/storage/migrate')).toBe(true)
  })
})

describe('Deleting people who left', () => {
  const MEMBER = { userId: 20, username: 'priya', firstName: 'Priya', email: 'p@example.com', status: 'A', reportingManagerUsername: 'mgr1' }

  it('lets a manager delete someone in their team', async () => {
    let team = [MEMBER]
    const { calls } = mockApi([
      ...sessionRoutes(MANAGER, MANAGER_PERMS),
      ['GET', '/api/v1/team', () => team],
      ['GET', '/api/v1/team/managers', []],
      [
        'DELETE',
        '/api/v1/team/20',
        () => {
          team = []
          return { username: 'priya', reportsMoved: 0 }
        },
      ],
    ])
    renderApp('/team', { user: MANAGER })
    const user = userEvent.setup()
    const row = (await screen.findByText('priya')).closest('tr')
    await user.click(within(row).getByRole('button', { name: 'Delete' }))
    expect(screen.getByText(/Knowledge they published stays available/)).toBeTruthy()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('priya deleted.')).toBeTruthy()
    expect(await screen.findByText('No one reports to you yet')).toBeTruthy()
    expect(calls.some((c) => c.method === 'DELETE' && c.path === '/api/v1/team/20')).toBe(true)
  })

  it('lets an admin delete a manager, then find and restore them under Deleted users', async () => {
    const MGR = { userId: 5, username: 'rahul.manager', email: 'r@example.com', status: 'A', groupCode: 'MANAGER' }
    const { calls } = mockApi([
      ...sessionRoutes(ADMIN_USER, ADMIN_USER_PERMS),
      [
        'GET',
        /\/api\/v1\/admin\/users(\?|$)/,
        (call) => (call.fullPath.includes('removed=true') ? page([{ ...MGR, status: 'D' }]) : page([MGR])),
      ],
      ['GET', '/api/v1/admin/groups', []],
      ['GET', '/api/v1/admin/users/options', []],
      ['DELETE', '/api/v1/admin/users/5', { username: 'rahul.manager', reportsMoved: 2, reportsMovedTo: null }],
      ['POST', '/api/v1/admin/users/5/restore', { ...MGR }],
    ])
    renderApp('/admin', { user: ADMIN_USER })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Delete rahul.manager' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText(/2 people who reported to them now report to no one/)).toBeTruthy()

    await user.click(screen.getByRole('button', { name: 'Deleted users' }))
    await user.click(await screen.findByRole('button', { name: 'Restore' }))
    expect(await screen.findByText(/rahul.manager restored/)).toBeTruthy()
    expect(calls.some((c) => c.path === '/api/v1/admin/users/5/restore')).toBe(true)
  })
})
