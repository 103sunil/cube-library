import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ACCESS_CODES, ADMIN_USER, ADMIN_USER_PERMS, mockApi, page, renderApp, sessionRoutes } from './helpers'

const GROUPS = [
  { groupId: 44, groupCode: 'ADMIN', groupName: 'Administrator', status: 'A', memberCount: 1 },
  { groupId: 45, groupCode: 'EMPLOYEE', groupName: 'Employee', status: 'A', memberCount: 3 },
  { groupId: 46, groupCode: 'MANAGER', groupName: 'Manager', status: 'A', memberCount: 2 },
]

const grant = (access, module = 'KNOWLEDGE', allowed = true) => ({
  moduleCode: module,
  accessCode: access,
  permission: `${module}:${access}`,
  allowed,
  status: 'A',
})

describe('Administration', () => {
  it('creates a user', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(ADMIN_USER, ADMIN_USER_PERMS),
      ['GET', '/api/v1/admin/users', page([{ userId: 1, username: 'admin', email: 'a@x.com', status: 'A', groupName: 'Administrator' }])],
      ['GET', '/api/v1/admin/groups', GROUPS],
      ['GET', '/api/v1/admin/users/options', [{ userId: 1, username: 'mgr1', displayName: 'Meera Iyer', level: 4 }]],
      ['POST', '/api/v1/admin/users', { userId: 9, username: 'newbie' }],
    ])
    renderApp('/admin', { user: ADMIN_USER })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'New user' }))
    await user.type(screen.getByLabelText(/Username/), 'newbie')
    await user.type(screen.getByLabelText(/Initial password/), 'Secret123')
    await user.type(screen.getByLabelText(/Email/), 'newbie@example.com')
    await user.click(screen.getByRole('button', { name: 'Create user' }))

    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true))
    const body = JSON.parse(calls.find((c) => c.method === 'POST').init.body)
    expect(body).toMatchObject({ username: 'newbie', password: 'Secret123', email: 'newbie@example.com', groupCode: 'EMPLOYEE', status: 'A' })
  })

  it('grants and removes group permissions using the CUBEDB access codes', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(ADMIN_USER, ADMIN_USER_PERMS),
      ['GET', '/api/v1/admin/groups', GROUPS],
      ['GET', '/api/v1/admin/access-codes', ACCESS_CODES],
      ['GET', '/api/v1/admin/groups/44/grants', [grant('MANAGE_USERS', 'AUTH')]],
      // EMPLOYEE has an explicit "N" for APPROVE, like the real data
      ['GET', '/api/v1/admin/groups/45/grants', [grant('CREATE'), grant('VIEW'), grant('SEARCH'), grant('DOWNLOAD'), grant('APPROVE', 'KNOWLEDGE', false)]],
      ['GET', '/api/v1/admin/groups/46/grants', [grant('APPROVE')]],
      ['PUT', '/api/v1/admin/groups/45', GROUPS[1]],
      ['PUT', '/api/v1/admin/groups/45/grants', []],
      ['DELETE', '/api/v1/admin/groups/45/grants/KNOWLEDGE/DOWNLOAD', null],
    ])
    renderApp('/admin?tab=groups', { user: ADMIN_USER })
    const user = userEvent.setup()

    const editButtons = await screen.findAllByRole('button', { name: 'Edit' })
    await user.click(editButtons[1]) // EMPLOYEE
    expect(screen.getByRole('checkbox', { name: /^Approve/ }).checked).toBe(false)
    await user.click(screen.getByRole('checkbox', { name: /^Approve/ }))
    await user.click(screen.getByRole('checkbox', { name: /^Download/ }))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(calls.some((c) => c.method === 'DELETE')).toBe(true))
    const put = calls.find((c) => c.method === 'PUT' && c.path.endsWith('/grants'))
    expect(JSON.parse(put.init.body)).toEqual({ moduleCode: 'KNOWLEDGE', accessCode: 'APPROVE', allowed: true })
    expect(calls.find((c) => c.method === 'DELETE').path).toBe('/api/v1/admin/groups/45/grants/KNOWLEDGE/DOWNLOAD')
  })
})
