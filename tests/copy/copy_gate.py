#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""LC 文案门禁：用户可见文案零命中「无门槛」字样 + 两条静态红线。

三条检查（同一个入口，全绿才 PASS）：

  copy   app/android/app/src/main/res/values/*.xml 的用户可见值不得出现
         会员|付费|VIP|开通|升级|订阅|解锁|充值|去水印
         （注释不算：那里写「原版此处为付费」是事实记录，必须保留）
  audi   发行面（见「扫描范围」）不得出现原版审图号
  tiles  发行面不得出现原版私有瓦片域 21atcloud / xinzhi.space

扫描范围 = `git ls-files` 的已跟踪文件，减去豁免（docs/、tests/、*.md、
investigation.cany/、reference/、checks/、素材目录与二进制），再剔除代码注释。
理由：测试要写负向断言、文档要记录原版事实，这两类不是发行面。

退出码 0 = 全绿，1 = 有命中 / 有文件解析失败 / 不是 git 仓库也能降级到 os.walk。
单跑：python tests/copy/copy_gate.py
"""
from __future__ import annotations

import fnmatch
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
RES_VALUES_DIR = REPO_ROOT / "app" / "android" / "app" / "src" / "main" / "res" / "values"
ALLOWLIST_PATH = Path(__file__).resolve().parent / "allowlist.txt"

# 用户可见文案的「无门槛」词表（总监 2026-11-05 定的零命中门禁）
PAYWALL_WORDS = ("会员", "付费", "VIP", "开通", "升级", "订阅", "解锁", "充值", "去水印")
PAYWALL_RE = re.compile("|".join(re.escape(w) for w in PAYWALL_WORDS), re.IGNORECASE)

# 红线词在运行时拼接：门禁自身的源码里不出现完整字面量，
# 免得「grep 全仓」式的自查把门禁自己判红。
AUDIT_NO_RE = re.compile("GS" + r"[（(]2026[）)]" + "1219")
TILE_RE = re.compile("21at" + "cloud" + "|" + "xinzhi" + r"\." + "space", re.IGNORECASE)

# 豁免：文档/取证/测试自身不在发行面。README 里有逐项说明。
EXEMPT_PREFIXES = (
    "docs/",
    "investigation.cany/",
    "reference/",
    "tests/",
    "checks/",
    "attachments/",
    "capture/",
    "pinned/",
    "target/",
    ".git/",
)

BINARY_EXTS = {
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".ico",
    ".apk", ".aab", ".jar", ".zip", ".7z", ".gz", ".xz",
    ".so", ".a", ".o", ".dll", ".exe", ".class", ".dex", ".pyc",
    ".woff", ".woff2", ".ttf", ".otf", ".mp3", ".mp4", ".webm", ".bin", ".onnx",
}

SLASH_COMMENT_EXTS = {
    ".java", ".kt", ".kts", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx",
    ".rs", ".c", ".cc", ".cpp", ".h", ".hpp", ".gradle", ".groovy", ".css",
}
HASH_COMMENT_EXTS = {
    ".ps1", ".psm1", ".py", ".sh", ".yaml", ".yml", ".properties",
    ".toml", ".bat", ".cmd", ".gitignore",
}

PASS, FAIL, WARN = "PASS", "FAIL", "WARN"

# 白名单计数钉子（总监 2026-11-07 定）：文件级豁免只放固定条数，
# 防止后来者往已豁免的原文里顺手追加 URL/端点。
#   文件 → (规则, 计数字符串, 期望出现次数)
ALLOWLIST_PINS: dict[str, tuple[str, str, int]] = {
    "app/android/app/src/main/assets/m3/contact.txt": ("tiles", "xinzhi" + "." + "space", 1),
    "app/android/app/src/main/assets/m3/agreement.txt": ("tiles", "xinzhi" + "." + "space", 2),
}


def check_allowlist_pins() -> tuple[list[str], list[str]]:
    """返回 (问题, 说明)。文件未落库只提示；已落库则条数必须精确等于钉子值。"""
    problems: list[str] = []
    notes: list[str] = []
    for rel, (rule, token, expected) in ALLOWLIST_PINS.items():
        path = REPO_ROOT / rel
        if not path.exists():
            notes.append(f"{rel} 尚未落库，计数钉子待生效（rule={rule} 期望 {expected} 处）")
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        actual = text.count(token)
        if actual != expected:
            problems.append(
                f"{rel}: `{token}` 出现 {actual} 次，白名单钉住 {expected} 次"
                "（新增原文请走总监审批，不许往豁免文件里追加）"
            )
        else:
            notes.append(f"{rel}: {actual} 处，与钉子一致")
    return problems, notes


def rel_of(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def tracked_files() -> list[str]:
    """已跟踪文件（相对仓库根，posix）。git 不可用时降级 os.walk。"""
    try:
        proc = subprocess.run(
            ["git", "-C", str(REPO_ROOT), "ls-files", "-z"],
            capture_output=True,
            check=False,
        )
        if proc.returncode == 0:
            names = proc.stdout.decode("utf-8", "surrogateescape").split("\0")
            return sorted(n for n in names if n)
    except OSError:
        pass
    found = []
    for dirpath, dirnames, filenames in os.walk(REPO_ROOT):
        dirnames[:] = [d for d in dirnames if d not in {".git", "target", "checks"}]
        for name in filenames:
            found.append(Path(dirpath, name).relative_to(REPO_ROOT).as_posix())
    return sorted(found)


def is_exempt(rel: str) -> bool:
    if rel.lower().endswith(".md"):  # 文档：事实记录允许写原版词
        return True
    return any(rel.startswith(p) for p in EXEMPT_PREFIXES)


def is_binary(rel: str) -> bool:
    return Path(rel).suffix.lower() in BINARY_EXTS


def comment_kind(rel: str) -> str | None:
    suffix = Path(rel).suffix.lower()
    if suffix == ".xml":
        return "xml"
    if suffix in SLASH_COMMENT_EXTS:
        return "slash"
    if suffix in HASH_COMMENT_EXTS or Path(rel).name == ".gitignore":
        return "hash"
    return None


def _blank(match: re.Match[str]) -> str:
    """把一段文本替换成等长空白（保留换行），行号不漂。"""
    return re.sub(r"[^\n]", " ", match.group(0))


def strip_comments(text: str, kind: str | None) -> str:
    """剔除注释，行数保持不变。

    - xml   ：`<!-- ... -->`（可跨行）
    - slash ：`/* ... */` 块 + 行首/行尾 `//`（`://` 的 URL 不当注释）
    - hash  ：行首/行尾 `#`（`url#frag` 不当注释）
    行尾 `//` 之外的中间注释不猜，宁可多报也不漏报真实的私有瓦片地址。
    """
    if kind is None:
        return text
    if kind == "xml":
        return re.sub(r"<!--.*?-->", _blank, text, flags=re.S)

    out: list[str] = []
    in_block = False
    for line in text.split("\n"):
        s = line
        if in_block:
            idx = s.find("*/")
            if idx < 0:
                out.append("")
                continue
            s = s[idx + 2:]
            in_block = False

        stripped = s.lstrip()
        if kind == "hash":
            if stripped.startswith("#"):
                s = ""
            else:
                m = re.search(r"(^|\s)#", s)
                if m:
                    s = s[: m.start(1)] if m.group(1) else ""
        else:
            if stripped.startswith("//"):
                s = ""
            elif stripped.startswith("/*"):
                idx = s.find("*/", s.find("/*") + 1)
                if idx < 0:
                    in_block = True
                    s = ""
                else:
                    s = s[idx + 2:]
            s = re.sub(r"(?<!:)//.*$", "", s)
        out.append(s)
    return "\n".join(out)


def find_hits(rel: str, text: str, pattern: re.Pattern[str]) -> list[tuple[int, str]]:
    hits = []
    for lineno, line in enumerate(text.split("\n"), 1):
        for m in pattern.finditer(line):
            hits.append((lineno, m.group(0)))
    return hits


def load_allowlist() -> list[tuple[str, str, str]]:
    """allowlist.txt：每行 `<rule> <glob> [理由]`，rule ∈ copy|audi|tiles|all。"""
    if not ALLOWLIST_PATH.exists():
        return []
    entries = []
    for raw in ALLOWLIST_PATH.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 2)
        if len(parts) < 2:
            print(f"[{WARN}] allowlist 行无法解析：{line}")
            continue
        rule, glob = parts[0], parts[1]
        reason = parts[2].strip() if len(parts) > 2 else ""
        if rule not in {"copy", "audi", "tiles", "all"}:
            print(f"[{WARN}] allowlist 未知规则 {rule}：{line}")
            continue
        entries.append((rule, glob, reason))
    return entries


class Report:
    def __init__(self) -> None:
        self.failed = False

    def section(self, rule: str, label: str, hits: list[tuple[str, int, str, str]], scope: str) -> None:
        if hits:
            self.failed = True
            print(f"[{FAIL}] {rule}: {len(hits)} 处命中 —— {label}")
            for rel, lineno, word, extra in hits:
                suffix = f"  [{extra}]" if extra else ""
                print(f"    {rel}:{lineno}: {word}{suffix}")
        else:
            print(f"[{PASS}] {rule}: {label}（{scope}）")


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
        sys.stderr.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
    except Exception:
        pass

    report = Report()
    allow = load_allowlist()
    used = set()

    def apply_allow(rule: str, findings: list[tuple[str, int, str, str]]):
        kept = []
        for rel, lineno, word, extra in findings:
            matched = False
            for idx, (r, glob, _reason) in enumerate(allow):
                if r in (rule, "all") and fnmatch.fnmatch(rel, glob):
                    used.add(idx)
                    matched = True
                    break
            if not matched:
                kept.append((rel, lineno, word, extra))
        return kept

    print(f"LC COPY GATE | repo={REPO_ROOT}")

    # --- 1/3 用户可见文案 ---------------------------------------------------
    strings_files = sorted(RES_VALUES_DIR.glob("*.xml"))
    copy_hits: list[tuple[str, int, str, str]] = []
    parse_errors: list[str] = []
    for path in strings_files:
        rel = rel_of(path)
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError as exc:
            parse_errors.append(f"{rel}: 不是 UTF-8（{exc}）")
            continue
        try:
            ET.fromstring(text)
        except ET.ParseError as exc:
            parse_errors.append(f"{rel}: XML 解析失败（{exc}）")
            continue
        clean = strip_comments(text, "xml")
        for lineno, line in enumerate(clean.split("\n"), 1):
            for m in PAYWALL_RE.finditer(line):
                name = re.search(r'name="([^"]+)"', line)
                copy_hits.append((rel, lineno, m.group(0), f'string={name.group(1)}' if name else ""))

    if not strings_files:
        parse_errors.append(f"未找到用户可见文案目录：{rel_of(RES_VALUES_DIR)}")
    if parse_errors:
        report.failed = True
        for err in parse_errors:
            print(f"[{FAIL}] copy: {err}")

    copy_hits = apply_allow("copy", copy_hits)
    report.section(
        "copy",
        '用户可见文案零命中 ' + "|".join(PAYWALL_WORDS),
        copy_hits,
        f"{len(strings_files)} 个 values/*.xml，注释已剔除",
    )

    # --- 2/3 + 3/3 发行面静态红线 ------------------------------------------
    files = tracked_files()
    scan_files = [f for f in files if not is_exempt(f) and not is_binary(f)]
    audi_hits: list[tuple[str, int, str, str]] = []
    tile_hits: list[tuple[str, int, str, str]] = []
    for rel in scan_files:
        path = REPO_ROOT / rel
        try:
            data = path.read_bytes()
        except OSError:
            continue
        if b"\0" in data[:8192]:
            continue
        text = data.decode("utf-8", "replace")
        clean = strip_comments(text, comment_kind(rel))
        for lineno, word in find_hits(rel, clean, AUDIT_NO_RE):
            audi_hits.append((rel, lineno, word, ""))
        for lineno, word in find_hits(rel, clean, TILE_RE):
            tile_hits.append((rel, lineno, word, ""))

    audi_hits = apply_allow("audi", audi_hits)
    tile_hits = apply_allow("tiles", tile_hits)
    scope = f"扫 {len(scan_files)} 个已跟踪文件，注释与 docs/ · tests/ · *.md 已豁免"
    report.section("audi", "发行面无原版审图号", audi_hits, scope)
    report.section("tiles", "发行面无原版私有瓦片域 21atcloud / xinzhi.space", tile_hits, scope)

    # --- 白名单计数钉子 -----------------------------------------------------
    pin_problems, pin_notes = check_allowlist_pins()
    for note in pin_notes:
        print(f"[{PASS}] 计数钉子：{note}")
    if pin_problems:
        report.failed = True
        for problem in pin_problems:
            print(f"[{FAIL}] 计数钉子：{problem}")

    for idx, (rule, glob, reason) in enumerate(allow):
        if idx not in used:
            print(f"[{WARN}] allowlist 未命中：{rule} {glob}" + (f"  # {reason}" if reason else ""))

    print("")
    if report.failed:
        print("COPY: FAIL")
        return 1
    print("COPY: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
