<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { fetchHealth, isMockEnabled, type HealthResponse } from './api'

const route = useRoute()
const health = ref<HealthResponse>()
const healthError = ref(false)
let healthController: AbortController | undefined
const activeTitle = computed(() => String(route.meta.title || '智能诊断'))

async function checkHealth() {
  healthController?.abort()
  const controller = new AbortController()
  healthController = controller
  healthError.value = false
  try {
    const response = await fetchHealth(controller.signal)
    if (!controller.signal.aborted) health.value = response
  } catch { if (!controller.signal.aborted) healthError.value = true }
}

onMounted(checkHealth)
onBeforeUnmount(() => healthController?.abort())
</script>

<template>
  <div class="app-shell">
    <aside class="side-rail">
      <RouterLink class="brand" to="/" aria-label="禾诊首页">
        <span class="brand-mark">禾</span>
        <span><strong>禾诊</strong><small>FIELD NOTES · 01</small></span>
      </RouterLink>

      <div class="rail-caption">工作台</div>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink to="/" class="nav-link" :class="{ active: route.meta.nav === 'diagnose' }">
          <span class="nav-icon">◉</span><span>智能诊断</span><span class="nav-current">→</span>
        </RouterLink>
        <RouterLink to="/knowledge" class="nav-link" :class="{ active: route.meta.nav === 'knowledge' }">
          <span class="nav-icon">✳</span><span>营养知识</span>
        </RouterLink>
        <RouterLink to="/dashboard" class="nav-link" :class="{ active: route.meta.nav === 'dashboard' }">
          <span class="nav-icon">▥</span><span>服务看板</span>
        </RouterLink>
      </nav>

      <div class="rail-bottom">
        <div class="field-note">
          <div class="note-kicker">FIELD NOTE 014</div>
          <p>先观察，再判断。<br>每一片叶子都有线索。</p>
          <span class="note-sprig">✳</span>
        </div>
        <div class="rail-profile"><div class="profile-avatar">农</div><div><strong>农技工作台</strong><small>作物营养助手</small></div><span class="more-dots">···</span></div>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="breadcrumbs"><span>禾诊</span><span class="crumb-slash">/</span><strong>{{ activeTitle }}</strong></div>
        <div class="topbar-right">
          <span v-if="isMockEnabled()" class="environment-tag"><i></i>演示环境</span>
          <span v-else class="environment-tag real"><i></i>接口模式</span>
          <button class="health-chip" type="button" @click="checkHealth" :title="healthError ? '检查失败，点击重试' : '刷新服务状态'">
            <span class="health-dot" :class="healthError ? 'is-error' : (health?.status === 'ok' ? 'is-ok' : 'is-waiting')"></span>
            {{ healthError ? '状态未知' : health?.status === 'ok' ? '服务正常' : isMockEnabled() ? '演示服务' : '检查中' }}
          </button>
          <div class="today-date">农事 · {{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }) }}</div>
        </div>
      </header>

      <div v-if="isMockEnabled()" class="mock-banner" role="status">
        <span class="mock-banner-icon">!</span>
        <span><strong>当前为 Mock 演示</strong>　页面中的诊断、施肥与统计数据均为示例，不可用于田间决策。</span>
        <RouterLink to="/dashboard">查看联调状态 <span>↗</span></RouterLink>
      </div>

      <RouterView />

      <footer class="app-footer">
        <span>禾诊 · 作物营养助手</span><span>依据作物表现，连接科学管理</span>
      </footer>
    </main>
  </div>
</template>
