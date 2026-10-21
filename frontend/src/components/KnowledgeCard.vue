<script setup lang="ts">
import type { KnowledgeResponse } from '../api'
import { safeHttpUrl } from '../api/links'

defineProps<{ item: KnowledgeResponse; compact?: boolean }>()
</script>

<template>
  <article class="knowledge-card" :class="{ compact }">
    <div class="knowledge-card-head">
      <span class="element-symbol">{{ item.element }}</span>
      <div><span class="eyebrow">元素科普</span><h3>{{ item.elementName || '未提供' }}</h3></div>
    </div>
    <p class="knowledge-overview">{{ item.overview || '未提供' }}</p>
    <div v-if="item.symptoms?.length" class="knowledge-section">
      <span class="section-label">常见表现</span>
      <div class="knowledge-pills"><span v-for="symptom in item.symptoms" :key="symptom">{{ symptom }}</span></div>
    </div>
    <div v-else class="knowledge-section"><span class="section-label">常见表现</span><div class="empty-muted">{{ item.symptoms ? '暂无内容' : '未提供' }}</div></div>
    <div v-if="!compact && item.crops?.length" class="knowledge-section">
      <span class="section-label">作物范围</span><div class="knowledge-pills"><span v-for="crop in item.crops" :key="crop">{{ crop }}</span></div>
    </div>
    <div v-else-if="!compact" class="knowledge-section"><span class="section-label">作物范围</span><div class="empty-muted">{{ item.crops ? '暂无内容' : '未提供' }}</div></div>
    <div v-if="item.advice?.length" class="knowledge-note"><span>✳</span><div><span class="section-label">管理提示</span><p>{{ item.advice.join('；') }}</p></div></div>
    <div v-else class="knowledge-note"><span>✳</span><div><span class="section-label">管理提示</span><p>{{ item.advice ? '暂无内容' : '未提供' }}</p></div></div>
    <div v-if="item.sources?.length" class="knowledge-sources">
      <span class="section-label">资料来源</span>
      <a v-for="source in item.sources" :key="source.title" :href="safeHttpUrl(source.url)" target="_blank" rel="noopener noreferrer">{{ source.title }} ↗</a>
    </div>
    <div v-else class="knowledge-sources"><span class="section-label">资料来源</span><span class="empty-muted">{{ item.sources ? '暂无来源资料' : '未提供' }}</span></div>
  </article>
</template>
