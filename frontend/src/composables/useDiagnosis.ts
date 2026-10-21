import { computed, ref, watch, type UnwrapNestedRefs } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiError, diagnose, fetchAdvice, visibleTraceId, type AdviceResponse, type DiagnosisResponse, type DiagnoseField } from '../api'

export interface DiagnosisForm {
  crop: string
  symptomText: string
  part: string
  leafAge: string
  env: string
  soil: string
  stage: string
  mode: 'farmer' | 'pro'
}

type ReactiveDiagnosisForm = UnwrapNestedRefs<DiagnosisForm>

export function useDiagnosis(form: ReactiveDiagnosisForm) {
  const loading = ref(false)
  const adviceLoading = ref(false)
  const adviceError = ref(false)
  const result = ref<DiagnosisResponse>()
  const advice = ref<AdviceResponse>()
  const selectedIndex = ref(0)
  const errorText = ref('')
  const trace = ref<string>()
  const retryAfterSeconds = ref(0)
  const activeCandidate = computed(() => result.value?.candidates?.[selectedIndex.value])
  const isNoMatch = computed(() => result.value?.decision === 'NO_MATCH')
  const isFollowUp = computed(() => result.value?.decision === 'NEED_MORE_INFO')

  let diagnoseController: AbortController | undefined
  let adviceController: AbortController | undefined
  let retryTimer: ReturnType<typeof setInterval> | undefined

  watch(() => form.crop, (crop, previousCrop) => {
    if (crop === previousCrop) return
    diagnoseController?.abort()
    adviceController?.abort()
    result.value = undefined
    advice.value = undefined
    loading.value = false
    adviceLoading.value = false
    adviceError.value = false
  })

  function clearError() { errorText.value = ''; trace.value = undefined }

  function apiErrorMessage(error: unknown): string {
    if (error instanceof ApiError) {
      trace.value = visibleTraceId(error.traceId)
      if (error.code === 1001) return '请检查必填内容后重试。'
      if (error.code === 1002) return '有选项暂不受支持，请调整后重试。'
      if (error.code === 1003) return '描述字数超出限制，请缩短后重试。'
      if (error.code === 2001) return '服务正在繁忙，请稍后再试。'
      if (error.code === 4001) {
        retryAfterSeconds.value = 5
        clearInterval(retryTimer)
        retryTimer = setInterval(() => {
          retryAfterSeconds.value = Math.max(0, retryAfterSeconds.value - 1)
          if (retryAfterSeconds.value === 0) clearInterval(retryTimer)
        }, 1000)
        return '操作过于频繁，请稍后再试。'
      }
      if (error.code === 5000) return '服务暂时不可用，请保留描述并重试。'
    }
    return '连接服务失败，请检查服务状态后重试。'
  }

  async function loadAdvice(element: string, mode = form.mode) {
    adviceController?.abort()
    const controller = new AbortController()
    adviceController = controller
    adviceLoading.value = true
    adviceError.value = false
    try {
      const response = await fetchAdvice(element, form.crop, mode, controller.signal)
      if (!controller.signal.aborted && activeCandidate.value?.element === element && form.mode === mode) advice.value = response
    } catch {
      if (!controller.signal.aborted) {
        adviceError.value = true
        ElMessage.error('施肥方案暂时无法获取，可单独重试。')
      }
    } finally {
      if (!controller.signal.aborted) adviceLoading.value = false
    }
  }

  async function runDiagnosis(nextForm: ReactiveDiagnosisForm = form) {
    clearError()
    const symptomLength = nextForm.symptomText.trim().length
    if (!nextForm.crop.trim()) { errorText.value = '请填写作物名称。'; return }
    if (symptomLength < 8 || symptomLength > 500) { errorText.value = '症状描述请控制在 8–500 字。'; return }
    if (nextForm.env.length > 200) { errorText.value = '环境描述最多 200 字。'; return }

    diagnoseController?.abort()
    adviceController?.abort()
    adviceLoading.value = false
    adviceError.value = false
    const controller = new AbortController()
    diagnoseController = controller
    loading.value = true
    result.value = undefined
    advice.value = undefined
    selectedIndex.value = 0
    try {
      const response = await diagnose({
        crop: nextForm.crop.trim(),
        symptomText: nextForm.symptomText.trim(),
        ...(nextForm.part && { part: nextForm.part }),
        ...(nextForm.leafAge && { leafAge: nextForm.leafAge }),
        ...(nextForm.env && { env: nextForm.env.trim() }),
        ...(nextForm.soil && { soil: nextForm.soil }),
        ...(nextForm.stage && { stage: nextForm.stage }),
        mode: nextForm.mode,
      }, controller.signal)
      if (controller.signal.aborted) return
      if (!response || typeof response !== 'object' || !['CONFIRMED', 'NEED_MORE_INFO', 'NO_MATCH'].includes(response.decision)) {
        errorText.value = '收到暂不支持的结果格式，请稍后重试。'
        return
      }
      if (response.decision === 'CONFIRMED' && !response.candidates?.some((candidate) => typeof candidate?.element === 'string' && candidate.element.trim())) {
        errorText.value = '诊断响应缺少有效候选，请检查服务响应后重试。'
        return
      }
      result.value = { ...response, candidates: response.candidates?.slice(0, 3) ?? [] }
      if (response.decision === 'CONFIRMED' && response.candidates?.length) await loadAdvice(response.candidates[0]!.element, nextForm.mode)
    } catch (error) {
      if (!controller.signal.aborted) errorText.value = apiErrorMessage(error)
    } finally {
      if (!controller.signal.aborted) loading.value = false
    }
  }

  function selectCandidate(index: number) {
    selectedIndex.value = index
    const candidate = result.value?.candidates?.[index]
    if (candidate) void loadAdvice(candidate.element)
  }

  function switchMode(mode: DiagnosisForm['mode']) {
    if (form.mode === mode) return
    form.mode = mode
    if (activeCandidate.value) void loadAdvice(activeCandidate.value.element, mode)
  }

  function applyFollowUp(option: { label: string; expectedField?: string; value: string }) {
    const field = option.expectedField as DiagnoseField | undefined
    const allowed: DiagnoseField[] = ['part', 'leafAge', 'env', 'soil', 'stage']
    if (field && allowed.includes(field)) form[field] = option.value
    else {
      form.symptomText = `${form.symptomText.trim()}；补充信息：${option.value}`
      ElMessage.info(`收到未识别的补充字段“${option.expectedField || '未提供'}”，已将答案附加到症状描述。`)
    }
  }

  function reset() {
    diagnoseController?.abort()
    adviceController?.abort()
    result.value = undefined
    advice.value = undefined
    adviceError.value = false
    selectedIndex.value = 0
    errorText.value = ''
    trace.value = undefined
    loading.value = false
    adviceLoading.value = false
  }

  function dispose() {
    diagnoseController?.abort()
    adviceController?.abort()
    clearInterval(retryTimer)
  }

  return {
    loading, adviceLoading, adviceError, result, advice, selectedIndex, errorText, trace, retryAfterSeconds,
    activeCandidate, isNoMatch, isFollowUp, runDiagnosis, loadAdvice, selectCandidate,
    switchMode, applyFollowUp, clearError, reset, dispose,
  }
}
