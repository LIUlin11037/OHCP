<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { fetchKnowledge } from '../api'
import { confidencePercent, safeHttpUrl } from '../api/links'
import KnowledgeCard from '../components/KnowledgeCard.vue'
import { useDiagnosis, type DiagnosisForm } from '../composables/useDiagnosis'
import { useChat } from '../composables/useChat'

const form = reactive<DiagnosisForm>({ crop: '', symptomText: '', part: '', leafAge: '', env: '', soil: '', stage: '', mode: 'farmer' })
const showMore = ref(false)
const showEvidence = ref(false)
const knowledgeVisible = ref(false)
const knowledgeLoading = ref(false)
const knowledgeError = ref(false)
const knowledge = ref<Awaited<ReturnType<typeof fetchKnowledge>>>()
let knowledgeController: AbortController | undefined
const {
  loading, adviceLoading, adviceError, result, advice, selectedIndex, errorText, trace, retryAfterSeconds,
  activeCandidate, isNoMatch, isFollowUp, runDiagnosis, loadAdvice, selectCandidate,
  switchMode, applyFollowUp, clearError, reset: resetDiagnosis, dispose: disposeDiagnosis,
} = useDiagnosis(form)
const {
  chatMessages, chatInput, chatSending, send: sendChat, stop: stopChat,
  reset: resetChat, dispose: disposeChat,
} = useChat(
  () => result.value?.sessionId,
  (sessionId) => { if (result.value) result.value.sessionId = sessionId },
)
const chars = computed(() => form.symptomText.trim().length)

function startDiagnosis() { resetChat(); void runDiagnosis() }
function answerFollowUp(option: { label: string; expectedField?: string; value: string }) {
  resetChat()
  applyFollowUp(option)
  void runDiagnosis()
}

async function openKnowledge(element: string) {
  knowledgeController?.abort()
  const controller = new AbortController()
  knowledgeController = controller
  knowledgeVisible.value = true
  knowledgeLoading.value = true
  knowledgeError.value = false
  try {
    const response = await fetchKnowledge(element, controller.signal)
    if (!controller.signal.aborted) knowledge.value = response
  } catch { if (!controller.signal.aborted) knowledgeError.value = true }
  finally { if (!controller.signal.aborted) knowledgeLoading.value = false }
}

function useExample() {
  form.crop = '水稻'
  form.symptomText = '水稻下部老叶整体颜色变浅，近期长势偏弱，想了解可能的原因。'
  form.part = '叶片'
  form.leafAge = '老叶'
  form.stage = '营养生长期'
  clearError()
}
function resetForm() {
  resetDiagnosis(); knowledgeController?.abort(); resetChat()
  Object.assign(form, { crop: '', symptomText: '', part: '', leafAge: '', env: '', soil: '', stage: '', mode: 'farmer' })
  knowledge.value = undefined; knowledgeLoading.value = false; knowledgeError.value = false
}

onBeforeUnmount(() => { disposeDiagnosis(); disposeChat(); knowledgeController?.abort() })
</script>

<template>
  <section class="page-content diagnose-page">
    <div class="page-heading diagnose-heading">
      <div>
        <div class="eyebrow"><span class="eyebrow-line"></span> CROP HEALTH · 诊断台</div>
        <h1>从一片叶子，<em>读懂作物。</em></h1>
        <p class="heading-copy">描述作物表现，获取有依据的营养诊断与管理建议。</p>
      </div>
      <div class="season-stamp"><span>FIELD GUIDE</span><strong>01</strong><small>作物营养 · 识别</small></div>
    </div>

    <div class="diagnose-grid">
      <section class="panel form-panel">
        <div class="panel-topline"><div><span class="step-label">STEP 01 / 02</span><h2>描述作物情况</h2></div><span class="required-note"><i>*</i> 必填信息</span></div>
        <div class="form-rule"></div>

        <div class="field-block">
          <label for="crop-name">作物名称 <span class="required-star">*</span></label>
          <el-input id="crop-name" v-model="form.crop" placeholder="输入作物名称，如：水稻" maxlength="40" />
          <div class="field-help">请填写本次观察的作物</div>
        </div>

        <div class="field-block symptom-field">
          <div class="label-row"><label for="symptom-text">症状描述 <span class="required-star">*</span></label><button class="example-link" type="button" @click="useExample">填入示例 ↗</button></div>
          <el-input id="symptom-text" v-model="form.symptomText" type="textarea" :rows="5" maxlength="800" placeholder="例如：下部老叶开始泛黄，叶脉仍保持绿色……" />
          <div class="textarea-meta"><span>尽量描述颜色、位置和变化过程</span><span :class="{ 'count-warning': chars > 500 }">{{ chars }} / 500 字</span></div>
        </div>

        <button class="disclosure-row" type="button" :aria-expanded="showMore" @click="showMore = !showMore">
          <span class="disclosure-symbol">＋</span><span><strong>补充观察信息</strong><small>提供更多细节，帮助准确判断</small></span><span class="disclosure-chevron" :class="{ open: showMore }">⌄</span>
        </button>
        <div v-if="showMore" class="optional-fields">
          <div class="optional-field"><label>受影响部位</label><el-input v-model="form.part" placeholder="如：叶片、茎秆" /></div>
          <div class="optional-field"><label>叶片年龄</label><el-input v-model="form.leafAge" placeholder="如：新叶、老叶" /></div>
          <div class="optional-field"><label>生育阶段</label><el-input v-model="form.stage" placeholder="如：营养生长期" /></div>
          <div class="optional-field"><label>土壤情况</label><el-input v-model="form.soil" placeholder="已知的土壤信息" /></div>
          <div class="optional-field full"><label>近期环境 <span class="field-limit">{{ form.env.length }}/200</span></label><el-input v-model="form.env" type="textarea" :rows="2" maxlength="240" placeholder="降雨、温度或田间管理变化" /></div>
        </div>

        <div class="mode-row">
          <div><strong>建议视角</strong><small>调整施肥方案的表达方式</small></div>
          <div class="mode-switch" role="group" aria-label="建议视角">
            <button type="button" :aria-pressed="form.mode === 'farmer'" :class="{ selected: form.mode === 'farmer' }" @click="switchMode('farmer')">农户版</button>
            <button type="button" :aria-pressed="form.mode === 'pro'" :class="{ selected: form.mode === 'pro' }" @click="switchMode('pro')">专业版</button>
          </div>
        </div>

        <div v-if="errorText" class="inline-error" role="alert"><span>!</span><div><strong>{{ errorText }}</strong><small v-if="trace">诊断编号 · {{ trace }}</small></div><button type="button" :disabled="retryAfterSeconds > 0" @click="startDiagnosis">{{ retryAfterSeconds ? `${retryAfterSeconds}s 后重试` : '重试' }}</button></div>
        <div class="form-actions">
          <button class="primary-button" type="button" :disabled="loading || retryAfterSeconds > 0" @click="startDiagnosis"><span v-if="loading" class="button-spinner"></span><span>{{ loading ? '正在整理线索…' : retryAfterSeconds ? `${retryAfterSeconds}s 后可重试` : '开始诊断' }}</span><span v-if="!loading">→</span></button>
          <button class="text-button" type="button" @click="resetForm">重置表单</button>
        </div>
        <div class="privacy-note"><span>⌁</span>信息仅用于本次诊断流程</div>
      </section>

      <section class="panel result-panel">
        <div class="panel-topline"><div><span class="step-label">STEP 02 / 02</span><h2>诊断与管理建议</h2></div><span class="result-status" :class="{ ready: result }"><i></i>{{ loading ? '分析中' : result ? '已返回结果' : '等待输入' }}</span></div>
        <div class="form-rule"></div>

        <div v-if="!result && !loading" class="result-empty">
          <div class="empty-illustration"><span class="sun-disc"></span><span class="leaf-one">❧</span><span class="empty-ground"></span></div>
          <span class="eyebrow">OBSERVE · UNDERSTAND</span>
          <h3>每个症状，都是一条线索。</h3>
          <p>填写左侧作物信息后，诊断结果与施肥建议会显示在这里。</p>
          <div class="empty-footnote"><span>✳</span> 如条件允许，请结合土壤检测与当地农技建议</div>
        </div>

        <div v-else-if="loading && !result" class="result-loading"><span class="loading-orbit"></span><strong>正在分析症状</strong><small>正在整理作物信息与症状描述</small></div>

        <div v-else-if="isFollowUp" class="followup-result">
          <span class="decision-badge need-info"><i></i> 需要更多信息</span>
          <h3>{{ result?.followUp?.question || '请补充症状细节，帮助缩小判断范围。' }}</h3>
          <p>目前信息不足以形成判断，请选择最符合的一项。</p>
          <div v-if="result?.followUp?.options?.length" class="followup-options">
            <button v-for="option in result.followUp.options" :key="option.value" type="button" @click="answerFollowUp(option)">{{ option.label }}<span>→</span></button>
          </div>
          <div v-else class="followup-hint">可在左侧补充信息后重新诊断。</div>
        </div>

        <div v-else-if="isNoMatch" class="no-match-result">
          <span class="decision-badge no-match"><i></i> 暂无匹配</span>
          <h3>当前线索还不足以匹配到明确原因。</h3>
          <p>{{ result?.followUp?.question || '请补充更多田间表现或近期环境变化，并咨询当地农技人员。' }} 系统不会基于此结果给出施肥用量。</p>
          <div class="no-match-note">✳ 保留当前描述，补充更多信息后可以再次诊断。</div>
        </div>

        <div v-else-if="result?.decision === 'CONFIRMED' && !activeCandidate" class="followup-result"><span class="decision-badge no-match"><i></i> 响应异常</span><h3>服务没有返回可展示的候选结果。</h3><p>当前诊断结论已保留，请检查后端响应结构后重试。</p><button class="text-button" type="button" @click="startDiagnosis">重新诊断 →</button></div>
        <div v-else-if="result && activeCandidate" class="confirmed-result">
          <div class="decision-top"><span class="decision-badge confirmed"><i></i>{{ result.isDegraded ? '辅助模式 · 参考判断' : '初步判断' }}</span><span v-if="result.requestId" class="request-reference">编号 {{ result.requestId }}</span></div>
          <div class="candidate-title-row"><div><span class="eyebrow">可能的营养元素</span><h3>{{ activeCandidate.elementName || '未提供' }}</h3></div><button class="learn-link" type="button" @click="openKnowledge(activeCandidate.element)">了解更多 ↗</button></div>
          <p class="candidate-summary">{{ activeCandidate.summary || '未提供' }}</p>
          <div class="confidence-row"><span>匹配度</span><div class="confidence-track"><i :style="{ width: `${confidencePercent(activeCandidate.confidence)}%` }"></i></div><strong>{{ activeCandidate.confidence == null ? '未提供' : `${confidencePercent(activeCandidate.confidence)}%` }}</strong></div>
          <div v-if="result.candidates && result.candidates.length > 1" class="candidate-list"><button v-for="(candidate, index) in result.candidates || []" :key="candidate.element" type="button" :aria-pressed="index === selectedIndex" :class="{ active: index === selectedIndex }" @click="selectCandidate(index)"><span class="candidate-rank">0{{ index + 1 }}</span><span>{{ candidate.elementName || '未提供' }}</span><strong>{{ candidate.confidence == null ? '未提供' : `${confidencePercent(candidate.confidence)}%` }}</strong><i>→</i></button></div>
          <div class="evidence-block">
            <button class="evidence-toggle" type="button" :aria-expanded="showEvidence" @click="showEvidence = !showEvidence"><span>诊断依据</span><span>{{ activeCandidate.matchingFeatures?.length ?? '未提供' }} 条特征</span><i :class="{ open: showEvidence }">⌄</i></button>
            <div class="feature-tags"><span v-for="feature in activeCandidate.matchingFeatures || []" :key="feature">{{ feature }}</span><span v-if="activeCandidate.matchingFeatures?.length === 0 || !activeCandidate.matchingFeatures" class="empty-muted">{{ activeCandidate.matchingFeatures ? '暂无内容' : '未提供' }}</span></div>
            <div v-if="showEvidence" class="evidence-list"><article v-for="evidence in activeCandidate.evidence || []" :key="evidence.title"><strong>{{ evidence.title || '未提供' }}</strong><p>{{ evidence.excerpt || '未提供' }}</p><a v-if="safeHttpUrl(evidence.source)" :href="safeHttpUrl(evidence.source)" target="_blank" rel="noopener noreferrer">查看来源 ↗</a></article><span v-if="activeCandidate.evidence?.length === 0 || !activeCandidate.evidence" class="empty-muted">{{ activeCandidate.evidence ? '暂无来源资料' : '未提供' }}</span></div>
          </div>

          <div class="advice-section">
            <div class="advice-heading"><div><span class="eyebrow">NEXT STEPS</span><h4>管理建议</h4></div><span v-if="adviceLoading" class="refreshing-label"><i></i>正在更新</span><button v-else class="refresh-advice" type="button" @click="loadAdvice(activeCandidate.element)">↻</button></div>
            <div v-if="adviceError" class="inline-error advice-error" role="alert"><span>!</span><strong>{{ advice ? '方案更新失败，以下保留的是上一方案。' : '施肥方案暂时无法获取。' }}</strong><button type="button" @click="loadAdvice(activeCandidate.element)">局部重试</button></div>
            <div v-if="advice" class="advice-content" :class="{ refreshing: adviceLoading || adviceError }">
              <p class="advice-intro">{{ advice.introduction || '未提供' }}</p>
              <div class="advice-column"><span class="section-label">农艺调控</span><p v-for="(item, index) in advice.agronomicControl || []" :key="index">{{ item }}</p><span v-if="!advice.agronomicControl?.length" class="empty-muted">{{ advice.agronomicControl ? '暂无内容' : '未提供' }}</span></div>
              <div class="advice-column"><span class="section-label">有机 / 生物肥</span><div v-for="item in advice.organicPlan || []" :key="item.name" class="fertilizer-row"><strong>{{ item.name || '未提供' }}</strong><span>{{ item.method || '未提供' }} · {{ item.nutrientContent || '未提供' }}</span><small>{{ item.notes || '未提供' }}</small></div><span v-if="!advice.organicPlan?.length" class="empty-muted">{{ advice.organicPlan ? '暂无内容' : '未提供' }}</span></div>
              <div class="advice-column"><span class="section-label">化学施肥</span><div v-for="item in advice.chemicalPlan || []" :key="item.name" class="fertilizer-row"><strong>{{ item.name || '未提供' }}</strong><span>{{ item.amount || '未提供' }} · {{ item.method || '未提供' }}</span><small>{{ item.nutrientContent || '未提供' }} · {{ item.notes || '未提供' }}</small></div><span v-if="!advice.chemicalPlan?.length" class="empty-muted">{{ advice.chemicalPlan ? '暂无内容' : '未提供' }}</span></div>
              <div class="safety-note"><span>!</span><p>{{ advice.safetyReminders?.length ? advice.safetyReminders.join('；') : advice.safetyReminders ? '暂无内容' : '安全减量提醒：未提供' }}</p></div>
              <div class="advice-sources"><span class="section-label">资料来源</span><template v-if="advice.sources?.length"><a v-for="source in advice.sources" :key="source.title" :href="safeHttpUrl(source.url)" target="_blank" rel="noopener noreferrer">{{ source.title }}</a></template><span v-else class="empty-muted">{{ advice.sources ? '暂无来源资料' : '未提供' }}</span></div>
            </div>
            <div v-else-if="adviceLoading" class="advice-placeholder"><span class="button-spinner"></span><span>正在读取对应方案</span></div>
          </div>
          <div v-if="result.degradedMode" class="degraded-note">当前服务模式：{{ result.degradedMode }}</div>
          <div class="green-tip"><span>✳</span><p>{{ result.greenTip || '合理施肥，关注地力，减少养分流失。' }}</p></div>
        </div>
      </section>
    </div>

    <section v-if="result?.sessionId && result.decision === 'CONFIRMED'" class="panel chat-panel">
      <div class="chat-header"><div><span class="eyebrow">CONTINUE THE CONVERSATION</span><h2>继续追问</h2><p>基于本次诊断继续了解田间管理问题</p></div><button v-if="chatMessages.length" class="text-button" type="button" @click="resetChat">清空对话</button></div>
      <div v-if="chatMessages.length" class="chat-messages" aria-live="polite"><div v-for="(message, index) in chatMessages" :key="index" class="chat-message" :class="message.role"><span class="message-avatar">{{ message.role === 'user' ? '我' : '禾' }}</span><div class="message-body"><span class="message-role">{{ message.role === 'user' ? '你' : '禾诊助手' }}</span><p>{{ message.text || (message.status === 'streaming' ? '正在组织回答…' : '') }}</p><details v-for="(detail, detailIndex) in message.details || []" :key="detailIndex" class="chat-detail"><summary>{{ detail.label }}</summary><pre>{{ detail.value }}</pre></details><small v-if="message.status === 'interrupted'">已停止，保留已收到内容</small><small v-if="message.status === 'error'">连接中断</small></div></div></div>
      <form class="chat-composer" @submit.prevent="sendChat"><el-input v-model="chatInput" placeholder="还有什么想了解的？" :disabled="chatSending" maxlength="300" @keydown.enter.exact.prevent="sendChat" /><button v-if="chatSending" class="stop-chat" type="button" @click="stopChat">停止</button><button v-else class="send-chat" type="submit" :disabled="!chatInput.trim()">发送 <span>↑</span></button></form>
      <div class="chat-footnote">回复由后端服务生成，请结合实际田间情况判断</div>
    </section>

    <el-drawer v-model="knowledgeVisible" title="营养元素科普" size="min(520px, 94vw)" destroy-on-close>
      <div v-if="knowledgeLoading" class="drawer-loading"><span class="button-spinner"></span>正在读取知识条目</div>
      <div v-else-if="knowledgeError" class="drawer-error"><p>知识条目暂时无法获取。</p><button class="text-button" type="button" @click="activeCandidate && openKnowledge(activeCandidate.element)">重新加载</button></div>
      <KnowledgeCard v-else-if="knowledge" :item="knowledge" />
    </el-drawer>
  </section>
</template>
