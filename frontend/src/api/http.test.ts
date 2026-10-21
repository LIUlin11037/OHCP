import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, requestJson, visibleTraceId } from './http'

afterEach(() => vi.unstubAllGlobals())

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('requestJson', () => {
  it('retries a safe request once for the documented service error', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ code: 5000, message: 'temporary' }))
      .mockResolvedValueOnce(jsonResponse({ code: 0, data: { value: 'ready' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestJson<{ value: string }>('/health', { retrySafe: true })).resolves.toEqual({ value: 'ready' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not retry a mutating request unless the caller marks it safe', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 5000, message: 'temporary' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestJson('/knowledge/reload', { method: 'POST' })).rejects.toMatchObject({ code: 5000 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('does not replay an unsafe request after code 2001', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 2001, message: 'busy' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestJson('/knowledge/reload', { method: 'POST' })).rejects.toMatchObject({ code: 2001 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('keeps trace IDs compact for user-facing diagnostics', () => {
    expect(visibleTraceId('request-trace-12345678')).toBe('12345678')
    expect(visibleTraceId('short')).toBeUndefined()
  })

  it('does not retry field validation errors', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 1001, message: 'field detail' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(requestJson('/diagnose', { method: 'POST', body: {}, retrySafe: true })).rejects.toBeInstanceOf(ApiError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
