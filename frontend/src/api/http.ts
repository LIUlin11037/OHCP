export class ApiError extends Error {
  constructor(
    message: string,
    readonly code?: number,
    readonly traceId?: string,
    readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  timeoutMs?: number
  retrySafe?: boolean
}

const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'
const DEFAULT_TIMEOUT_MS = 15_000
const RETRYABLE_CODES = new Set([2001, 5000])

export function isMockEnabled(): boolean {
  return import.meta.env.VITE_USE_MOCKS !== 'false'
}

function pause(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason)
      return
    }
    const cleanup = () => signal?.removeEventListener('abort', onAbort)
    const timer = setTimeout(() => { cleanup(); resolve() }, ms)
    const onAbort = () => {
      clearTimeout(timer)
      cleanup()
      reject(signal?.reason)
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, timeoutMs = DEFAULT_TIMEOUT_MS, retrySafe = false, signal: requestSignal, headers, ...init } = options
  const signal = requestSignal || undefined
  let attempt = 0

  while (true) {
    const timeout = AbortSignal.timeout(timeoutMs)
    const combined = signal ? AbortSignal.any([signal, timeout]) : timeout
    try {
      const response = await fetch(`${API_BASE}${path}`, {
        ...init,
        signal: combined,
        headers: { 'Content-Type': 'application/json', ...headers },
        body: body === undefined ? undefined : JSON.stringify(body),
      })

      const payload = await response.json().catch(() => undefined) as
        | { code?: unknown; message?: unknown; data?: unknown; traceId?: unknown }
        | undefined
      const code = typeof payload?.code === 'number' ? payload.code : undefined
      if (!response.ok || (code !== undefined && code !== 0)) {
        const error = new ApiError(
          typeof payload?.message === 'string' ? payload.message : '请求未成功',
          code,
          typeof payload?.traceId === 'string' ? payload.traceId : undefined,
          response.status,
        )
        if (attempt === 0 && ((code === 2001 && retrySafe) || (retrySafe && RETRYABLE_CODES.has(code ?? -1)))) {
          attempt += 1
          await pause(code === 2001 ? 3000 : 500, signal)
          continue
        }
        throw error
      }
      return (payload && 'data' in payload ? payload.data : payload) as T
    } catch (error) {
      if (signal?.aborted) throw error
      const timedOut = error instanceof DOMException && error.name === 'TimeoutError'
      if (attempt === 0 && retrySafe && (timedOut || !(error instanceof ApiError))) {
        attempt += 1
        await pause(500, signal)
        continue
      }
      if (error instanceof ApiError) throw error
      throw new ApiError(timedOut ? '请求超时，请稍后重试' : '网络连接失败，请检查服务状态', undefined, undefined)
    }
  }
}

export function visibleTraceId(traceId?: string): string | undefined {
  return traceId && traceId.length >= 8 ? traceId.slice(-8) : undefined
}
