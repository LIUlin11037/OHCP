# crawler —— 离线知识生产

爬虫是**离线作业**，跑完就退出，不参与后端启动，也不与后端进程通信。
唯一的交付方式是文件：把结果写进 `../data/`。

## 四步链路

```bash
pip install -r requirements.txt

python crawl.py                 # 1. 抓取 -> data/raw/*.html
python clean.py                 # 2. 清洗去重 -> crawler/out/clean.jsonl
python struct.py                # 3. 抽候选 -> crawler/out/candidates.jsonl
python embed.py                 # 4. 向量化 -> data/vectors/vectors.jsonl
```

第 3 步产出的是**候选**（`needsReview: true`），需要人工把要用的条目改成
`needsReview: false`，再 `python struct.py --merge` 并入 `data/kb/deficiency_kb.jsonl`。
原因很直接：自动抽取能捞句子，但施肥用量与安全提示必须人工核对，否则会出错误建议。

## 合规

单站点串行、间隔 ≥1 秒、带明确 UA、只抓公开页面、每条保留 `sourceUrl` 与抓取时间。
不采集个人信息，不绕过登录与付费墙。

## 兜底

`data/kb/` 里已有 8 条人工整理的核心知识（N/P/K/Mg/Fe/Zn/Ca/B）。
**即使爬虫完全跑失败，后端也能启动并完成演示。** 这是刻意的设计，不是偷懒。

## 环境变量（仅 embed.py 需要）

```
EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
EMBEDDING_API_KEY=sk-xxx
EMBEDDING_MODEL=text-embedding-v3
```

没配也能跑前 3 步；`vectors.jsonl` 缺失时后端自动降级为「规则 + BM25」，不影响识别。
