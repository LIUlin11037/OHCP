import { describe, expect, it } from 'vitest'
import { mockDiagnose } from './mock'

const request = (symptomText: string, leafAge = '') => ({ crop: '水稻', symptomText, leafAge, mode: 'farmer' as const })

describe('Mock diagnosis fixtures', () => {
  it('returns the follow-up state for a short symptom description', async () => {
    await expect(mockDiagnose(request('叶片发黄'))).resolves.toMatchObject({ decision: 'NEED_MORE_INFO', candidates: [] })
    await expect(mockDiagnose(request('叶片发黄', '老叶'))).resolves.toMatchObject({ decision: 'CONFIRMED' })
  })

  it('makes no-match and degraded samples available for demo walkthroughs', async () => {
    await expect(mockDiagnose(request('演示无匹配状态的完整症状描述'))).resolves.toMatchObject({ decision: 'NO_MATCH' })
    await expect(mockDiagnose(request('演示降级状态的完整症状描述'))).resolves.toMatchObject({ decision: 'CONFIRMED', isDegraded: true })
  })
})
