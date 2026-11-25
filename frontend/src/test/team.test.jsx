import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  EMPLOYEE,
  EMPLOYEE_PERMS,
  MANAGER,
  MANAGER_PERMS,
  detail,
  mockApi,
  page,
  renderApp,
  sessionRoutes,
  summary,
} from './helpers'

const MANAGERS = [
  { userId: 1, username: 'mgr1', displayName: 'Meera Iyer', designation: 'Manager', level: 4, inMyTeam: true },
  { userId: 7, username: 'mgr2', displayName: 'Arun Rao', designation: 'Manager', level: 4, inMyTeam: false },
]
const MEMBER = {
  userId: 20,
  username: 'priya',
  firstName: 'Priya',
  lastName: 'Sharma',
  email: 'priya@example.com',
  status: 'A',
  reportingManagerUsername: 'mgr1',
}

describe('My team', () => {
  it('lets a manager create a login for a new employee under themselves', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(MANAGER, MANAGER_PERMS),
      ['GET', '/api/v1/team', []],
      ['GET', '/api/v1/team/managers', MANAGERS],
      ['POST', '/api/v1/team', { ...MEMBER, userId: 21, username: 'ravi' }],
    ])
    renderApp('/team', { user: MANAGER })
    const user = userEvent.setup()

    expect(await screen.findByText('No one reports to you yet')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Add employee' }))
    await user.type(screen.getByLabelText(/Username/), 'ravi')
    await user.type(screen.getByLabelText(/Initial password/), 'Secret123')
    await user.type(screen.getByLabelText(/Email/), 'ravi@example.com')
    await user.type(screen.getByLabelText(/Employee ID/), 'E1042')
    await user.type(screen.getByLabelText(/Designation/), 'Team Lead')
    await user.click(screen.getByRole('button', { name: 'Create login' }))

    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true))
    expect(JSON.parse(calls.find((c) => c.method === 'POST').init.body)).toMatchObject({
      username: 'ravi',
      email: 'ravi@example.com',
      reportingManagerUsername: 'mgr1',
      employeeCode: 'E1042',
      designation: 'Team Lead',
    })
  })

  it('adds someone who reports to a team lead in the team', async () => {
    const LEAD = { userId: 30, username: 'akshay.rs', firstName: 'Akshay', email: 'a@example.com', status: 'A', designation: 'Team Lead', reportingManagerUsername: 'mgr1' }
    const { calls } = mockApi([
      ...sessionRoutes(MANAGER, MANAGER_PERMS),
      ['GET', '/api/v1/team', [LEAD]],
      ['GET', '/api/v1/team/managers', [MANAGERS[0], { userId: 30, username: 'akshay.rs', displayName: 'Akshay', designation: 'Team Lead', level: 3, inMyTeam: true }, MANAGERS[1]]],
      ['POST', '/api/v1/team', { ...MEMBER, userId: 31, username: 'rahul.rajput', reportingManagerUsername: 'akshay.rs' }],
    ])
    renderApp('/team', { user: MANAGER })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Add employee' }))
    await user.type(screen.getByLabelText(/Username/), 'rahul.rajput')
    await user.type(screen.getByLabelText(/Initial password/), 'Secret123')
    await user.type(screen.getByLabelText(/Email/), 'rahul@example.com')
    await user.click(screen.getByLabelText('Reports to'))
    // Only the manager's own team is offered for new people.
    expect(screen.queryByRole('option', { name: /Arun Rao/ })).toBeNull()
    await user.click(await screen.findByRole('option', { name: /akshay.rs.*Team Lead/ }))
    await user.click(screen.getByRole('button', { name: 'Create login' }))
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true))
    expect(JSON.parse(calls.find((c) => c.method === 'POST').init.body).reportingManagerUsername).toBe('akshay.rs')
  })

  it('offers only Team Leads or higher, and never someone below the person', async () => {
    const LEAD = { userId: 30, username: 'akshay.rs', firstName: 'Akshay', email: 'a@x.com', status: 'A', designation: 'Team Lead', reportingManagerUsername: 'mgr1' }
    const SUNIL = { userId: 32, username: 'sunil.kumar', firstName: 'Sunil', email: 's@x.com', status: 'A', designation: 'Consultant', reportingManagerUsername: 'mgr1' }
    const RAHUL = { userId: 31, username: 'rahul.rajput', firstName: 'Rahul', email: 'r@x.com', status: 'A', designation: 'Consultant', reportingManagerUsername: 'akshay.rs' }
    mockApi([
      ...sessionRoutes(MANAGER, MANAGER_PERMS),
      ['GET', '/api/v1/team', [LEAD, RAHUL, SUNIL]],
      // The server already leaves out employees such as Sunil; the page also filters by level.
      ['GET', '/api/v1/team/managers', [MANAGERS[0], { userId: 30, username: 'akshay.rs', displayName: 'Akshay', designation: 'Team Lead', level: 3, inMyTeam: true }, MANAGERS[1]]],
    ])
    renderApp('/team', { user: MANAGER })
    const user = userEvent.setup()
    const row = (await screen.findByText('rahul.rajput')).closest('tr')
    await user.click(within(row).getByRole('button', { name: 'Edit' }))

    await user.click(screen.getByLabelText('Reports to'))
    expect(screen.getByRole('option', { name: /akshay.rs.*Team Lead/ })).toBeTruthy()
    expect(screen.queryByRole('option', { name: /sunil/ })).toBeNull()
    await user.keyboard('{Escape}')

    // Promote Rahul to Manager: the Team Lead is no longer a valid choice.
    const designation = screen.getByLabelText(/Designation/)
    await user.clear(designation)
    await user.type(designation, 'Manager')
    expect(screen.getByText(/below their new level/)).toBeTruthy()
    await user.click(screen.getByLabelText('Reports to'))
    expect(screen.queryByRole('option', { name: /akshay.rs/ })).toBeNull()
    expect(screen.getByRole('option', { name: /Meera Iyer/ })).toBeTruthy()
  })

  it('moves an employee to another manager', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(MANAGER, MANAGER_PERMS),
      ['GET', '/api/v1/team', [MEMBER]],
      ['GET', '/api/v1/team/managers', MANAGERS],
      ['PUT', '/api/v1/team/20', { ...MEMBER, reportingManagerUsername: 'mgr2' }],
    ])
    renderApp('/team', { user: MANAGER })
    const user = userEvent.setup()

    const row = (await screen.findByText('priya')).closest('tr')
    await user.click(within(row).getByRole('button', { name: 'Edit' }))
    await user.click(screen.getByLabelText('Reports to'))
    await user.click(await screen.findByRole('option', { name: /Arun Rao/ }))
    expect(screen.getByText(/will move to mgr2/)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
    expect(JSON.parse(calls.find((c) => c.method === 'PUT').init.body).reportingManagerUsername).toBe('mgr2')
  })
})

describe('Bookmarks and my uploads', () => {
  it('bookmarks a published item and lists bookmarks', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/11', detail({ bookmarked: false })],
      ['PUT', '/api/v1/knowledge/11/bookmark', null],
      ['GET', '/api/v1/knowledge/bookmarks', page([summary({ bookmarked: true })])],
    ])
    renderApp('/knowledge/11', { user: EMPLOYEE })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Bookmark' }))
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT' && c.path.endsWith('/bookmark'))).toBe(true))
    expect(await screen.findByRole('button', { name: 'Remove bookmark' })).toBeTruthy()

    await user.click(screen.getByRole('link', { name: 'Bookmarks' }))
    expect(await screen.findByRole('link', { name: 'How to configure VPN on Windows 11' })).toBeTruthy()
  })

  it('does not show Pending or Rejected tabs to managers', async () => {
    mockApi([...sessionRoutes(MANAGER, MANAGER_PERMS), ['GET', '/api/v1/knowledge/mine', page([])]])
    renderApp('/mine', { user: MANAGER })
    await screen.findByRole('heading', { name: 'My uploads' })
    const tabs = screen.getAllByRole('tab').map((t) => t.textContent)
    expect(tabs).toEqual(['All', 'Drafts', 'Published', 'Archived'])
  })

  it('shows employees all statuses and who reviews their pending item', async () => {
    mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/mine', page([summary({ status: 'PENDING', assignedReviewerName: 'Meera Iyer' })])],
    ])
    renderApp('/mine', { user: EMPLOYEE })
    expect(await screen.findByText('Reviewer: Meera Iyer')).toBeTruthy()
    expect(screen.getAllByRole('tab')).toHaveLength(6)
  })
})
