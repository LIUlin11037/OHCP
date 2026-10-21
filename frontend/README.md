# OHCP 农作物营养助手前端

Vue 3 + TypeScript 单页应用，覆盖作物诊断、施肥方案展示、流式追问、营养知识、热门查询、服务自检和知识重载入口。

## 本地运行

要求 Node.js `^22.18.0` 或 `>=24.12.0`，npm 11 可用。

```powershell
npm install
npm run dev
```

打开终端输出的本地地址（默认 `http://localhost:5173`）。默认 `VITE_USE_MOCKS=true`，所有演示数据都有 Mock 标记，不是后端契约，也不能用于农事决策。

## 检查和构建

```powershell
npm run typecheck
npm test
npm run build
```

生产文件生成在 `dist/`。Vue Router 使用 Hash 路由，可以将 `dist` 内容复制到 Spring Boot 静态资源目录，由后端同源提供页面和 `/api/v1` 接口。

## 后端联调

复制 `.env.example` 为 `.env.local`，将 `VITE_USE_MOCKS` 改为 `false`。开发服务器默认把 `/api` 代理到 `http://localhost:8080`，保留 `/api/v1` 路径；后端地址可通过 `VITE_API_PROXY_TARGET` 调整。公开环境变量只包含代理地址和 Mock 开关，不放密钥。

当前仓库没有后端 OpenAPI 或接口样例。`src/api/types.ts` 与 `examples/mock/` 只表达前端展示层目前假设的 DTO；这些字段和错误包装仍需后端确认。接口缺失、未提供字段和未验证的真实服务状态必须如实展示。

| 接口 | 页面用途 |
| --- | --- |
| `POST /api/v1/diagnose` | 作物诊断与补充信息 |
| `POST /api/v1/advice` | 获取当前候选的施肥方案 |
| `POST /api/v1/chat` | SSE 流式追问 |
| `GET /api/v1/knowledge/{element}` | 知识页与诊断抽屉 |
| `GET /api/v1/stats/hot` | 热门查询 |
| `GET /api/v1/health` | 服务状态 |
| `POST /api/v1/knowledge/reload` | 二次确认后的维护操作 |

## 目录

- `src/api/`：接口 DTO、请求错误处理、SSE 解析和 Mock 适配。
- `src/views/`：诊断、知识和服务看板。
- `examples/mock/`：仅用于页面演示的样例，禁止作为后端契约或农艺依据。
- `docs/前端开发计划书.md`：页面范围、接口待确认项和验收标准。
