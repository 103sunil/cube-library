import { describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  EMPLOYEE,
  EMPLOYEE_PERMS,
  MANAGER,
  MANAGER_PERMS,
  blobResponse,
  detail,
  mockApi,
  page,
  renderApp,
  sessionRoutes,
  summary,
} from './helpers'

describe('Discover', () => {
  it('lists published knowledge and searches', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/published', page([summary()])],
      ['GET', '/api/v1/knowledge/search', page([summary({ knowledgeId: 12, title: 'VPN troubleshooting' })])],
    ])
    renderApp('/', { user: EMPLOYEE })

    expect(await screen.findByRole('link', { name: 'How to configure VPN on Windows 11' })).toBeTruthy()
    expect(screen.getByText('1 published item')).toBeTruthy()

    const user = userEvent.setup()
    await user.type(screen.getByRole('textbox', { name: 'Search knowledge' }), 'vpn errors')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByRole('link', { name: 'VPN troubleshooting' })).toBeTruthy()
    const search = calls.find((c) => c.path === '/api/v1/knowledge/search')
    expect(search.fullPath).toContain('q=vpn+errors')
  })
})

describe('Submit', () => {
  it('validates, refuses files over 10 MB and posts the multipart form', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['POST', '/api/v1/knowledge/submit', { knowledgeId: 77, status: 'PENDING' }],
      ['GET', '/api/v1/knowledge/77', detail({ knowledgeId: 77, title: 'VPN setup', status: 'PENDING' })],
    ])
    renderApp('/submit', { user: EMPLOYEE })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Submit for approval' }))
    expect(screen.getByText('Title is required')).toBeTruthy()
    expect(screen.getByText('Description is required')).toBeTruthy()
    expect(screen.getByText('Add at least one keyword')).toBeTruthy()
    expect(screen.getByText('Attach at least one file')).toBeTruthy()
    expect(calls.some((c) => c.method === 'POST')).toBe(false)

    await user.type(screen.getByLabelText(/Title/), 'VPN setup')
    await user.type(screen.getByLabelText(/Description/), 'Install the client')
    await user.type(screen.getByLabelText(/Keywords/), 'vpn{Enter}network,')

    const good = new File(['hello'], 'guide.pdf', { type: 'application/pdf' })
    const big = new File(['x'], 'huge.zip', { type: 'application/zip' })
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 })
    fireEvent.change(screen.getByTestId('file-input'), { target: { files: [good, big] } })

    expect(await screen.findByText(/"huge.zip" is 11.0 MB. The limit is 10 MB per file./)).toBeTruthy()
    expect(screen.getByText('guide.pdf')).toBeTruthy()
    expect(screen.queryByText('huge.zip')).toBeNull()

    await user.click(screen.getByRole('button', { name: /Submit for approval/ }))

    expect(await screen.findByRole('heading', { name: 'VPN setup' })).toBeTruthy()
    const post = calls.find((c) => c.method === 'POST')
    expect(post.path).toBe('/api/v1/knowledge/submit')
    const form = post.init.body
    expect(form.get('title')).toBe('VPN setup')
    expect(form.get('description')).toBe('Install the client')
    expect(form.getAll('keywords')).toEqual(['vpn', 'network'])
    expect(form.getAll('files').map((f) => f.name)).toEqual(['guide.pdf'])
  })

  it('saves a draft without a description', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['POST', '/api/v1/knowledge/draft', { knowledgeId: 78, status: 'DRAFT' }],
      ['GET', '/api/v1/knowledge/78', detail({ knowledgeId: 78, title: 'Half done', status: 'DRAFT', description: null })],
    ])
    renderApp('/submit', { user: EMPLOYEE })
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText(/Title/), 'Half done')
    await user.click(screen.getByRole('button', { name: 'Save as draft' }))
    expect(await screen.findByRole('heading', { name: 'Half done' })).toBeTruthy()
    expect(calls.some((c) => c.path === '/api/v1/knowledge/draft')).toBe(true)
  })
})

describe('Knowledge detail', () => {
  it('lets a manager download, approve and reject a pending item', async () => {
    const pending = detail({
      status: 'PENDING',
      actions: { ...detail().actions, canApprove: true, canReject: true, canEdit: true, canDelete: true },
    })
    const { calls } = mockApi([
      ...sessionRoutes(MANAGER, MANAGER_PERMS),
      ['GET', '/api/v1/knowledge/11', pending],
      ['GET', '/api/v1/attachments/5/download', blobResponse('%PDF', 'application/pdf')],
      ['POST', '/api/v1/knowledge/11/approve', { ...pending, status: 'PUBLISHED' }],
      ['POST', '/api/v1/knowledge/11/reject', { ...pending, status: 'REJECTED' }],
    ])
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x')
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    renderApp('/knowledge/11', { user: MANAGER })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Download guide.pdf' }))
    await waitFor(() => expect(createUrl).toHaveBeenCalled())
    expect(anchorClick).toHaveBeenCalled()
    const download = calls.find((c) => c.path === '/api/v1/attachments/5/download')
    expect(download.init.headers.Authorization).toBe('Bearer test-token')

    await user.click(screen.getByRole('button', { name: 'Reject' }))
    await user.click(screen.getAllByRole('button', { name: 'Reject' }).at(-1))
    expect(await screen.findByText('A reason is required')).toBeTruthy()
    expect(calls.some((c) => c.path.endsWith('/reject'))).toBe(false)

    await user.type(screen.getByLabelText('Reason (shown to the author)'), 'Add screenshots')
    await user.click(screen.getAllByRole('button', { name: 'Reject' }).at(-1))
    await waitFor(() => expect(calls.some((c) => c.path.endsWith('/reject'))).toBe(true))
    const reject = calls.find((c) => c.path.endsWith('/reject'))
    expect(JSON.parse(reject.init.body)).toEqual({ reason: 'Add screenshots' })

    await user.click(await screen.findByRole('button', { name: 'Approve' }))
    await waitFor(() => expect(calls.some((c) => c.path.endsWith('/approve'))).toBe(true))
  })

  it('shows the author the rejection reason and lets them resubmit', async () => {
    const rejected = detail({
      status: 'REJECTED',
      rejectReason: 'Add screenshots',
      reviewedBy: 'mgr1',
      actions: { ...detail().actions, canEdit: true, canSubmit: true, canManageAttachments: true },
    })
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/11', rejected],
      ['POST', '/api/v1/knowledge/11/submit', { ...rejected, status: 'PENDING' }],
    ])
    renderApp('/knowledge/11', { user: EMPLOYEE })

    expect(await screen.findByText(/Add screenshots/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Resubmit' }))
    await waitFor(() => expect(calls.some((c) => c.path === '/api/v1/knowledge/11/submit')).toBe(true))
  })

  it('shows a clear message when the item is not visible', async () => {
    mockApi([...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS)])
    renderApp('/knowledge/999', { user: EMPLOYEE })
    expect(await screen.findByText('No mock for GET /api/v1/knowledge/999')).toBeTruthy()
  })
})

describe('Edit and resubmit', () => {
  it('saves changes and resubmits a rejected item', async () => {
    const rejected = detail({
      status: 'REJECTED',
      rejectReason: 'Too short',
      actions: { ...detail().actions, canEdit: true, canSubmit: true, canManageAttachments: true },
    })
    const { calls } = mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/11', rejected],
      ['PUT', '/api/v1/knowledge/11', rejected],
      ['POST', '/api/v1/knowledge/11/submit', { ...rejected, status: 'PENDING' }],
    ])
    renderApp('/knowledge/11/edit', { user: EMPLOYEE })
    const user = userEvent.setup()

    const description = await screen.findByLabelText(/Description/)
    await user.clear(description)
    await user.type(description, 'Longer explanation')
    await user.click(screen.getByRole('button', { name: 'Save and resubmit' }))

    await waitFor(() => expect(calls.some((c) => c.path === '/api/v1/knowledge/11/submit')).toBe(true))
    const put = calls.find((c) => c.method === 'PUT')
    expect(JSON.parse(put.init.body)).toEqual({
      title: rejected.title,
      description: 'Longer explanation',
      keywords: rejected.keywords,
    })
  })
})

describe('My uploads and approvals', () => {
  it('shows my items with status, rejection reason and an edit link', async () => {
    mockApi([
      ...sessionRoutes(EMPLOYEE, EMPLOYEE_PERMS),
      ['GET', '/api/v1/knowledge/mine', page([summary({ status: 'REJECTED', rejectReason: 'Needs steps' })])],
    ])
    renderApp('/mine', { user: EMPLOYEE })
    expect(await screen.findByText('Needs steps')).toBeTruthy()
    expect(screen.getAllByText('Rejected')).toHaveLength(2) // the tab and the status chip
    expect(screen.getByRole('link', { name: 'Edit & resubmit' }).getAttribute('href')).toBe('/knowledge/11/edit')
  })

  it('approves a pending item from the queue', async () => {
    const { calls } = mockApi([
      ...sessionRoutes(MANAGER, MANAGER_PERMS),
      ['GET', /\/api\/v1\/knowledge\?status=PENDING/, page([summary({ status: 'PENDING' })])],
      ['POST', '/api/v1/knowledge/11/approve', {}],
    ])
    renderApp('/approvals', { user: MANAGER })
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Approve' }))
    await waitFor(() => expect(calls.some((c) => c.path === '/api/v1/knowledge/11/approve')).toBe(true))
    expect(calls.filter((c) => c.fullPath.includes('status=PENDING')).length).toBeGreaterThanOrEqual(2)
  })
})
