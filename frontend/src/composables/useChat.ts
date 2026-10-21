import { ref } from 'vue'
import { streamChat } from '../api'

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  status?: 'streaming' | 'interrupted' | 'error'
  details?: Array<{ label: string; value: string }>
}

export function useChat(getSessionId: () => string | undefined, onSessionId?: (sessionId: string) => void) {
  const chatMessages = ref<ChatMessage[]>([])
  const chatInput = ref('')
  const chatSending = ref(false)
  let controller: AbortController | undefined

  async function send() {
    const question = chatInput.value.trim()
    const sessionId = getSessionId()
    if (!question || chatSending.value || !sessionId) return
    chatMessages.value.push({ role: 'user', text: question })
    const answer: ChatMessage = { role: 'assistant', text: '', status: 'streaming' }
    chatMessages.value.push(answer)
    chatInput.value = ''
    chatSending.value = true
    const current = new AbortController()
    controller = current
    try {
      await streamChat({ sessionId, question }, ({ event, data }) => {
        if (event === 'meta' && typeof data === 'object' && data && 'sessionId' in data && typeof data.sessionId === 'string') onSessionId?.(data.sessionId)
        if (event === 'token') {
          const chunk = data as { delta?: string; data?: { delta?: string } }
          const delta = typeof data === 'string' ? data : chunk.delta ?? chunk.data?.delta
          if (delta) answer.text += delta
        }
        if (event === 'candidate' || event === 'sizheng') {
          answer.details ??= []
          answer.details.push({ label: event === 'candidate' ? '候选补充' : '辨证信息', value: typeof data === 'string' ? data : JSON.stringify(data) })
        }
        if (event === 'error') answer.status = 'error'
        if (event === 'done') answer.status = undefined
      }, current.signal)
      if (current.signal.aborted) answer.status = 'interrupted'
      else if (answer.status === 'streaming') answer.status = undefined
    } catch {
      answer.status = current.signal.aborted ? 'interrupted' : 'error'
      if (answer.status === 'error') answer.text ||= '连接中断，保留已收到的内容。'
    } finally {
      if (controller === current) chatSending.value = false
    }
  }

  function stop() { controller?.abort() }
  function reset() {
    controller?.abort()
    controller = undefined
    chatMessages.value = []
    chatInput.value = ''
    chatSending.value = false
  }
  function dispose() { controller?.abort() }

  return { chatMessages, chatInput, chatSending, send, stop, reset, dispose }
}
