import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'diagnose', component: () => import('../views/DiagnoseView.vue'), meta: { title: '智能诊断', nav: 'diagnose' } },
    { path: '/knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { title: '营养知识库', nav: 'knowledge' } },
    { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '服务与数据', nav: 'dashboard' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
