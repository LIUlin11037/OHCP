export type AdviceMode = 'farmer' | 'pro'
export type Decision = 'CONFIRMED' | 'NEED_MORE_INFO' | 'NO_MATCH'
export type DiagnoseField = 'part' | 'leafAge' | 'env' | 'soil' | 'stage'

export interface DiagnoseRequest {
  crop: string
  symptomText: string
  part?: string
  leafAge?: string
  env?: string
  soil?: string
  stage?: string
  mode: AdviceMode
}

export interface DiagnosisCandidate {
  element: string
  elementName: string
  confidence?: number | null
  summary?: string | null
  matchingFeatures?: string[] | null
  evidence?: Array<{ title: string; source?: string; excerpt?: string }> | null
}

export interface DiagnosisResponse {
  requestId?: string
  sessionId?: string
  decision: Decision | string
  candidates?: DiagnosisCandidate[] | null
  followUp?: { question: string; options?: Array<{ label: string; expectedField?: DiagnoseField | string; value: string }> }
  isDegraded?: boolean
  degradedMode?: string
  greenTip?: string
}

export interface AdviceResponse {
  element: string
  elementName: string
  mode: AdviceMode
  introduction: string
  agronomicControl?: string[] | null
  organicPlan?: Array<{ name: string; method?: string; nutrientContent?: string; notes?: string }> | null
  chemicalPlan?: Array<{ name: string; amount?: string; method?: string; nutrientContent?: string; notes?: string }> | null
  safetyReminders?: string[] | null
  sources?: Array<{ title: string; url?: string }> | null
}

export interface KnowledgeResponse {
  element: string
  elementName: string
  overview?: string | null
  symptoms?: string[] | null
  crops?: string[] | null
  advice?: string[] | null
  sources?: Array<{ title: string; url?: string }> | null
}

export interface HotQuery {
  crop?: string | null
  symptom?: string | null
  count?: number | null
}

export interface HealthResponse {
  status?: string | null
  checkedAt?: string | null
  services?: Array<{ name?: string; status?: string; detail?: string }> | null
}

export interface ApiEnvelope<T> {
  code: number
  message?: string
  data: T
  traceId?: string
  costMs?: number
}

export interface ChatRequest {
  sessionId: string
  question: string
}
