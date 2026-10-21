<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { fetchKnowledge, type KnowledgeResponse } from '../api'
import KnowledgeCard from '../components/KnowledgeCard.vue'

const elements = ['N', 'P', 'K', 'Mg', 'Fe', 'Zn', 'Ca', 'B', 'Mn', 'Cu', 'Mo', 'S']
const active = ref('N')
const item = ref<KnowledgeResponse>()
const loading = ref(false)
const failed = ref(false)
let requestController: AbortController | undefined

async function load(element = active.value) {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  active.value = element
  loading.value = true
  failed.value = false
  try {
    const response = await fetchKnowledge(element, controller.signal)
    if (!controller.signal.aborted) item.value = response
  } catch { if (!controller.signal.aborted) failed.value = true }
  finally { if (!controller.signal.aborted) loading.value = false }
}

onMounted(() => void load())
onBeforeUnmount(() => requestController?.abort())
</script>

<template>
  <section class="page-content knowledge-page">
    <div class="page-heading">
      <div><div class="eyebrow"><span class="eyebrow-line"></span> FIELD LIBRARY · 科普</div><h1>认识作物的<span class="heading-accent">营养语言。</span></h1><p class="heading-copy">从元素与作物的关系，理解田间症状背后的线索。</p></div>
      <div class="season-stamp"><span>REFERENCE</span><strong>12</strong><small>营养元素索引</small></div>
    </div>

    <div class="knowledge-layout">
      <aside class="element-index panel">
        <div class="index-heading"><span class="step-label">ELEMENTS / 12</span><h2>元素索引</h2><p>选择元素查看知识条目</p></div>
        <div class="element-list"><button v-for="element in elements" :key="element" type="button" :class="{ active: element === active }" @click="load(element)"><span class="element-mini">{{ element }}</span><span>{{ ({ N: '氮', P: '磷', K: '钾', Mg: '镁', Fe: '铁', Zn: '锌', Ca: '钙', B: '硼', Mn: '锰', Cu: '铜', Mo: '钼', S: '硫' } as Record<string, string>)[element] }}</span><span class="index-arrow">→</span></button></div>
        <div class="index-footnote"><span>✳</span> 以真实知识库返回内容为准</div>
      </aside>
      <div class="knowledge-detail panel">
        <div class="detail-toolbar"><div><span class="step-label">KNOWLEDGE ENTRY</span><span class="entry-id">{{ active }}</span></div><button class="refresh-advice" type="button" :disabled="loading" aria-label="刷新知识条目" @click="load()">↻</button></div>
        <div v-if="loading" class="knowledge-loading"><span class="loading-orbit"></span><strong>正在读取知识条目</strong><small>请稍候片刻</small></div>
        <div v-else-if="failed" class="knowledge-failure"><div class="failure-mark">!</div><h2>知识条目暂时不可用</h2><p>本次失败不会影响其他页面与已经完成的诊断。</p><button class="primary-button compact-button" type="button" @click="load()">重试 <span>→</span></button></div>
        <KnowledgeCard v-else-if="item" :item="item" />
        <div class="knowledge-reference-note"><span>NOTE</span><p>条目和来源由后端知识库提供。页面不生成缺失的作物范围、诊断结论或施肥内容。</p></div>
      </div>
    </div>
  </section>
</template>
