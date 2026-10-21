export type SseEvent = {
  event: string
  data: string
}

function decodeFrame(frame: string): SseEvent | undefined {
  let event = 'message'
  const data: string[] = []

  let heartbeat = false
  for (const line of frame.split(/\r?\n/)) {
    if (!line) continue
    if (line.startsWith(':')) { heartbeat = true; continue }
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '')
    if (field === 'event') event = value
    if (field === 'data') data.push(value)
  }

  if (data.length === 0) return heartbeat ? { event: 'ping', data: '' } : undefined
  return { event, data: data.join('\n') }
}

/** Reads SSE frames from a network stream, including frames split across UTF-8 chunks. */
export async function* parseSseStream(
  body: ReadableStream<Uint8Array>,
  signal?: AbortSignal,
): AsyncGenerator<SseEvent> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const cancelOnAbort = () => { void reader.cancel(signal?.reason).catch(() => undefined) }
  signal?.addEventListener('abort', cancelOnAbort, { once: true })

  try {
    while (true) {
      if (signal?.aborted) break
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let boundary = /\r?\n\r?\n/.exec(buffer)
      while (boundary?.index !== undefined) {
        const frame = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary[0].length)
        const event = decodeFrame(frame)
        if (event) yield event
        boundary = /\r?\n\r?\n/.exec(buffer)
      }
    }

    buffer += decoder.decode()
    const finalEvent = decodeFrame(buffer)
    if (finalEvent) yield finalEvent
  } finally {
    signal?.removeEventListener('abort', cancelOnAbort)
    if (signal?.aborted) await reader.cancel(signal.reason).catch(() => undefined)
    reader.releaseLock()
  }
}
