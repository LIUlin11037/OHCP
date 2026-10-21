import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamChat } from './index'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('streamChat', () => {
  it('dispatches known SSE events, ignores ping, and keeps the stream open until done', async () => {
    vi.stubEnv('VITE_USE_MOCKS', 'false')
    const body = [
      'event: meta\ndata: {"sessionId":"s-1"}\n\n',
      'event: candidate\ndata: {"element":"N"}\n\n',
      'event: token\ndata: {"delta":"田间"}\n\n',
      'event: sizheng\ndata: {"note":"演示"}\n\n',
      ': ping\n\n',
      'event: done\ndata: {"status":"done"}\n\n',
    ].join('')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 200 })))
    const received: string[] = []

    await streamChat({ sessionId: 's-1', question: '问题' }, ({ event }) => received.push(event), new AbortController().signal)

    expect(received).toEqual(['meta', 'candidate', 'token', 'sizheng', 'done'])
  })

  it('returns immediately after a terminal error event', async () => {
    vi.stubEnv('VITE_USE_MOCKS', 'false')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response([
      'event: error\ndata: {"code":5000}\n\n',
      'event: token\ndata: {"delta":"must not be read"}\n\n',
    ].join(''), { status: 200 })))
    const received: string[] = []

    await streamChat({ sessionId: 's-1', question: '问题' }, ({ event }) => received.push(event), new AbortController().signal)

    expect(received).toEqual(['error'])
  })
})
