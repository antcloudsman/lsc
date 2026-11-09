#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
M6「联系我们」Dialog 像素量测（原 `checks/m6/contact-dialog-measure.py`，2026-11-09 迁入仓库）。

口径与输出格式保持原脚本不变：对 `docs/fidelity/m3/contact.png` 做纯像素扫描（无设备）
- 圆角：卡片顶边 y=1127 起逐行找首个白像素的 x 内缩量；
- 蓝色按钮 bbox：限定在白卡内，按 b>140 且 b-r>50 且 b-g>30 判定；
- 输出按钮中心采样色。

用法（仓库根执行，或任意目录；默认根取本文件上两级）：
    python tests/e2e/contact-dialog-measure.py
    python tests/e2e/contact-dialog-measure.py --root E:\\APPDetector\\wt\\lc-client-p0

数据引用：`docs/fidelity/m6/client-four-pages-diff.md §2`（未决项 3）。
"""
from PIL import Image
import argparse
import os
import sys

DEFAULT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CARD_LEFT, CARD_TOP, CARD_RIGHT, CARD_BOTTOM = 168, 1127, 1058, 1693


def main():
    ap = argparse.ArgumentParser(description="M6 联系我们 Dialog 像素量测（圆角内缩 + 蓝按钮 bbox）")
    ap.add_argument("--root", default=DEFAULT_ROOT, help=f"仓库根（默认 {DEFAULT_ROOT}）")
    args = ap.parse_args()

    p = os.path.join(args.root, r"docs\fidelity\m3\contact.png")
    if not os.path.exists(p):
        print(f"MISSING {p}")
        return 1

    im = Image.open(p).convert("RGB")
    W, H = im.size
    px = im.load()
    is_white = lambda c: c[0] > 250 and c[1] > 250 and c[2] > 250

    # 圆角：卡片顶边 y=1127 起，逐行找首个白像素的 x 内缩量
    print("corner inset per row (from y=1127):")
    for k in range(0, 40):
        y = 1127 + k
        first = None
        for x in range(160, 400):
            if is_white(px[x, y]):
                first = x
                break
        print("  row +%2d  inset=%s" % (k, (first - CARD_LEFT) if first is not None else None))

    # 蓝色按钮 bbox（限定在卡片内）
    x0, y0, x1, y1 = W, H, -1, -1
    for y in range(CARD_TOP, CARD_BOTTOM + 1, 2):
        for x in range(CARD_LEFT, CARD_RIGHT + 1, 2):
            r, g, b = px[x, y]
            if b > 140 and b - r > 50 and b - g > 30:
                x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
    print("blue button bbox", (x0, y0, x1, y1), "w,h =", x1 - x0 + 1, y1 - y0 + 1)
    print("button center color", px[(x0 + x1) // 2, (y0 + y1) // 2])
    return 0


if __name__ == "__main__":
    sys.exit(main())
