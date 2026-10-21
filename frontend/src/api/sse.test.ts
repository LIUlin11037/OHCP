import { describe, expect, it } from 'vitest'
import { parseSseStream } from './sse'

function streamFrom(chunks: string[]): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      const encoder = new TextEncoder()
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
}

describe('parseSseStream', () => {
  it('joins multiline data and supports CRLF event boundaries', async () => {
    const events = []
    for await (const event of parseSseStream(streamFrom(['event: token\r\ndata: 第一行\r\ndata: 第二行\r\n\r\n']))) {
      events.push(event)
    }

    expect(events).toEqual([{ event: 'token', data: '第一行\n第二行' }])
  })

  it('decodes Chinese characters split across network chunks', async () => {
    const bytes = new TextEncoder().encode('event: token\ndata: 水稻叶片\n\n')
    const splitAt = bytes.indexOf(0xe6) + 1
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(bytes.slice(0, splitAt))
        controller.enqueue(bytes.slice(splitAt))
        controller.close()
      },
    })
    const events = []
    for await (const event of parseSseStream(stream)) events.push(event)

    expect(events[0]?.data).toBe('水稻叶片')
  })

  it('flushes a final event without a trailing blank line', async () => {
    const events = []
    for await (const event of parseSseStream(streamFrom(['event: done\ndata: {}']))) events.push(event)

    expect(events).toEqual([{ event: 'done', data: '{}' }])
  })

  it('emits heartbeat comments so the consumer can reset its idle timer', async () => {
    const events = []
    for await (const event of parseSseStream(streamFrom([': ping\n\n']))) events.push(event)

    expect(events).toEqual([{ event: 'ping', data: '' }])
  })
})
