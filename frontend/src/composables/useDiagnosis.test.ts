import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import type { DiagnosisResponse } from '../api'
import { diagnose } from '../api'
import { useDiagnosis, type DiagnosisForm } from './useDiagnosis'

vi.mock('../api', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api')>()
  return { ...original, diagnose: vi.fn(), fetchAdvice: vi.fn() }
})

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

function followUp(requestId: string): DiagnosisResponse {
  return { requestId, decision: 'NEED_MORE_INFO', candidates: [] }
}

describe('useDiagnosis', () => {
  beforeEach(() => vi.mocked(diagnose).mockReset())

  it('does not let an older response replace the latest diagnosis', async () => {
    const first = deferred<DiagnosisResponse>()
    vi.mocked(diagnose)
      .mockImplementationOnce(() => first.promise)
      .mockResolvedValueOnce(followUp('new-request'))
    const form = reactive<DiagnosisForm>({ crop: '水稻', symptomText: '足够长的水稻叶片变化描述', part: '', leafAge: '', env: '', soil: '', stage: '', mode: 'farmer' })
    const diagnosis = useDiagnosis(form)

    const oldRequest = diagnosis.runDiagnosis()
    form.symptomText = '更新后的水稻叶片变化描述信息'
    await diagnosis.runDiagnosis()
    first.resolve(followUp('old-request'))
    await oldRequest

    expect(diagnosis.result.value?.requestId).toBe('new-request')
    expect(diagnosis.loading.value).toBe(false)
    diagnosis.dispose()
  })

  it('validates required form data before making a request', async () => {
    vi.mocked(diagnose).mockResolvedValue(followUp('unused'))
    const form = reactive<DiagnosisForm>({ crop: '', symptomText: '缺少作物', part: '', leafAge: '', env: '', soil: '', stage: '', mode: 'farmer' })
    const diagnosis = useDiagnosis(form)

    await diagnosis.runDiagnosis()

    expect(diagnosis.errorText.value).toBe('请填写作物名称。')
    expect(diagnose).not.toHaveBeenCalled()
    diagnosis.dispose()
  })

  it.each([
    ['1234567', '症状描述请控制在 8–500 字。'],
    ['1'.repeat(501), '症状描述请控制在 8–500 字。'],
  ])('rejects symptom description length %i', async (symptomText, message) => {
    const form = reactive<DiagnosisForm>({ crop: '水稻', symptomText, part: '', leafAge: '', env: '', soil: '', stage: '', mode: 'farmer' })
    const diagnosis = useDiagnosis(form)

    await diagnosis.runDiagnosis()

    expect(diagnosis.errorText.value).toBe(message)
    expect(diagnose).not.toHaveBeenCalled()
    diagnosis.dispose()
  })

  it.each([8, 500])('accepts symptom description length %i', async (length) => {
    vi.mocked(diagnose).mockResolvedValue(followUp('accepted'))
    const form = reactive<DiagnosisForm>({ crop: '水稻', symptomText: 'x'.repeat(length), part: '', leafAge: '', env: '', soil: '', stage: '', mode: 'farmer' })
    const diagnosis = useDiagnosis(form)

    await diagnosis.runDiagnosis()

    expect(diagnose).toHaveBeenCalledOnce()
    expect(diagnosis.result.value?.requestId).toBe('accepted')
    diagnosis.dispose()
  })

  it('rejects environment descriptions longer than 200 characters', async () => {
    const form = reactive<DiagnosisForm>({ crop: '水稻', symptomText: '水稻叶片轻微发黄，想了解原因', part: '', leafAge: '', env: 'x'.repeat(201), soil: '', stage: '', mode: 'farmer' })
    const diagnosis = useDiagnosis(form)

    await diagnosis.runDiagnosis()

    expect(diagnosis.errorText.value).toBe('环境描述最多 200 字。')
    expect(diagnose).not.toHaveBeenCalled()
    diagnosis.dispose()
  })
})
