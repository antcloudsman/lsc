#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M6 四页色值统计（原 `checks/m6/color-sample.py`，2026-11-09 迁入仓库可跟踪位置）。

口径与输出格式保持原脚本不变：对每张 PNG 取**全图众数**作为背景色、取**最亮像素**作为文字色，
并输出前四个主要色及其占比。数据源见 `docs/fidelity/m6/client-four-pages-diff.md §0.3`。

用法（在仓库根执行，或从任意目录调用——默认根取本文件上两级）：
    python tests/e2e/color-sample.py
    python tests/e2e/color-sample.py --root E:\\APPDetector\\wt\\lc-client-p0   # 兼容旧 worktree

输出列：side/file | size | bg(modal) | share | brightest | top3
"""
from PIL import Image
from collections import Counter
import argparse
import os
import sys

# 仓库根：tests/e2e/color-sample.py -> 上两级
DEFAULT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

FILES = [
    ("orig/account-setting", r"docs\fidelity\m3\account-setting.png"),
    ("orig/complaint", r"docs\fidelity\m3\complaint.png"),
    ("orig/about", r"docs\fidelity\m3\about.png"),
    ("orig/contact", r"docs\fidelity\m3\contact.png"),
    ("lc/a-account", r"docs\fidelity\m3\stage3\a-account.png"),
    ("lc/b-complaint", r"docs\fidelity\m3\stage3\b-complaint-submit.png"),
    ("lc/c-about", r"docs\fidelity\m3\stage3\c-about.png"),
    ("lc/j-contact", r"docs\fidelity\m3\stage3\j-contact-toast.png"),
]


def main():
    ap = argparse.ArgumentParser(description="M6 四页色值统计（全图众数 + 最亮像素 + 前四主色）")
    ap.add_argument("--root", default=DEFAULT_ROOT,
                    help=f"仓库根（默认 {DEFAULT_ROOT}）")
    args = ap.parse_args()
    root = args.root

    print(f"{'side/file':22} {'size':12} {'bg(modal)':16} {'share':8} brightest")
    missing = 0
    for name, rel in FILES:
        p = os.path.join(root, rel)
        if not os.path.exists(p):
            print(f"{name:22} MISSING {p}")
            missing += 1
            continue
        im = Image.open(p).convert("RGB")
        w, h = im.size
        data = im.getdata()
        c = Counter(data)
        bg, bgn = c.most_common(1)[0]
        brightest = max(data, key=lambda t: t[0] * 0.299 + t[1] * 0.587 + t[2] * 0.114)
        top3 = [(col, round(n / (w * h), 3)) for col, n in c.most_common(4)[1:]]
        print(f"{name:22} {w}x{h:<7} {str(bg):16} {bgn/(w*h):7.2%} {brightest} top3={top3}")

    # 缺图时返回非零，避免把 MISSING 当成"通过"
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
