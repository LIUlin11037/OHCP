import { describe, expect, it } from 'vitest'
import { confidencePercent, safeHttpUrl } from './links'

describe('display helpers', () => {
  it('accepts only absolute HTTP and HTTPS source links', () => {
    expect(safeHttpUrl('https://example.org/source')).toBe('https://example.org/source')
    expect(safeHttpUrl('http://example.org/source')).toBe('http://example.org/source')
    expect(safeHttpUrl('javascript:alert(1)')).toBeUndefined()
    expect(safeHttpUrl('httpjavascript://example.org')).toBeUndefined()
    expect(safeHttpUrl('/relative/path')).toBeUndefined()
  })

  it('keeps confidence display within the percentage range', () => {
    expect(confidencePercent(0.84)).toBe(84)
    expect(confidencePercent(1.2)).toBe(100)
    expect(confidencePercent(-0.1)).toBe(0)
    expect(confidencePercent(Number.NaN)).toBe(0)
  })
})
