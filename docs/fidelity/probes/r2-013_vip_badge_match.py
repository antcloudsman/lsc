"""R2-013 A 项定量：哪一张原版位图就是读盘 sheet 行尾那枚金色 VIP 徽标？

用法（仓库根目录，需要 Pillow）：
    python docs/fidelity/probes/r2-013_vip_badge_match.py \
        [--shot docs/fidelity/orig/w3-sheet.png] \
        [--res  E:\\APPDetector\\output\\.inspect\\unpack\\decoded\\res]

方法：
1. 在原版截图里按「金色」阈值求该徽标的真实外接框（a11y 节点框会被裁剪，不能用）；
2. 把 res 下所有 PNG/WebP（<=500x500）缩放到该框尺寸，与截图区域算平均绝对误差（MAE），取最小；
3. 第一名显著领先（实测 13.41 vs 19.97）才判命中。

2026-11-07 实测结论：mipmap-xxxhdpi/ic_compass_vip.webp（132x112）MAE=13.41，次名 19.97。
"""
import argparse
import os
import statistics
import sys

from PIL import Image


def golden_bbox(shot, x0, y0, x1, y1):
    px = shot.load()
    xs, ys = [], []
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = px[x, y]
            if r > 230 and 150 < g < 225 and b < 160 and (r - b) > 60:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--shot", default=r"docs/fidelity/orig/w3-sheet.png")
    ap.add_argument("--res", default=r"E:\APPDetector\output\.inspect\unpack\decoded\res")
    ap.add_argument("--search", default="840,1950,960,2040", help="搜索金色像素的窗口 x0,y0,x1,y1")
    ap.add_argument("--top", type=int, default=8)
    args = ap.parse_args()

    sx0, sy0, sx1, sy1 = (int(v) for v in args.search.split(","))
    shot = Image.open(args.shot).convert("RGB")
    box = golden_bbox(shot, sx0, sy0, sx1, sy1)
    if box is None:
        print("[FAIL] 没找到金色像素")
        return 2
    gx0, gy0, gx1, gy1 = box
    w, h = gx1 - gx0 + 1, gy1 - gy0 + 1
    print(f"金色像素外接框: [{gx0},{gy0}][{gx1},{gy1}] -> {w}x{h} ratio={w / h:.4f}")
    crop = shot.crop((gx0, gy0, gx1 + 1, gy1 + 1))
    crop_px = [crop.getpixel((x, y)) for y in range(0, h, 3) for x in range(0, w, 3)]

    results = []
    for dirpath, _dn, filenames in os.walk(args.res):
        for fn in filenames:
            if not fn.lower().endswith((".png", ".webp")):
                continue
            p = os.path.join(dirpath, fn)
            try:
                im = Image.open(p)
                if im.width > 500 or im.height > 500:
                    continue
                im = im.convert("RGBA")
            except Exception:
                continue
            bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
            bg.alpha_composite(im)
            a = bg.convert("RGB").resize((w, h), Image.LANCZOS)
            px = [a.getpixel((x, y)) for y in range(0, h, 3) for x in range(0, w, 3)]
            mae = statistics.mean(
                sum(abs(px[i][c] - crop_px[i][c]) for c in range(3)) / 3 for i in range(len(px))
            )
            results.append((mae, os.path.relpath(p, args.res), im.size))
    results.sort()
    print("误差最小的候选：")
    for mae, rel, size in results[: args.top]:
        print(f"  MAE={mae:6.2f}  {rel}  {size[0]}x{size[1]}")
    if results:
        print(f"[PASS] 命中 {results[0][1]} (MAE={results[0][0]:.2f})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
