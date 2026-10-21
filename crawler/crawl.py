"""
抓取器：按 seeds.txt 抓取公开农技科普页面，原始 HTML 落盘。离线作业，不参与后端启动。

合规约定（写死在代码里，不做可选开关）：
  1. 单站点串行，请求间隔 >= 1 秒
  2. 带明确 UA 标识，不伪装浏览器
  3. 只抓公开页面，不登录、不绕付费墙
  4. 每页保留来源 URL 与抓取时间，便于溯源

用法：
  python crawl.py                        # 抓 seeds.txt 全部种子
  python crawl.py --depth 1              # 顺带抓一层同域内链
  python crawl.py --url https://xxx      # 只抓单页，调试用
"""
import argparse
import hashlib
import json
import time
import urllib.parse
from pathlib import Path

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent.parent
RAW_DIR = ROOT / "data" / "raw"
SEEDS = Path(__file__).resolve().parent / "seeds.txt"

UA = "OHCP-CropAgent-Crawler/1.0 (classroom project; contact: student)"
HEADERS = {"User-Agent": UA, "Accept": "text/html,application/xhtml+xml"}
TIMEOUT = 10
DELAY = 1.2


def url_key(url: str) -> str:
    return hashlib.md5(url.encode("utf-8")).hexdigest()[:16]


def fetch(url: str) -> str | None:
    try:
        r = requests.get(url, headers=HEADERS, timeout=TIMEOUT, allow_redirects=True)
        if r.status_code != 200:
            print(f"  [skip] {r.status_code} {url}")
            return None
        r.encoding = r.apparent_encoding or r.encoding
        return r.text
    except Exception as e:
        print(f"  [err ] {type(e).__name__} {url}")
        return None


def same_domain_links(html: str, base: str, limit: int = 5) -> list[str]:
    host = urllib.parse.urlparse(base).netloc
    out, seen = [], set()
    soup = BeautifulSoup(html, "lxml")
    for a in soup.find_all("a", href=True):
        u = urllib.parse.urljoin(base, a["href"])
        p = urllib.parse.urlparse(u)
        if p.scheme not in ("http", "https") or p.netloc != host:
            continue
        u = u.split("#")[0]
        if u in seen or u == base:
            continue
        seen.add(u)
        out.append(u)
        if len(out) >= limit:
            break
    return out


def save(url: str, html: str) -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    key = url_key(url)
    (RAW_DIR / f"{key}.html").write_text(html, encoding="utf-8")
    (RAW_DIR / f"{key}.meta.json").write_text(
        json.dumps(
            {"url": url, "crawledAt": time.strftime("%Y-%m-%dT%H:%M:%S"), "bytes": len(html)},
            ensure_ascii=False, indent=2,
        ),
        encoding="utf-8",
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", help="只抓单页")
    ap.add_argument("--depth", type=int, default=0, help="同域内链层数，0 表示不跟")
    args = ap.parse_args()

    urls = [args.url] if args.url else [
        ln.strip() for ln in SEEDS.read_text(encoding="utf-8").splitlines()
        if ln.strip() and not ln.startswith("#")
    ]

    total = 0
    for u in urls:
        print(f"[fetch] {u}")
        html = fetch(u)
        if not html:
            continue
        save(u, html)
        total += 1
        time.sleep(DELAY)

        if args.depth > 0:
            for child in same_domain_links(html, u):
                print(f"  [child] {child}")
                h = fetch(child)
                if h:
                    save(child, h)
                    total += 1
                time.sleep(DELAY)

    print(f"\n完成：{total} 个页面 -> {RAW_DIR}")


if __name__ == "__main__":
    main()
