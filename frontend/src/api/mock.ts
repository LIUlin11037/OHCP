import type {
  AdviceMode,
  AdviceResponse,
  DiagnoseRequest,
  DiagnosisResponse,
  HealthResponse,
  HotQuery,
  KnowledgeResponse,
} from './types'
import confirmedExample from '../../examples/mock/diagnosis-confirmed.json'
import followUpExample from '../../examples/mock/diagnosis-follow-up.json'
import noMatchExample from '../../examples/mock/diagnosis-no-match.json'
import degradedExample from '../../examples/mock/diagnosis-degraded.json'
import adviceExample from '../../examples/mock/advice.json'
import knowledgeExample from '../../examples/mock/knowledge.json'
import hotExample from '../../examples/mock/stats-hot.json'
import healthExample from '../../examples/mock/health.json'

const wait = (ms = 320) => new Promise((resolve) => setTimeout(resolve, ms))

export async function mockDiagnose(input: DiagnoseRequest): Promise<DiagnosisResponse> {
  await wait(520)
  if (input.symptomText.includes('无匹配')) return structuredClone(noMatchExample)
  if (input.symptomText.includes('降级')) return structuredClone(degradedExample)
  const uncertain = input.symptomText.length < 22 && !input.leafAge
  return structuredClone(uncertain ? followUpExample : confirmedExample)
}

export async function mockAdvice(element: string, mode: AdviceMode): Promise<AdviceResponse> {
  await wait(280)
  return { ...structuredClone(adviceExample), element, elementName: `${element} 元素（演示）`, mode }
}

const elements: Record<string, KnowledgeResponse> = Object.fromEntries(
  ['N', 'P', 'K', 'Mg', 'Fe', 'Zn', 'Ca', 'B', 'Mn', 'Cu', 'Mo', 'S'].map((element) => [element, {
    ...structuredClone(knowledgeExample),
    element,
    elementName: `${element} 元素`,
  }]),
)

export async function mockKnowledge(element: string): Promise<KnowledgeResponse> {
  await wait(240)
  return elements[element] ?? elements.N!
}

export async function mockHotQueries(): Promise<HotQuery[]> {
  await wait(260)
  return structuredClone(hotExample)
}

export async function mockHealth(): Promise<HealthResponse> {
  await wait(220)
  return {
    ...structuredClone(healthExample),
    checkedAt: new Date().toISOString(),
  }
}

export async function mockReload(): Promise<void> {
  await wait(450)
}

export async function mockChatEvents(question: string): Promise<Array<{ event: string; data: unknown }>> {
  await wait(200)
  return [
    { event: 'meta', data: { sessionId: 'demo-session-001' } },
    { event: 'token', data: { delta: `Mock 演示回复：已收到“${question}”。真实追问结果由后端服务返回。` } },
    { event: 'done', data: { status: 'done' } },
  ]
}
