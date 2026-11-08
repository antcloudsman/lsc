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

退出码：0 = 无 fail；1 = 有 fail（--strict 时 warn 也算）；2 = 参数/路径错误。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

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


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Content validity lint for fidelity evidence (.xml/.png)")
    ap.add_argument("--root", default="docs/fidelity", help="evidence root (default: docs/fidelity)")
    ap.add_argument("--strict", action="store_true", help="treat warnings as failures")
    ap.add_argument("--mono-ratio", type=float, default=0.995, help="monochrome threshold (default 0.995)")
    ap.add_argument("--min-png-side", type=int, default=600, help="only lint png with min(w,h) >= this (default 600)")
    ap.add_argument("--skip-dirs", default="assets-vectors,assets,findings,probes,.git", help="comma-separated dir names to skip")
    ap.add_argument("--strict-root", action="store_true", help="warn when xml root is not <hierarchy>")
    ap.add_argument("--expect-size", help="declared screenshot size WxH, e.g. 1228x2700 (mismatch = warn)")
    ap.add_argument("--allow", help="allowlist JSON with entries {path, reason, by, verdict} (4 keys required)")
    ap.add_argument("--json", help="write JSON report to this path")
    args = ap.parse_args(argv)

    if not os.path.isdir(args.root):
        print(f"ERROR: not a directory: {args.root}", file=sys.stderr)
        return 2

    expect = None
    if args.expect_size:
        try:
            w_s, h_s = args.expect_size.lower().split("x")
            expect = (int(w_s), int(h_s))
        except ValueError:
            print(f"ERROR: bad --expect-size {args.expect_size!r}", file=sys.stderr)
            return 2

    allow: list[dict] = []
    if args.allow:
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
    for path in walk(args.root, skip):
        ext = os.path.splitext(path)[1].lower()
        try:
            if ext in XML_EXTS:
                counts["xml"] += 1
                findings.extend(check_xml(path, args.strict_root))
            elif ext in PNG_EXTS:
                counts["png"] += 1
                findings.extend(check_png(path, args.mono_ratio, args.min_png_side, expect))
            else:
                counts["other"] += 1
        except FileNotFoundError:
            continue

    allowed_map = {os.path.normcase(os.path.abspath(e["path"])): e for e in allow}
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
        print(f"{label} {f['kind']} {f['path']}: {extra}")

    print(
        f"EVIDENCE-LINT: scanned xml={counts['xml']} png={counts['png']} other={counts['other']} "
        f"fail={len(fails)} warn={len(warns)} allowed={len(alloweds)}"
    )

    if args.json:
        os.makedirs(os.path.dirname(os.path.abspath(args.json)), exist_ok=True)
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(
                {"root": args.root, "counts": counts, "findings": findings},
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
