#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""密钥闸门：天地图 key 不许出现在 git 索引里（仓库是 PUBLIC）。

两条检查（同一个入口，全绿才 PASS）：

  key  运行时从仓库外的 secrets 文件（或环境变量）读出 tdt.server.key / tdt.web.key，
       对 `git ls-files` 的每个已跟踪文件做**字节级精确匹配**；命中即 FAIL。
       只打印 `文件:行: <哪个 key>`，**绝不打 key 值**。
  hex  启发式告警：`assets/*.html` 与 `*.java` 里出现 32 位十六进制串 → WARN
       （新 key 若被明文写进发页面/源码但拼法躲过精确匹配，这条能兜住；不是 FAIL）。

值从哪来（优先级同 build.gradle）：
  1. 环境变量 DSH_SECRET_TDT_SERVER_KEY / DSH_SECRET_TDT_WEB_KEY
  2. `--keys-file <路径>` > 环境变量 TDT_KEYS_FILE > 默认 E:\\APPDetector\\secrets\\tdt.properties

只读不写，不联网，不改仓库。白名单 tests/secrets/allowlist.txt（仅对 hex 告警生效，理由必填）。

退出码 0 = 绿（告警不算红），1 = 命中 / 读不到 key / git 不可用。
单跑：python tests\\secrets\\secret_gate.py
"""
from __future__ import annotations

import argparse
import fnmatch
import os
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ALLOWLIST_PATH = Path(__file__).resolve().parent / "allowlist.txt"
DEFAULT_KEYS_FILE = Path(r"E:\APPDetector\secrets\tdt.properties")

KEY_NAMES = (("tdt.server.key", "DSH_SECRET_TDT_SERVER_KEY"), ("tdt.web.key", "DSH_SECRET_TDT_WEB_KEY"))
HEX32_RE = re.compile(r"(?<![0-9a-fA-F])[0-9a-fA-F]{32}(?![0-9a-fA-F])")
BINARY_SNIFF_EXT = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".apk", ".jar", ".zip", ".so", ".dex"}

PASS, FAIL, WARN = "PASS", "FAIL", "WARN"


def rel_of(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def die(msg: str) -> int:
    print(f"[{FAIL}] secret: {msg}")
    print("")
    print("SECRET: FAIL")
    return 1


def read_properties(path: Path) -> dict[str, str]:
    """够用的 .properties 解析：k=v / k:v，跳过 # ! 注释，处理 \\ 续行与常见转义。"""
    text = path.read_text(encoding="utf-8", errors="replace")
    text = text.replace("\\\r\n", "").replace("\\\n", "")
    props: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line[0] in "#!":
            continue
        sep = "=" if "=" in line else (":" if ":" in line else None)
        if sep is None:
            continue
        name, value = line.split(sep, 1)
        props[name.strip()] = value.strip().replace("\\:", ":").replace("\\=", "=")
    return props


def resolve_keys(keys_file: Path) -> tuple[dict[str, str], str, list[str]]:
    """返回 (keys, 来源描述, 缺失项)。任一 key 为空即视为缺失。"""
    keys: dict[str, str] = {}
    source_bits: list[str] = []
    missing: list[str] = []

    for name, env in KEY_NAMES:
        value = (os.environ.get(env) or "").strip()
        if value:
            keys[name] = value
            source_bits.append(f"{name}←${env}")
        else:
            keys[name] = ""

    need_file = [n for n, _ in KEY_NAMES if not keys[n]]
    if need_file:
        if not keys_file.is_file():
            return keys, f"文件 {keys_file}", need_file
        props = read_properties(keys_file)
        for name in need_file:
            value = (props.get(name) or "").strip()
            keys[name] = value
            if value:
                source_bits.append(f"{name}←{keys_file}")
        source_bits.append(f"文件={keys_file}")

    for name, _ in KEY_NAMES:
        if not keys[name]:
            missing.append(name)
    return keys, "，".join(dict.fromkeys(source_bits)) or f"文件 {keys_file}", missing


def git_files() -> list[str] | None:
    try:
        proc = subprocess.run(
            ["git", "-C", str(REPO_ROOT), "ls-files", "-z"],
            capture_output=True,
            check=False,
        )
    except OSError:
        return None
    if proc.returncode != 0:
        return None
    names = proc.stdout.decode("utf-8", "surrogateescape").split("\0")
    return sorted(n for n in names if n)


def load_allowlist() -> list[tuple[str, str]]:
    if not ALLOWLIST_PATH.exists():
        return []
    entries: list[tuple[str, str]] = []
    for raw in ALLOWLIST_PATH.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if len(parts) < 2 or not parts[1].strip():
            print(f"[{WARN}] allowlist 行缺理由，已忽略：{line}")
            continue
        entries.append((parts[0], parts[1].strip()))
    return entries


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
        sys.stderr.reconfigure(encoding="utf-8")  # type: ignore[attr-defined]
    except Exception:
        pass

    parser = argparse.ArgumentParser(description="天地图 key 泄露闸门")
    parser.add_argument("--keys-file", default=None, help="tdt.properties 路径（默认 E:\\APPDetector\\secrets\\tdt.properties）")
    parser.add_argument("--allow-missing", action="store_true",
                        help="拿不到 key 时跳过精确匹配、只跑 hex 启发式（仅用于 fork PR 等确实没有 secrets 的 CI）")
    args = parser.parse_args()

    keys_file = Path(args.keys_file) if args.keys_file else Path(os.environ.get("TDT_KEYS_FILE") or DEFAULT_KEYS_FILE)

    print(f"LC SECRET GATE | repo={REPO_ROOT}")

    keys, source, missing = resolve_keys(keys_file)
    exact = True
    if missing:
        if not args.allow_missing:
            return die(f"读不到 {'、'.join(missing)}（来源：{source}）。无法确认真实 key 是否泄露，按红处理；"
                       f"构建机上准备 secrets 文件或设 DSH_SECRET_* 环境变量。见 docs/BUILD-SECRETS.md")
        exact = False
        print(f"[{WARN}] key: 读不到 {'、'.join(missing)}（来源：{source}），跳过精确匹配，只跑 hex 启发式")

    files = git_files()
    if files is None:
        return die("git ls-files 失败（不是 git 仓库或 git 不可用），无法确定扫描范围")

    needles = [(name, keys[name].encode("utf-8")) for name, _ in KEY_NAMES] if exact else []
    key_hits: list[tuple[str, int, str]] = []
    hex_hits: list[tuple[str, int, str]] = []
    allow = load_allowlist()
    used: set[int] = set()

    for rel in files:
        path = REPO_ROOT / rel
        try:
            data = path.read_bytes()
        except OSError:
            continue
        for name, needle in needles:
            idx = data.find(needle)
            if idx < 0:
                continue
            line = data.count(b"\n", 0, idx) + 1
            occurrences = data.count(needle)
            note = name if occurrences == 1 else f"{name}×{occurrences}"
            key_hits.append((rel, line, note))

        if rel.endswith(".java") or ("/assets/" in rel and rel.endswith(".html")):
            if path.suffix.lower() in BINARY_SNIFF_EXT:
                continue
            text = data.decode("utf-8", "replace")
            for lineno, line in enumerate(text.split("\n"), 1):
                for m in HEX32_RE.finditer(line):
                    suppressed = False
                    for idx_a, (glob, _reason) in enumerate(allow):
                        if fnmatch.fnmatch(rel, glob):
                            used.add(idx_a)
                            suppressed = True
                            break
                    if not suppressed:
                        token = m.group(0)
                        hex_hits.append((rel, lineno, token[:4] + "…" + f"（{len(token)} 位十六进制）"))

    if key_hits:
        print(f"[{FAIL}] key: 天地图 key 出现在已跟踪文件里（{len(key_hits)} 处）——public 仓库，必须删除并轮换 key")
        for rel, line, note in key_hits:
            print(f"    {rel}:{line}: {note}")
    else:
        if exact:
            print(f"[PASS] key: {len(needles)} 个 key × {len(files)} 个已跟踪文件，零命中（{source}）")
        else:
            print(f"[{WARN}] key: 精确匹配已跳过（无 key 可用），{len(files)} 个文件未做 key 比对")

    if hex_hits:
        print(f"[{WARN}] hex: {len(hex_hits)} 处 32 位十六进制串落在 assets/*.html 或 *.java（疑似 key，需人判）")
        for rel, line, masked in hex_hits:
            print(f"    {rel}:{line}: {masked}")
    else:
        print(f"[PASS] hex: assets/*.html 与 *.java 无 32 位十六进制串")

    for idx_a, (glob, reason) in enumerate(allow):
        if idx_a not in used:
            print(f"[{WARN}] allowlist 未命中：{glob}  # {reason}")

    print("")
    if key_hits:
        print("SECRET: FAIL")
        return 1
    print("SECRET: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
