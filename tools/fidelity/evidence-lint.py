#!/usr/bin/env python3
"""evidence-lint — 证据文件「内容有效性」检查（M6 Stage 4 复验用）。

背景：`assets.py verify --strict` 只校验文件存在与 sha256，查不出「文件在、内容垃圾」。
实例：`docs/fidelity/m3/help-offline.xml`（47 B）内容是 shell 报错
`cat: /sdcard/a.xml: No such file or directory`，不是 dump，却一直被当证据引用。

本脚本对 .xml / .png 做结构与内容检查，不看 sha：

  .xml
    - 0 字节 → fail
    - 无法 parse → fail
    - 首行/全文命中 shell 报错模式（cat:/adb:/No such file/error:/not found 等）→ fail
    - 根节点不是 uiautomator 的 `hierarchy` 且不是已知 dump 结构 → warn（可能是别的 xml）
  .png
    - 0 字节 → fail
    - 无法解码 → fail
    - 尺寸 ≤ 0 → fail
    - 近似单色（抽样后主色占比 ≥ monoc_ratio，默认 0.995）→ fail（空屏/纯色）
    - 全黑 / 全白 / 全透明 → fail（与单色重叠，单独报原因）

用法：
  python tools/fidelity/evidence-lint.py --root docs/fidelity
  python tools/fidelity/evidence-lint.py --root docs/fidelity/m3 --strict --json out.json

  生成物溯源（MD 为唯一源，HTML 是生成物）：
  python tools/fidelity/evidence-lint.py --generated-html app/android/app/src/main/assets/lc-help/index.html \
         --source docs/product/help-center.md

退出码：0 = 无 fail；1 = 有 fail（--strict 时 warn 也算）；2 = 参数/路径错误。
"""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

GEN_HEADER_RE = re.compile(r"GENERATED from\s+(\S+)\s+@\s+([0-9a-fA-F]{7,40})")


def check_eol(path: str, strict_crlf: bool) -> list[dict]:
    """源码换行符检查（技术债示例：MainActivity 4083 CRLF + 203 裸 LF 混用 → 编辑工具重排产生 203 行假 diff）。

    规则：
      - **混用（既有 CRLF 又有裸 LF）→ FAIL**：这正是假 diff 的直接来源；
      - **纯 CRLF → WARN**，`strict_crlf=True` 时也判 FAIL（本仓约定源码 LF）；
      - 裸 CR → FAIL。
    只检查显式传入的文件；**不做全仓判死**（存量债：全仓 296/699 文件含 CR，需单独立项规范化）。
    """
    out: list[dict] = []
    try:
        with open(path, "rb") as fh:
            data = fh.read()
    except OSError as exc:
        return [{"level": "fail", "kind": "eol", "reason": f"unreadable: {exc}", "path": path}]

    crlf = data.count(b"\r\n")
    bare_lf = data.count(b"\n") - crlf
    bare_cr = data.count(b"\r") - crlf

    if crlf == 0 and bare_cr == 0:
        return out  # 纯 LF，合规
    if crlf > 0 and bare_lf > 0:
        out.append(
            {
                "level": "fail",
                "kind": "eol",
                "reason": f"mixed EOL: {crlf} CRLF + {bare_lf} bare LF (fake-diff risk)",
                "path": path,
                "crlf": crlf,
                "bare_lf": bare_lf,
            }
        )
    elif crlf > 0:
        out.append(
            {
                "level": "fail" if strict_crlf else "warn",
                "kind": "eol",
                "reason": f"CRLF-only ({crlf} CRLF, 0 bare LF): repo convention is LF",
                "path": path,
                "crlf": crlf,
            }
        )
    else:
        out.append(
            {"level": "fail", "kind": "eol", "reason": f"bare CR x{bare_cr}", "path": path}
        )
    return out


MAGIC = {b"\x89PNG": "png", b"RIFF": "webp(RIFF)"}


def check_magic(path: str) -> list[dict]:
    """扩展名与真实格式一致性：`.png` 必须是 PNG、`.webp` 必须是 RIFF/WebP。

    历史坑：仓里 19 个 `.png` 实为 WebP（magic RIFF），以及 22 张损坏图（0x89 被替换成 EF BF BD）。
    只检查显式传入的文件（T-04：不做全仓判死）。
    """
    ext = os.path.splitext(path)[1].lower()
    want = {".png": "png", ".webp": "webp(RIFF)"}.get(ext)
    if want is None:
        return []
    try:
        with open(path, "rb") as fh:
            head = fh.read(4)
    except OSError as exc:
        return [{"level": "fail", "kind": "magic", "reason": f"unreadable: {exc}", "path": path}]
    got = "png" if head[:4] == b"\x89PNG" else ("webp(RIFF)" if head[:4] == b"RIFF" else f"other({head[:4].hex()})")
    if got != want:
        return [
            {
                "level": "fail",
                "kind": "magic",
                "reason": f"extension {ext} but content is {got} (magic/extension mismatch)",
                "path": path,
            }
        ]
    return []


def check_generated(html_path: str, source_path: str) -> list[dict]:
    """HTML 是生成物：头部 `GENERATED from <md> @ <blob sha>` 必须与 md 当前 blob sha 一致。

    一致 = 生成物没被手改、也没落后于源；不一致或缺失 = FAIL。
    源文件 blob sha 用 `git hash-object` 现算（与 git 内部一致），不依赖提交历史。
    """
    out: list[dict] = []
    if not os.path.isfile(html_path):
        return [
            {
                "level": "fail",
                "kind": "generated",
                "reason": f"generated file not found: {html_path}",
                "path": html_path,
            }
        ]
    if not os.path.isfile(source_path):
        return [
            {
                "level": "fail",
                "kind": "generated",
                "reason": f"source file not found: {source_path}",
                "path": source_path,
            }
        ]
    try:
        with open(html_path, "rb") as fh:
            head = fh.read(8192).decode("utf-8", errors="replace")
    except OSError as exc:
        return [
            {
                "level": "fail",
                "kind": "generated",
                "reason": f"unreadable: {exc}",
                "path": html_path,
            }
        ]

    m = GEN_HEADER_RE.search(head)
    if not m:
        out.append(
            {
                "level": "fail",
                "kind": "generated",
                "reason": "missing header 'GENERATED from <src> @ <blob sha>' (hand-written or generator not wired)",
                "path": html_path,
            }
        )
        return out

    declared_path, declared_sha = m.group(1), m.group(2).lower()
    if os.path.normcase(os.path.basename(declared_path)) != os.path.normcase(os.path.basename(source_path)):
        out.append(
            {
                "level": "fail",
                "kind": "generated",
                "reason": f"header source {declared_path!r} != expected {source_path!r}",
                "path": html_path,
            }
        )
        return out

    try:
        actual = subprocess.run(
            ["git", "hash-object", source_path],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip().lower()
    except Exception as exc:  # noqa: BLE001
        return [
            {
                "level": "fail",
                "kind": "generated",
                "reason": f"cannot compute blob sha for {source_path}: {type(exc).__name__}",
                "path": source_path,
            }
        ]

    if not actual.startswith(declared_sha):
        out.append(
            {
                "level": "fail",
                "kind": "generated",
                "reason": f"stale/mismatched: header {declared_sha[:12]}… != source blob {actual[:12]}… (regenerate)",
                "path": html_path,
                "header_sha": declared_sha,
                "source_blob": actual,
            }
        )
    return out

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))  # tools/fidelity -> repo root


def resolve(path: str) -> str:
    """相对路径一律以 REPO_ROOT 为基准；绝对路径原样返回。

    门禁工具会被复制到别处、被别的脚本调用、在别的工作树里跑——依赖 cwd 的工具，
    换个人跑就是另一个结果，而这种差异最容易被误读成「别人改坏了」。
    """
    if not path:
        return path
    if os.path.isabs(path):
        return path
    return os.path.normpath(os.path.join(REPO_ROOT, path))


def resolve_existing(path: str) -> str | None:
    """先按原样（cwd 相对）、再按 REPO_ROOT 解析，返回第一个存在的路径。"""
    for cand in (path, resolve(path)):
        if os.path.exists(cand):
            return cand
    return None


SHELL_ERROR_PATTERNS = [
    r"^cat:\s",
    r"^adb:\s",
    r"No such file or directory",
    r"^error:",
    r"^ERROR:",
    r"not found$",
    r"Permission denied",
    r"^uiautomator:",
    r"^cannot ",
    r"^failed to ",
]

PNG_EXTS = {".png"}
IMG_EXTS = {".png", ".webp"}
XML_EXTS = {".xml"}


def _first_text(path: str, limit: int = 4096) -> str:
    try:
        with open(path, "rb") as fh:
            raw = fh.read(limit)
        return raw.decode("utf-8", errors="replace")
    except OSError as exc:  # pragma: no cover
        return f"<unreadable: {exc}>"


def check_xml(path: str, strict_root: bool) -> list[dict]:
    out: list[dict] = []
    size = os.path.getsize(path)
    if size == 0:
        return [{"level": "fail", "kind": "xml", "reason": "zero-byte", "path": path}]

    text = _first_text(path)
    for pat in SHELL_ERROR_PATTERNS:
        m = re.search(pat, text, flags=re.MULTILINE)
        if m:
            snippet = text.strip().splitlines()[0][:120] if text.strip() else ""
            out.append(
                {
                    "level": "fail",
                    "kind": "xml",
                    "reason": f"shell-error / non-dump content (/{pat}/)",
                    "path": path,
                    "size": size,
                    "first_line": snippet,
                }
            )
            return out

    try:
        with open(path, "rb") as fh:
            full = fh.read(32 * 1024 * 1024)
        root = ET.fromstring(full.decode("utf-8", errors="replace"))
    except ET.ParseError as exc:
        return [
            {
                "level": "fail",
                "kind": "xml",
                "reason": f"unparseable: {exc}",
                "path": path,
                "size": size,
            }
        ]

    tag = root.tag.split("}")[-1]
    if tag == "hierarchy":
        # 合法但为空的 dump：<hierarchy/>，或没有第二个 <node>
        if len(root.findall(".//node")) == 0:
            out.append(
                {
                    "level": "fail",
                    "kind": "xml",
                    "reason": "empty dump (0 <node> elements)",
                    "path": path,
                    "size": size,
                }
            )
            return out
    elif strict_root:
        out.append(
            {
                "level": "warn",
                "kind": "xml",
                "reason": f"root tag is <{tag}>, not <hierarchy> (not a uiautomator dump?)",
                "path": path,
                "size": size,
            }
        )
    return out


def check_png(path: str, mono_ratio: float, min_side: int, expect_size: tuple[int, int] | None) -> list[dict]:
    from PIL import Image  # local import: only needed for png

    size = os.path.getsize(path)
    if size == 0:
        return [{"level": "fail", "kind": "png", "reason": "zero-byte", "path": path}]
    try:
        with Image.open(path) as im:
            im.load()
            w, h = im.size
            if w <= 0 or h <= 0:
                return [
                    {"level": "fail", "kind": "png", "reason": f"bad size {w}x{h}", "path": path}
                ]
            # 声明尺寸：逐图表（--expect-size-map）优先，其次全局（--expect-size）。
            # 2026-11-09 前这个参数只解析不比较（空转），此处补实；不符判 fail。
            # 注意必须在 min_side 早退之前判，否则 201px 宽的裁切图根本进不来。
            if expect_size is not None and (w, h) != tuple(expect_size):
                return [
                    {
                        "level": "fail",
                        "kind": "png",
                        "reason": f"declared {expect_size[0]}x{expect_size[1]}, actual {w}x{h}",
                        "path": path,
                        "wh": f"{w}x{h}",
                    }
                ]
            if min(w, h) < min_side:
                # 素材图标/小图不按「证据截图」判纯色
                return []
            rgb = im.convert("RGBA")
            # sample up to 4000 pixels on a grid to stay fast on 1228x2700
            step_x = max(1, w // 64)
            step_y = max(1, h // 64)
            samples = []
            for y in range(0, h, step_y):
                for x in range(0, w, step_x):
                    samples.append(rgb.getpixel((x, y)))
            total = len(samples) or 1
            counts: dict[tuple[int, int, int, int], int] = {}
            for px in samples:
                counts[px] = counts.get(px, 0) + 1
            top_px, top_n = max(counts.items(), key=lambda kv: kv[1])
            ratio = top_n / total
            opaque = [s for s in samples if s[3] > 8]
            if not opaque:
                return [
                    {"level": "fail", "kind": "png", "reason": "fully transparent", "path": path}
                ]
            if ratio >= mono_ratio:
                r, g, b, a = top_px
                if a <= 8:
                    reason = "effectively blank (transparent-dominant)"
                elif (r, g, b) == (0, 0, 0):
                    reason = "effectively black (blank screen)"
                elif (r, g, b) == (255, 255, 255):
                    reason = "effectively white (blank screen)"
                else:
                    reason = f"monochrome {ratio:.4f} rgb=({r},{g},{b})"
                return [
                    {
                        "level": "fail",
                        "kind": "png",
                        "reason": reason,
                        "path": path,
                        "size": size,
                        "wh": f"{w}x{h}",
                    }
                ]
    except Exception as exc:  # noqa: BLE001 - any decode error is a fail
        return [
            {
                "level": "fail",
                "kind": "png",
                "reason": f"decode failed: {type(exc).__name__}: {exc}",
                "path": path,
            }
        ]
    return []


def walk(root: str, skip_dirs: set[str]):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in skip_dirs]
        for name in filenames:
            yield os.path.join(dirpath, name)


def _sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def check_duplicate_images(root: str, skip_dirs: set[str]) -> tuple[list[dict], dict]:
    """同一目录内字节完全相同的图片 → fail（只比同目录；跨目录的合法复用不计）。

    背景（2026-11-09）：`lc-help/fig-32.webp` 与 `fig-08.webp` 字节相同——图注写「点一下
    『点击查看 ›』就地展开」，画面其实是点击前的未展开态；另有 7 张整屏被当成局部图注
    （fig-07/10/11/12/14/17）。尺寸 1228x2700 全对、可解码、引用 29=29，现有四条检查
    一条都报不出来，只有按内容哈希查重才抓得到。判 fail 而不是 warn：发现它的事实本身
    就说明这类重复会冒充别的证据。
    """
    by_dir: dict[str, dict[str, list[str]]] = {}
    for path in walk(root, skip_dirs):
        if os.path.splitext(path)[1].lower() not in IMG_EXTS:
            continue
        try:
            digest = _sha256_file(path)
        except OSError:
            continue
        by_dir.setdefault(os.path.dirname(os.path.abspath(path)), {}).setdefault(digest, []).append(path)

    findings: list[dict] = []
    stats = {"files": 0, "unique": 0, "duplicated": 0, "redundant": 0}
    for directory in sorted(by_dir):
        groups = by_dir[directory]
        stats["files"] += sum(len(v) for v in groups.values())
        stats["unique"] += len(groups)
        for digest, paths in sorted(groups.items()):
            if len(paths) < 2:
                continue
            ordered = sorted(paths)
            stats["duplicated"] += len(ordered)
            stats["redundant"] += len(ordered) - 1
            for dup in ordered[1:]:
                findings.append(
                    {
                        "level": "fail",
                        "kind": "dup",
                        "path": dup,
                        "kept": ordered[0],
                        "group": [os.path.basename(p) for p in ordered],
                        "reason": (
                            f"identical bytes to {os.path.basename(ordered[0])} "
                            f"(sha256 {digest[:12]}), group={len(ordered)}"
                        ),
                    }
                )
    return findings, stats


def expected_size_for(
    path: str,
    default: tuple[int, int] | None,
    rules: list[tuple[str, tuple[int, int]]],
    hits: dict[str, list[str]] | None = None,
    default_hits: list[str] | None = None,
) -> tuple[int, int] | None:
    """逐图声明尺寸：先按「仓库相对路径」匹配，再按 basename 匹配，都没命中才用全局默认。

    为什么要有逐图表：`lc-help` 的 6 张局部图裁切后尺寸各不相同（1228x68 / 201x1111 …），
    拿一个 `--expect-size 1228x2700` 套全目录，裁切一落地就全红；而没有逐图表，
    真正该红的那张（尺寸与图注不符）又查不出来。

    `hits` 记录每条规则实际命中的文件（键=pattern）。这不是统计装饰：规则指向一个
    不存在的文件时，旧实现既不比较也不报告——「先写表、后补图」于是得到一张
    「已声明但其实没验过」的绿（2026-11-10 LC审成文实测）。命中数是要拿来报的。
    """
    rel = os.path.relpath(os.path.abspath(path), REPO_ROOT).replace("\\", "/")
    base = os.path.basename(path)
    for pattern, size in rules:
        if fnmatch.fnmatchcase(rel, pattern) or fnmatch.fnmatchcase(base, pattern):
            if hits is not None:
                hits.setdefault(pattern, []).append(base)
            return size
    if default_hits is not None:
        default_hits.append(base)
    return default


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Content validity lint for fidelity evidence (.xml/.png)")
    ap.add_argument("--root", default=None, help="evidence root (default: <repo>/docs/fidelity, resolved against the script location; SKIPPED when only --generated-html is given)")
    ap.add_argument("--strict", action="store_true", help="treat warnings as failures")
    ap.add_argument("--mono-ratio", type=float, default=0.995, help="monochrome threshold (default 0.995)")
    ap.add_argument("--min-png-side", type=int, default=600, help="only lint png with min(w,h) >= this (default 600)")
    ap.add_argument("--skip-dirs", default="assets-vectors,assets,findings,probes,.git", help="comma-separated dir names to skip")
    ap.add_argument("--strict-root", action="store_true", help="warn when xml root is not <hierarchy>")
    ap.add_argument("--expect-size", help="declared screenshot size WxH for images not covered by the map, e.g. 1228x2700 (mismatch = fail)")
    ap.add_argument("--expect-size-map", help="JSON {pattern: WxH} per-image declared sizes; pattern matches repo-relative path or basename (glob)")
    ap.add_argument("--allow", help="allowlist JSON with entries {path, reason, by, verdict} (4 keys required)")
    ap.add_argument("--json", help="write JSON report to this path")
    ap.add_argument("--magic", help="comma-separated image files to check magic/extension consistency")
    ap.add_argument("--eol", nargs="?", const="", help="comma-separated source files to check for CRLF/mixed EOL; REQUIRES an explicit file list (bare --eol prints SKIP)")
    ap.add_argument("--eol-strict", action="store_true", help="also FAIL on CRLF-only files (repo convention is LF)")
    ap.add_argument("--generated-html", help="generated artifact (e.g. lc-help/index.html) to verify against its source")
    ap.add_argument("--source", help="source markdown for --generated-html (e.g. docs/product/help-center.md)")
    args = ap.parse_args(argv)

    root_explicit = args.root is not None
    if not root_explicit:
        args.root = "docs/fidelity"
    args.root = resolve(args.root)
    if not os.path.isdir(args.root):
        print(f"ERROR: not a directory: {args.root}", file=sys.stderr)
        return 2

    # 只跑生成物溯源时不要顺手扫整个证据库：
    # 不传 --root 就跑 --generated-html，会连 docs/fidelity 一起判，把“生成物没问题”报成“全库 fail”，
    # 两种跑法混在一起最容易发生「你那个 PASS 是限定了 root 的」这类争议（2026-11-09 客户端遇到）。
    scan_evidence = root_explicit or not (args.generated_html or args.source)
    if not scan_evidence:
        print("ROOT: SKIP evidence scan (only --generated-html/--source given; pass --root to lint evidence too)")

    expect = None
    if args.expect_size:
        try:
            w_s, h_s = args.expect_size.lower().split("x")
            expect = (int(w_s), int(h_s))
        except ValueError:
            print(f"ERROR: bad --expect-size {args.expect_size!r}", file=sys.stderr)
            return 2

    size_rules: list[tuple[str, tuple[int, int]]] = []
    if args.expect_size_map:
        map_path = resolve(args.expect_size_map)
        if not os.path.isfile(map_path):
            print(f"ERROR: size map not found: {map_path}", file=sys.stderr)
            return 2
        with open(map_path, encoding="utf-8") as fh:
            raw_map = json.load(fh)
        for pattern, spec in raw_map.items():
            if pattern.startswith("_"):
                continue  # 说明/备注键，不参与匹配
            try:
                w_s, h_s = str(spec).lower().split("x")
                size_rules.append((pattern, (int(w_s), int(h_s))))
            except ValueError:
                print(f"ERROR: bad size in map: {pattern!r} -> {spec!r}", file=sys.stderr)
                return 2
        print(f"SIZE-MAP: {len(size_rules)} rule(s) from {map_path}")

    allow: list[dict] = []
    if not args.allow:
        default_allow = os.path.join(SCRIPT_DIR, "evidence-lint-allow.json")
        if os.path.isfile(default_allow):
            args.allow = default_allow
    if args.allow:
        args.allow = resolve(args.allow)
        if not os.path.isfile(args.allow):
            print(f"ERROR: allowlist not found: {args.allow}", file=sys.stderr)
            return 2
        with open(args.allow, encoding="utf-8") as fh:
            doc = json.load(fh)
        allow = doc.get("allow", []) if isinstance(doc, dict) else doc
        for idx, entry in enumerate(allow):
            missing = [k for k in ("path", "reason", "by", "verdict") if not entry.get(k)]
            if missing:
                print(f"ERROR: allowlist entry #{idx + 1} missing {missing}", file=sys.stderr)
                return 2
        print(f"ALLOWLIST: {len(allow)} entry(ies) from {args.allow}")

    findings: list[dict] = []
    counts = {"xml": 0, "png": 0, "other": 0}
    skip = {s.strip() for s in args.skip_dirs.split(",") if s.strip()}
    rule_hits: dict[str, list[str]] = {p: [] for p, _ in size_rules}
    default_hits: list[str] = []
    for path in (walk(args.root, skip) if scan_evidence else []):
        ext = os.path.splitext(path)[1].lower()
        try:
            if ext in XML_EXTS:
                counts["xml"] += 1
                findings.extend(check_xml(path, args.strict_root))
            elif ext in IMG_EXTS:
                counts["png"] += 1
                findings.extend(
                    check_png(
                        path,
                        args.mono_ratio,
                        args.min_png_side,
                        expected_size_for(path, expect, size_rules, rule_hits, default_hits),
                    )
                )
            else:
                counts["other"] += 1
        except FileNotFoundError:
            continue

    # 空转条目必须报出来：规则一条都没命中时，旧实现「既不比也不报」，
    # 于是凭一张表就能宣称「逐图验过」。命中 0 文件 = 覆盖洞，判 FAIL。
    if scan_evidence and not size_rules and expect is None:
        # 参数没给就跳过检查时，必须留一行 SKIP（2026-11-09 门禁工具通则）：
        # 静默 exit 0 会被读成「尺寸全对」。这里不判 FAIL，但绝不能不说话。
        print("SIZE: SKIP (no --expect-size and no --expect-size-map: images are NOT size-checked)")
    if scan_evidence and size_rules:
        for pattern, size in size_rules:
            got = rule_hits.get(pattern) or []
            if got:
                print("SIZE-MAP-HIT: {} -> {}x{} <- {}".format(pattern, size[0], size[1], ", ".join(sorted(got))))
            else:
                print(f"SIZE-MAP-MISS: {pattern} -> {size[0]}x{size[1]} <- (0 files)")
                findings.append(
                    {
                        "kind": "size-rule",
                        "level": "fail",
                        "path": pattern,
                        "reason": (
                            f"size rule matched 0 file(s) under {os.path.basename(args.root)} "
                            f"(declared {size[0]}x{size[1]}) - declared-but-never-compared (idle rule)"
                        ),
                    }
                )
        matched = sum(1 for got in rule_hits.values() if got)
        by_rule = sum(len(v) for v in rule_hits.values())
        print(
            f"SIZE-MAP-COVERAGE: rules {matched}/{len(size_rules)} matched; "
            f"files by rule={by_rule} by default={len(default_hits)}"
            + ("" if expect is not None else " (no --expect-size: files outside the map are NOT size-checked)")
        )
        if expect is None and default_hits:
            findings.append(
                {
                    "kind": "size-default",
                    "level": "warn",
                    "path": args.root,
                    "reason": (
                        f"{len(default_hits)} file(s) fall outside the size map and no --expect-size was given "
                        "- those files are NOT size-checked (not a pass)"
                    ),
                }
            )

    allowed_map = {os.path.normcase(os.path.abspath(resolve(e["path"]))): e for e in allow}

    if scan_evidence:
        dup_findings, dup_stats = check_duplicate_images(args.root, skip)
    else:
        dup_findings, dup_stats = [], {"files": 0, "unique": 0, "duplicated": 0, "redundant": 0}
    findings.extend(dup_findings)

    if args.generated_html or args.source:
        if not (args.generated_html and args.source):
            print("ERROR: --generated-html and --source must be used together", file=sys.stderr)
            return 2
        findings.extend(check_generated(args.generated_html, args.source))

    if args.magic:
        for raw in [x.strip() for x in args.magic.split(",") if x.strip()]:
            p = resolve_existing(raw)
            if not p:
                print(f"ERROR: --magic file not found: {raw}", file=sys.stderr)
                return 2
            findings.extend(check_magic(p))

    if args.eol is not None:
        eol_files = [s.strip() for s in args.eol.split(",") if s.strip()]
        if not eol_files:
            # 精确才有人守：不带文件列表一律跳过（默认跑法不带 --eol，存量 296/699 含 CR，宽泛的红等于没有门禁）
            print("EOL: SKIP (no explicit file list; pass --eol <file[,file...]>)")
        for p in eol_files:
            p2 = resolve_existing(p)
            if not p2:
                print(f"ERROR: --eol file not found: {p}", file=sys.stderr)
                return 2
            findings.extend(check_eol(p2, args.eol_strict))

    for f in findings:
        entry = allowed_map.get(os.path.normcase(os.path.abspath(f["path"])))
        if entry:
            f["level"] = "allowed"
            f["allow_reason"] = entry["reason"]
            f["allow_by"] = entry["by"]
            f["allow_verdict"] = entry["verdict"]

    fails = [f for f in findings if f["level"] == "fail"]
    warns = [f for f in findings if f["level"] == "warn"]
    alloweds = [f for f in findings if f["level"] == "allowed"]

    for f in alloweds:
        print("ALLOWED {} {}: {} [by {}; verdict {}]".format(
            f["kind"], f["path"], f.get("allow_reason"), f.get("allow_by"), f.get("allow_verdict")
        ))

    for f in fails + warns:
        label = "FAIL" if f["level"] == "fail" else "WARN"
        extra = f.get("first_line") or f.get("reason")
        if f.get("group"):
            # 把整组成员打出来：只报「非 kept 的那些」时，读者容易误以为被报的就是多余的那个
            extra = f"{extra} [group: {', '.join(f['group'])}; kept={os.path.basename(f.get('kept') or '')}]"
        print(f"{label} {f['kind']} {f['path']}: {extra}")

    print(
        f"DUP: files={dup_stats['files']} unique={dup_stats['unique']} "
        f"duplicated={dup_stats['duplicated']} redundant={dup_stats['redundant']}"
    )
    print(
        f"EVIDENCE-LINT: scanned xml={counts['xml']} png={counts['png']} other={counts['other']} "
        f"fail={len(fails)} warn={len(warns)} allowed={len(alloweds)}"
    )

    if args.json:
        os.makedirs(os.path.dirname(os.path.abspath(args.json)), exist_ok=True)
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(
                {"root": args.root, "counts": counts, "dup": dup_stats, "findings": findings},
                fh,
                ensure_ascii=False,
                indent=2,
            )
        print(f"JSON: {args.json}")

    if fails or (args.strict and warns):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
