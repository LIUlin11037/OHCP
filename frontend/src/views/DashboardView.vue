<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchHealth, fetchHotQueries, isMockEnabled, reloadKnowledge, type HealthResponse, type HotQuery } from '../api'

const health = ref<HealthResponse>()
const hot = ref<HotQuery[]>([])
const loading = ref(false)
const reloadLoading = ref(false)
const failed = ref(false)
const lastUpdated = ref('')
let requestController: AbortController | undefined
const maxCount = computed(() => Math.max(1, ...hot.value.map((item) => typeof item.count === 'number' ? item.count : 0)))
const checkedAtDisplay = computed(() => {
  if (!health.value?.checkedAt) return '未提供'
  const date = new Date(health.value.checkedAt)
  return Number.isNaN(date.getTime()) ? '未提供' : date.toLocaleString('zh-CN')
})

async function refresh() {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  failed.value = false
  try {
    const [healthResult, hotResult] = await Promise.all([fetchHealth(controller.signal), fetchHotQueries(controller.signal)])
    if (controller.signal.aborted) return
    if (!healthResult || typeof healthResult !== 'object' || !Array.isArray(hotResult)) throw new Error('响应结构无效')
    health.value = healthResult
    hot.value = hotResult
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch { if (!controller.signal.aborted) failed.value = true }
  finally { if (!controller.signal.aborted) loading.value = false }
}

async function confirmReload() {
  if (reloadLoading.value) return
  try {
    await ElMessageBox.confirm('确认重载后端知识库？正在执行的知识查询可能短暂受影响。', '重载知识库', {
      confirmButtonText: '确认重载', cancelButtonText: '取消', type: 'warning', distinguishCancelAndClose: true,
    })
  } catch { return }
  reloadLoading.value = true
  try {
    await reloadKnowledge()
    ElMessage.success(isMockEnabled() ? '演示操作已完成；未连接真实知识库。' : '知识库重载请求已提交。')
    await refresh()
  } catch { ElMessage.error('重载请求未成功，请检查服务状态。') }
  finally { reloadLoading.value = false }
}

onMounted(() => void refresh())
onBeforeUnmount(() => requestController?.abort())
</script>

<template>
  <section class="page-content dashboard-page">
    <div class="page-heading">
      <div><div class="eyebrow"><span class="eyebrow-line"></span> SYSTEM OVERVIEW · 服务</div><h1>把握服务与知识<span class="heading-accent">状态。</span></h1><p class="heading-copy">了解当前服务连接和热门查询数据，及时检查知识资源。</p></div>
      <div class="dashboard-actions"><span class="last-updated">{{ lastUpdated ? `最近更新 ${lastUpdated}` : '尚未更新' }}</span><button class="outline-button" type="button" :disabled="loading" @click="refresh"><span :class="{ rotating: loading }">↻</span> 刷新数据</button></div>
    </div>

    <div v-if="failed" class="dashboard-error" role="alert"><span>!</span><div><strong>部分状态未能加载</strong><small>检查失败不会清除已有诊断结果或历史信息。</small></div><button class="text-button" type="button" @click="refresh">重试</button></div>

    <div class="dashboard-grid">
      <section class="panel health-panel">
        <div class="panel-heading-row"><div><span class="step-label">SERVICE HEALTH</span><h2>服务自检</h2><p>显示后端实际提供的服务状态</p></div><span class="status-summary" :class="health?.status === 'ok' ? 'healthy' : 'pending'"><i></i>{{ health?.status === 'ok' ? '全部正常' : health?.status === 'demo' ? '演示数据' : health?.status || '状态未提供' }}</span></div>
        <div v-if="loading && !health" class="dashboard-loading">正在检查服务…</div>
        <div v-else-if="health?.services?.length" class="service-list"><article v-for="(service, index) in health.services" :key="service.name || index" class="service-row"><span class="service-mark" :class="{ good: service.status === 'ok' }">{{ service.status === 'ok' ? '✓' : '·' }}</span><div class="service-copy"><strong>{{ service.name || '未提供' }}</strong><small>{{ service.detail || '未提供' }}</small></div><span class="service-state" :class="{ good: service.status === 'ok' }">{{ service.status || '未提供' }}</span></article></div>
        <div v-else-if="health" class="service-empty">{{ health.services ? '暂无服务状态' : '未提供服务状态' }}</div>
        <div v-else class="service-empty">状态数据未提供</div>
        <div class="health-footer"><span>检查时间</span><strong>{{ checkedAtDisplay }}</strong></div>
      </section>

      <section class="panel hot-panel">
        <div class="panel-heading-row"><div><span class="step-label">FIELD TRENDS</span><h2>热门查询</h2><p>按后端统计结果展示</p></div><span class="trend-symbol">↗</span></div>
        <div v-if="loading && !hot.length" class="dashboard-loading">正在读取统计…</div>
        <div v-else-if="hot.length" class="hot-table"><div class="hot-table-head"><span>作物 / 症状</span><span>查询量</span></div><article v-for="(query, index) in hot" :key="`${query.crop || index}-${query.symptom || index}`" class="hot-row"><div class="hot-rank">0{{ index + 1 }}</div><div class="hot-copy"><strong>{{ query.crop || '未提供' }}</strong><small>{{ query.symptom || '未提供' }}</small><div class="bar-track"><i :style="{ width: `${(query.count ?? 0) / maxCount * 100}%` }"></i></div></div><strong class="hot-count">{{ query.count ?? '未提供' }}</strong></article></div>
        <div v-else class="service-empty">暂无统计数据</div>
        <div class="hot-footer"><span>统计周期与排序由后端契约定义</span><span>条目 {{ hot.length }}</span></div>
      </section>
    </div>

    <section class="panel reload-panel">
      <div class="reload-icon">↻</div><div class="reload-copy"><span class="step-label">MAINTENANCE</span><h2>知识库重载</h2><p>仅在后端允许的情况下执行。提交后会再次读取服务状态。</p></div><div class="reload-action"><button class="outline-button" type="button" :disabled="reloadLoading || isMockEnabled()" @click="confirmReload">{{ reloadLoading ? '正在提交…' : '重载知识库' }} <span>→</span></button><small v-if="isMockEnabled()">Mock 模式不会调用真实管理接口</small></div>
    </section>
    <div class="dashboard-footnote"><span>!</span>健康检查仅反映后端返回的依赖状态，不代表所有业务接口均可用。 {{ health?.status === 'demo' ? '当前为 Mock 状态示例。' : '' }}</div>
  </section>
</template>
