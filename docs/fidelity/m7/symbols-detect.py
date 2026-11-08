"""M7 A轮 · 原版截图「屏幕中心符号」检测（可复跑）

用法（仓库根目录）：
    python docs/fidelity/m7/symbols-detect.py [--dir docs/fidelity/orig] [--one map-home-01.png]

判定式（与 docs/fidelity/m7/symbols-stage1.md §3.2 一致）：
  · 红点：屏幕中心 13x13 内满足 r>180 && g<110 && b<130 的像素数 >= 40
  · 蓝臂：沿中心行/列向外 8..70px，满足 b>180 && 120<g<190 && r<70 的像素数，四向各 >= 15
  · 四项齐全 -> HIT（屏幕中心符号）
  · 仅 U/D 命中   -> NEEDLE（罗盘竖向指针）
  · 全 0          -> NONE

2026-11-08 实测：55 张原版截图，23 张 HIT、6 张 NEEDLE、其余 NONE。
"""
import argparse
import os
import sys

from PIL import Image


def probe(path):
    im = Image.open(path).convert("RGB")
    W, H = im.size
    cx, cy = W // 2, H // 2
    px = im.load()

    red = 0
    for y in range(cy - 6, cy + 7):
        for x in range(cx - 6, cx + 7):
            r, g, b = px[x, y]
            if r > 180 and g < 110 and b < 130:
                red += 1

    def is_blue(r, g, b):
        return b > 180 and 120 < g < 190 and r < 70

    L = R = U = D = 0
    for d in range(8, 70):
        if cx - d >= 0 and is_blue(*px[cx - d, cy]):
            L += 1
        if cx + d < W and is_blue(*px[cx + d, cy]):
            R += 1
        if cy - d >= 0 and is_blue(*px[cx, cy - d]):
            U += 1
        if cy + d < H and is_blue(*px[cx, cy + d]):
            D += 1
    return dict(size=(W, H), red=red, L=L, R=R, U=U, D=D)


def classify(res):
    arms = [res["L"], res["R"], res["U"], res["D"]]
    if res["red"] >= 40 and all(a >= 15 for a in arms):
        return "HIT"
    if res["U"] >= 15 and res["D"] >= 15 and res["L"] < 15 and res["R"] < 15:
        return "NEEDLE"
    return "NONE"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=os.path.join("docs", "fidelity", "orig"))
    ap.add_argument("--one", default=None)
    args = ap.parse_args()

    if args.one:
        res = probe(os.path.join(args.dir, args.one))
        print(args.one, res, classify(res))
        return 0

    if not os.path.isdir(args.dir):
        print(f"[FAIL] dir not found: {args.dir}")
        return 2

    tally = {"HIT": 0, "NEEDLE": 0, "NONE": 0}
    print(f"{'file':38} red L R U D  verdict")
    for fn in sorted(os.listdir(args.dir)):
        if not fn.lower().endswith(".png"):
            continue
        try:
            res = probe(os.path.join(args.dir, fn))
        except Exception as exc:  # 损坏或非 RGB
            print(f"{fn:38} SKIP ({exc})")
            continue
        v = classify(res)
        tally[v] += 1
        print(f"{fn:38} {res['red']:3} {res['L']:2} {res['R']:2} {res['U']:2} {res['D']:2}  {v}")

    print()
    print(f"HIT={tally['HIT']}  NEEDLE={tally['NEEDLE']}  NONE={tally['NONE']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
