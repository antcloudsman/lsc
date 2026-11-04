#!/usr/bin/env python3
"""两张 PNG 的区域 diff（AT-P0-0 诊断用，不做硬门禁）。

用法：
  python tests/numeric/tools/img_diff.py <a.png> <b.png> [x1 y1 x2 y2]
输出（UTF-8）：diff=<px> total=<px> pct=<%.2f> bbox=<x1,y1,x2,y2|none>
退出码：0 = 已输出结果（不表示相同）；2 = 参数/读图失败。
"""
import sys

from PIL import Image


def main() -> int:
    if len(sys.argv) not in (3, 7):
        print("usage: img_diff.py a.png b.png [x1 y1 x2 y2]", file=sys.stderr)
        return 2
    a = Image.open(sys.argv[1]).convert("RGB")
    b = Image.open(sys.argv[2]).convert("RGB")
    box = None
    if len(sys.argv) == 7:
        box = tuple(int(v) for v in sys.argv[3:7])
        a = a.crop(box)
        b = b.crop(box)
    if a.size != b.size:
        print(f"SIZE_MISMATCH {a.size} vs {b.size}")
        return 2
    pa = a.load()
    pb = b.load()
    w, h = a.size
    diff = 0
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            p, q = pa[x, y], pb[x, y]
            if abs(p[0] - q[0]) + abs(p[1] - q[1]) + abs(p[2] - q[2]) > 12:
                diff += 1
                xs.append(x)
                ys.append(y)
    total = w * h
    bbox = "none" if diff == 0 else f"{min(xs)},{min(ys)},{max(xs)},{max(ys)}"
    print(f"diff={diff} total={total} pct={100.0 * diff / total:.2f} bbox={bbox}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
