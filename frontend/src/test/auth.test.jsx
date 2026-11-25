import { describe, expect, it } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  EMPLOYEE,
  EMPLOYEE_PERMS,
  ADMIN_USER,
  ADMIN_USER_PERMS,
  MANAGER,
  MANAGER_PERMS,
  jsonResponse,
  mockApi,
  page,
  renderApp,
  sessionRoutes,
} from './helpers'

describe('login and navigation', () => {
  it('sends anonymous users to the login page and signs them in', async () => {
    const { calls } = mockApi([
      ['POST', '/api/v1/auth/login', { token: 'jwt-1', username: 'emp1' }],
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/published', page([])],
    ])
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in to CUBE' })).toBeTruthy()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Username'), 'emp1')
    await user.type(screen.getByLabelText('Password'), 'Passw0rd!')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Discover' })).toBeTruthy()
    expect(localStorage.getItem('cube.token')).toBe('jwt-1')
    const login = calls.find((c) => c.path === '/api/v1/auth/login')
    expect(JSON.parse(login.init.body)).toEqual({ username: 'emp1', password: 'Passw0rd!' })
  })

  it('always lands on the home page after login, not the page of the previous user', async () => {
    mockApi([
      ['POST', '/api/v1/auth/login', { token: 'jwt-2', username: 'emp1' }],
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/published', page([])],
    ])
    // e.g. a manager's session ended while on Approvals
    renderApp('/approvals')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Username'), 'emp1')
    await user.type(screen.getByLabelText('Password'), 'Passw0rd!')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('heading', { name: 'Discover' })).toBeTruthy()
    expect(screen.queryByText("You don't have access to this page")).toBeNull()
  })

  it('shows the backend error for bad credentials', async () => {
    mockApi([['POST', '/api/v1/auth/login', jsonResponse({ message: 'Invalid username or password' }, 401)]])
    renderApp('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Username'), 'emp1')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Invalid username or password')).toBeTruthy()
    expect(localStorage.getItem('cube.token')).toBeNull()
  })

  it('validates empty fields without calling the server', async () => {
    const { calls } = mockApi([])
    renderApp('/login')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }))
    expect(screen.getByText('Enter your username')).toBeTruthy()
    expect(calls).toHaveLength(0)
  })

  it('shows employees only the menus their permissions allow', async () => {
    mockApi([...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS), ['GET', '/api/v1/knowledge/published', page([])]])
    renderApp('/', { user: EMPLOYEE })
    const nav = await screen.findByRole('navigation', { name: 'Main' })
    const links = within(nav).getAllByRole('link').map((l) => l.textContent)
    expect(links).toEqual(['Discover', 'Submit', 'My uploads', 'Bookmarks'])
  })

  it('shows managers the approvals menu', async () => {
    mockApi([...sessionRoutes(MANAGER, MANAGER_PERMS), ['GET', '/api/v1/knowledge/published', page([])]])
    renderApp('/', { user: MANAGER })
    const nav = await screen.findByRole('navigation', { name: 'Main' })
    const links = within(nav).getAllByRole('link').map((l) => l.textContent)
    expect(links).toEqual(['Discover', 'Submit', 'My uploads', 'Bookmarks', 'Approvals', 'My team'])
  })

  it('sends administrators (AUTH permissions only) straight to Admin', async () => {
    mockApi([
      ...sessionRoutes(ADMIN_USER, ADMIN_USER_PERMS),
      ['GET', '/api/v1/admin/users', page([])],
      ['GET', '/api/v1/admin/groups', []],
    ])
    renderApp('/', { user: ADMIN_USER })
    expect(await screen.findByRole('heading', { name: 'Administration' })).toBeTruthy()
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getAllByRole('link').map((l) => l.textContent)).toEqual(['Admin'])
  })

  it('hides search from users without KNOWLEDGE:SEARCH', async () => {
    mockApi([
      ...sessionRoutes(EMPLOYEE, ['KNOWLEDGE:VIEW']),
      ['GET', '/api/v1/knowledge/published', page([])],
    ])
    renderApp('/', { user: EMPLOYEE })
    expect(await screen.findByRole('heading', { name: 'Discover' })).toBeTruthy()
    expect(screen.queryByRole('search')).toBeNull()
  })

  it('blocks employees from the admin page', async () => {
    mockApi(sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS))
    renderApp('/admin', { user: EMPLOYEE })
    expect(await screen.findByText("You don't have access to this page")).toBeTruthy()
  })

  it('logs out on the server and returns to the login page', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/published', page([])],
      ['POST', '/api/v1/auth/logout', { message: 'Logged out' }],
    ])
    renderApp('/', { user: EMPLOYEE })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Account menu' }))
    await user.click(screen.getByRole('menuitem', { name: 'Log out' }))
    expect(await screen.findByRole('heading', { name: 'Sign in to CUBE' })).toBeTruthy()
    expect(calls.some((c) => c.path === '/api/v1/auth/logout')).toBe(true)
    expect(localStorage.getItem('cube.token')).toBeNull()
  })

  it('returns to login when the session expires', async () => {
    mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/published', jsonResponse({ message: 'Authentication required' }, 401)],
    ])
    renderApp('/', { user: EMPLOYEE })
    expect(await screen.findByText('Your session has ended. Please sign in again.')).toBeTruthy()
    expect(localStorage.getItem('cube.token')).toBeNull()
  })
})
