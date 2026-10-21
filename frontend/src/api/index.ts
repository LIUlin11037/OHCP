import { isMockEnabled, requestJson } from './http'
import {
  mockAdvice,
  mockChatEvents,
  mockDiagnose,
  mockHealth,
  mockHotQueries,
  mockKnowledge,
  mockReload,
} from './mock'
import { parseSseStream } from './sse'
import type {
  AdviceMode,
  AdviceResponse,
  ChatRequest,
  DiagnoseRequest,
  DiagnosisResponse,
  HealthResponse,
  HotQuery,
  KnowledgeResponse,
} from './types'

export { isMockEnabled }
export { ApiError, visibleTraceId } from './http'
export * from './types'

export async function diagnose(input: DiagnoseRequest, signal?: AbortSignal): Promise<DiagnosisResponse> {
  if (isMockEnabled()) return mockDiagnose(input)
  return requestJson('/diagnose', { method: 'POST', body: input, signal, retrySafe: true })
}

export async function fetchAdvice(element: string, crop: string, mode: AdviceMode, signal?: AbortSignal): Promise<AdviceResponse> {
  if (isMockEnabled()) return mockAdvice(element, mode)
  return requestJson('/advice', { method: 'POST', body: { element, crop, mode }, signal, retrySafe: true })
}

export async function fetchKnowledge(element: string, signal?: AbortSignal): Promise<KnowledgeResponse> {
  if (isMockEnabled()) return mockKnowledge(element)
  return requestJson(`/knowledge/${encodeURIComponent(element)}`, { signal, retrySafe: true })
}

export async function fetchHotQueries(signal?: AbortSignal): Promise<HotQuery[]> {
  if (isMockEnabled()) return mockHotQueries()
  return requestJson('/stats/hot', { signal, retrySafe: true })
}

export async function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  if (isMockEnabled()) return mockHealth()
  return requestJson('/health', { signal, retrySafe: true })
}

export async function reloadKnowledge(signal?: AbortSignal): Promise<void> {
  if (isMockEnabled()) return mockReload()
  return requestJson('/knowledge/reload', { method: 'POST', signal })
}

const KNOWN_CHAT_EVENTS = new Set(['meta', 'candidate', 'token', 'sizheng', 'done', 'error'])

export async function streamChat(
  input: ChatRequest,
  onEvent: (event: { event: string; data: unknown }) => void,
  signal: AbortSignal,
): Promise<void> {
  if (isMockEnabled()) {
    const events = await mockChatEvents(input.question)
    for (const event of events) {
      if (signal.aborted) return
      onEvent(event)
      await new Promise((resolve) => setTimeout(resolve, 250))
    }
    return
  }

  const timeoutController = new AbortController()
  const combinedSignal = AbortSignal.any([signal, timeoutController.signal])
  let idleTimer: ReturnType<typeof setTimeout> | undefined
  const resetIdleTimer = () => {
    clearTimeout(idleTimer)
    idleTimer = setTimeout(() => timeoutController.abort(new DOMException('SSE idle timeout', 'TimeoutError')), 15_000)
  }
  resetIdleTimer()
  try {
    const response = await fetch('/api/v1/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(input),
      signal: combinedSignal,
    })
    if (!response.ok || !response.body) throw new Error(`追问连接失败（${response.status}）`)

    for await (const message of parseSseStream(response.body, combinedSignal)) {
      resetIdleTimer()
      if (message.event === 'ping') continue
      if (!KNOWN_CHAT_EVENTS.has(message.event)) continue
      let data: unknown = message.data
      try { data = JSON.parse(message.data) } catch { /* text events may carry plain strings */ }
      onEvent({ event: message.event, data })
      if (message.event === 'error') return
    }
    if (timeoutController.signal.aborted && !signal.aborted) throw timeoutController.signal.reason
  } finally {
    clearTimeout(idleTimer)
  }
}
