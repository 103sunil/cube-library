import { describe, expect, it, vi } from 'vitest'
import { ApiError, request, setUnauthorizedHandler, tokenStore } from '../api/client'
import { jsonResponse } from './helpers'

describe('api client', () => {
  it('sends the bearer token and JSON body to the /cube base path', async () => {
    tokenStore.set('abc')
    const fetchMock = vi.fn(async () => jsonResponse({ ok: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await request('/api/v1/knowledge/1/reject', { method: 'POST', body: { reason: 'x' } })

    expect(result).toEqual({ ok: 1 })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/cube/api/v1/knowledge/1/reject')
    expect(init.headers.Authorization).toBe('Bearer abc')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"reason":"x"}')
  })

  it('does not set a content type for FormData (the browser adds the boundary)', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)
    await request('/x', { method: 'POST', body: new FormData() })
    expect(fetchMock.mock.calls[0][1].headers['Content-Type']).toBeUndefined()
  })

  it('surfaces the backend message and field errors', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(
      { status: 400, message: 'Validation failed', fieldErrors: { reason: 'A rejection reason is required' } },
      400
    )))
    const error = await request('/x').catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.message).toBe('A rejection reason is required')
    expect(error.fieldErrors).toEqual({ reason: 'A rejection reason is required' })
  })

  it('reports a 401 on an authenticated call to the session handler', async () => {
    tokenStore.set('expired')
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ message: 'Authentication required' }, 401)))
    await expect(request('/api/v1/me')).rejects.toThrow('Authentication required')
    expect(handler).toHaveBeenCalledOnce()
    setUnauthorizedHandler(null)
  })

  it('turns network failures into a friendly message', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    }))
    await expect(request('/x')).rejects.toMatchObject({ status: 0, message: expect.stringContaining('Cannot reach the server') })
  })
})
