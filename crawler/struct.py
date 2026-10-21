"""
结构化器：从 clean.jsonl 中按元素关键词抽取症状句与施肥线索，产出候选条目。

设计说明（重要）：
  自动抽取只解决"把网页里跟缺素有关的句子捞出来"，不能自动写出准确的
  六段式施肥方案——那部分涉及用量与安全，必须人工校对。因此本脚本产出的是
  candidates.jsonl（needsReview=true），人工确认后再合并进 data/kb/deficiency_kb.jsonl。

用法：
  python struct.py                # 抽取候选，打印统计
  python struct.py --merge        # 把已人工校对（needsReview=false）的候选并入知识库
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = Path(__file__).resolve().parent / "out"
CAND = OUT_DIR / "candidates.jsonl"
KB = ROOT / "data" / "kb" / "deficiency_kb.jsonl"

ELEMENTS = {
    "氮": "N", "磷": "P", "钾": "K", "镁": "Mg", "铁": "Fe",
    "锌": "Zn", "钙": "Ca", "硼": "B", "硫": "S", "锰": "Mn",
}

CROPS = ["水稻", "小麦", "玉米", "大豆", "棉花", "油菜", "番茄", "黄瓜", "白菜",
         "马铃薯", "苹果", "柑橘", "葡萄", "茶叶", "花生", "甘蔗"]

SYMPTOM_WORDS = ["黄化", "发黄", "褪绿", "失绿", "紫红", "发紫", "焦枯", "焦边",
                 "褐斑", "坏死", "矮小", "瘦弱", "卷曲", "畸形", "小叶", "丛生",
                 "节间", "脐腐", "裂果", "花而不实", "倒伏", "白化", "厚脆", "分蘖"]

ADVICE_WORDS = ["施用", "追施", "基施", "叶面喷施", "用量", "kg", "公斤", "千克",
                "有机肥", "农家肥", "尿素", "过磷酸钙", "硫酸钾", "硫酸镁",
                "硫酸亚铁", "硫酸锌", "硼砂", "硝酸钙"]


def load_clean() -> list[dict]:
    path = OUT_DIR / "clean.jsonl"
    if not path.exists():
        raise SystemExit("找不到 clean.jsonl，请先运行 clean.py")
    return [json.loads(ln) for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]


def guess_element(text: str) -> str | None:
    for cn, code in ELEMENTS.items():
        if f"缺{cn}" in text or f"{cn}素" in text or f"{cn}肥" in text:
            return code
    return None


def guess_crops(text: str) -> list[str]:
    return [c for c in CROPS if c in text]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--merge", action="store_true", help="合并已校对候选到知识库")
    args = ap.parse_args()

    if args.merge:
        if not CAND.exists():
            raise SystemExit("没有 candidates.jsonl")
        exist = {json.loads(l)["id"] for l in KB.read_text(encoding="utf-8").splitlines() if l.strip()}
        added = 0
        with KB.open("a", encoding="utf-8") as f:
            for ln in CAND.read_text(encoding="utf-8").splitlines():
                if not ln.strip():
                    continue
                c = json.loads(ln)
                if c.get("needsReview", True) or c["id"] in exist:
                    continue
                f.write(json.dumps(c, ensure_ascii=False) + "\n")
                added += 1
        print(f"合并 {added} 条到 {KB}")
        return

    docs = load_clean()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    stats: dict[str, int] = {}

    with CAND.open("w", encoding="utf-8") as f:
        for i, d in enumerate(docs):
            for code in set(ELEMENTS.values()):
                hits = [s for s in d["sentences"] if code_hit(s, code)]
                if len(hits) < 2:
                    continue
                sympt = [s for s in hits if any(w in s for w in SYMPTOM_WORDS)]
                advice = [s for s in hits if any(w in s for w in ADVICE_WORDS)]
                if not sympt:
                    continue
                stats[code] = stats.get(code, 0) + 1
                f.write(json.dumps({
                    "id": f"cand_{code}_{i:03d}",
                    "element": code,
                    "elementName": cn_of(code),
                    "crops": guess_crops(d["text"]) or ["通用"],
                    "symptoms": sympt[:6],
                    "adviceHints": advice[:6],
                    "sourceUrl": d["url"],
                    "publisher": d.get("title", "")[:40],
                    "needsReview": True,
                }, ensure_ascii=False) + "\n")

    print("候选条目统计：")
    for k, v in sorted(stats.items()):
        print(f"  {cn_of(k)}({k})  {v} 条")
    print(f"\n共 {sum(stats.values())} 条候选 -> {CAND}")
    print("下一步：人工校对 candidates.jsonl，把需要的条目改成 needsReview=false 后 --merge")


def code_hit(sentence: str, code: str) -> bool:
    cn = cn_of(code)
    return f"缺{cn}" in sentence or f"{cn}素" in sentence or f"{cn}肥" in sentence


def cn_of(code: str) -> str:
    for cn, c in ELEMENTS.items():
        if c == code:
            return cn
    return code


if __name__ == "__main__":
    main()
