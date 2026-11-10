"""L2a 比较器（按 2026-11-10 审地图五条补全 + 实测校准）

设计要点（每条都可反查）：
  ① 分母 = **掩膜像素数**，不是全画布。1456² = 2,119,936；盘面 549²π/4 ≈ 236,700，差 8.96 倍。
     报告同时给比例与绝对像素数（比例用于判定，绝对量与块用于定位）。
  ② 掩膜只取 **canvas 侧 alpha>250**（drawCompass 每帧 clearRect，盘外真透明）；
     native 是 screencap/整图不透明，拿它算 alpha 掩膜会退化成全屏。同一掩膜套两张图。
  ③ 逐环存在性 **双向判**：native-only 与 canvas-only 的连续段都判，≥3° 即 FAIL。
     两侧阈值各自标定（内环透明度不同：canvas 用 rgba(...,0.45)），只判「结构有无」，不判亮度像不像。
  ④ 时序锚点：**不用 sig**（它只随 dialType 变，同类型重绘不改 sig）。取图前后要
     `groundRenderSeq`（每次 drawCompass 成功后自增）相等 + texDirty===false；没有 seq 时**判不可比**。
  ⑤ 阈值来自「同实现控制组」：把 canvas 侧与 native 侧都从 922 归一到同一空间（同一插值），
     量出的占比即基线 B；判定用 `pct ≤ B + margin`，报告必须打印 B。
  ⑥ 错位判据：1456 空间纯比例对 1px 不敏感（实测 0.39% < 2%），所以用**整数平移搜索**：
     最优平移必须是 (0,0)，且比最好的 ±1 邻居低 ≥ margin%（实测 (0,0)=8.69，邻居≈29.5，余量 70.5%）。

用法：
  python reports/m9-l2a-compare.py --canvas c.png --native n.png --control-pair a.png b.png
  python reports/m9-l2a-compare.py --selftest --selftest-space 1456
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

DIAL_RADII = [1.00, 0.91, 0.82, 0.73]
SPACE_DEFAULT = 1456
SIGMA_AT_549 = 1.5


def open_rgba(p: Path) -> Image.Image:
    return Image.open(p).convert("RGBA")


def norm(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.BILINEAR)


def sigma_for(space: int) -> float:
    """模糊按空间缩放：549 上 σ=1.5 对应 1456 上 σ≈3.98（物理等效）。"""
    return SIGMA_AT_549 * space / 549.0


# ---------- ① + ② 掩膜与差分 ----------
def diff_with_mask(canvas: Image.Image, native: Image.Image, sigma: float, mask_mode: str = "canvas"):
    """mask_mode:
      canvas —— 只用 canvas 侧 alpha>250（native 是 screencap 时唯一可用口径）
      union  —— canvas alpha>250 ∪ native alpha>250（native 必须是带 alpha 的原生导出图）
    为什么要有 union：canvas 缺画时，缺口落在 canvas 掩膜外，比例恒小（实测缺 350° 环只有 0.20%）。
    一旦原生侧能直出带 alpha 的 PNG，改并集后比例判据也能抓「缺画」，不必全靠存在性判据。
    """
    ca = np.asarray(canvas, dtype=np.int16)
    na = np.asarray(native, dtype=np.int16)
    mask_c = ca[:, :, 3] > 250
    if mask_mode == "union":
        mask = mask_c | (na[:, :, 3] > 250)
    else:
        mask = mask_c
    n_mask = int(mask.sum())
    cb = np.asarray(canvas.filter(ImageFilter.GaussianBlur(sigma)), dtype=np.int16)
    nb = np.asarray(native.filter(ImageFilter.GaussianBlur(sigma)), dtype=np.int16)
    d = np.abs(cb[:, :, :3] - nb[:, :, :3]).max(axis=2)
    return mask, d, n_mask


def blocks(mask: np.ndarray) -> list[tuple[int, float, float, int, int, int, int]]:
    """连通域 → [(面积, cx, cy, x0, y0, x1, y1)]，按面积降序。"""
    seen = np.zeros_like(mask, dtype=bool)
    out = []
    h, w = mask.shape
    for y0, x0 in np.argwhere(mask):
        if seen[y0, x0]:
            continue
        stack = [(y0, x0)]
        seen[y0, x0] = True
        n = 0
        sx = sy = 0.0
        minx, miny, maxx, maxy = w, h, -1, -1
        while stack:
            y, x = stack.pop()
            n += 1
            sx += x
            sy += y
            minx, miny = min(minx, x), min(miny, y)
            maxx, maxy = max(maxx, x), max(maxy, y)
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        out.append((n, sx / n, sy / n, minx, miny, maxx, maxy))
    out.sort(key=lambda t: -t[0])
    return out


# ---------- ⑥ 平移搜索 ----------
def shift_score(canvas: Image.Image, native: Image.Image) -> dict[tuple[int, int], float]:
    ca = np.asarray(canvas)
    na = np.asarray(native)
    mask = ca[:, :, 3] > 250
    res = {}
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            shifted = Image.new("RGBA", native.size, (0, 0, 0, 0))
            shifted.paste(native, (dx, dy))
            d = np.abs(ca[:, :, :3].astype(np.int16) - np.asarray(shifted)[:, :, :3].astype(np.int16)).max(axis=2)
            res[(dx, dy)] = float(d[mask].mean()) if mask.sum() else float("inf")
    return res


# ---------- ③ 逐环双向存在性 ----------
def ring_runs(canvas: Image.Image, native: Image.Image, space: int) -> dict[str, list[str]]:
    ca = np.asarray(canvas.convert("L"), dtype=np.float64)
    na = np.asarray(native.convert("L"), dtype=np.float64)
    cx = cy = space / 2.0
    step = 0.5
    angles = np.arange(0.0, 360.0, step)
    report = {"native_only": [], "canvas_only": []}

    def prof(img, r):
        vals = []
        for th in np.deg2rad(angles):
            best = 0.0
            for dr in (-2.0, -1.0, 0.0, 1.0, 2.0):
                x = int(round(cx + (r + dr) * math.cos(th)))
                y = int(round(cy - (r + dr) * math.sin(th)))
                if 0 <= x < space and 0 <= y < space:
                    best = max(best, img[y, x])
            vals.append(best)
        return np.asarray(vals)

    def longest_runs(bits: np.ndarray) -> list[tuple[float, float]]:
        runs = []
        cur = 0
        start = 0
        for i, b in enumerate(list(bits) + [False]):
            if b:
                if cur == 0:
                    start = i
                cur += 1
            else:
                if cur and cur * step >= 3.0:
                    runs.append((start * step, cur * step))
                cur = 0
        return runs

    for frac in DIAL_RADII:
        r = frac * (space / 2.0) - 1.0
        pn = prof(na, r)
        pc = prof(ca, r)
        # 每侧独立标定：「有描边」阈值 = 99.9 分位的一半。
        # 95 分位不够：缺弧让该半径只剩 2.8% 的角度有描边（10°/360°）时，p95 会掉到 0，
        # 阈值退化成 0 → has_c 全 true → 缺失被判成「没有缺失」（自检实测：gap 场景漏判）。
        hi_n = float(np.percentile(pn, 99.9)) if pn.size else 0.0
        hi_c = float(np.percentile(pc, 99.9)) if pc.size else 0.0
        tn = 0.5 * hi_n if hi_n > 0 else 0.0
        tc = 0.5 * hi_c if hi_c > 0 else 0.0
        if tn <= 0 or tc <= 0:
            continue
        has_n = pn >= tn
        has_c = pc >= tc
        if has_n.mean() < 0.5 and has_c.mean() < 0.5:
            continue      # 该半径两侧都没环（素材盘面常见），跳过
        for st, ln in longest_runs(has_n & ~has_c):
            report["native_only"].append(f"r={frac:.2f} 起{st:.1f}° 长{ln:.1f}°")
        for st, ln in longest_runs(has_c & ~has_n):
            report["canvas_only"].append(f"r={frac:.2f} 起{st:.1f}° 长{ln:.1f}°")
    return report


def ring_radius_match(canvas: Image.Image, native: Image.Image, space: int, tol_px: float = 1.5) -> list[str]:
    """逐环估半径：存在性判「有没有」，这条判「位置对不对」。

    对每条环，在半径 r0±10px 内找「该半径上平均亮度最高」的位置作为实测半径，
    两侧各估一次，|Δr| > tol_px 即判 FAIL（审地图建议的第 4 条，Δr ≤ 1.5px）。
    """
    ca = np.asarray(canvas.convert("L"), dtype=np.float64)
    na = np.asarray(native.convert("L"), dtype=np.float64)
    cx = cy = space / 2.0
    angs = np.linspace(0, 2 * math.pi, 720, endpoint=False)
    out = []

    def best_radius(img: np.ndarray, r0: float) -> tuple[float, float]:
        best_r, best_v = r0, -1.0
        for dr in np.arange(-10.0, 10.01, 0.5):
            r = r0 + dr
            vals = []
            for th in angs:
                x = int(round(cx + r * math.cos(th)))
                y = int(round(cy - r * math.sin(th)))
                if 0 <= x < space and 0 <= y < space:
                    vals.append(img[y, x])
            v = float(np.mean(vals)) if vals else 0.0
            if v > best_v:
                best_v, best_r = v, r
        return best_r, best_v

    for frac in DIAL_RADII:
        r0 = frac * (space / 2.0) - 1.0
        rn, vn = best_radius(na, r0)
        rc, vc = best_radius(ca, r0)
        if vn < 20 or vc < 20:      # 该半径任一侧几乎没描边 → 不判（存在性判据负责）
            continue
        delta = abs(rn - rc)
        flag = ""
        if delta > tol_px:
            flag = "  <-- FAIL"
            out.append(f"r={frac:.2f} native={rn:.1f}px canvas={rc:.1f}px Δ={delta:.1f}px{flag}")
        else:
            out.append(f"r={frac:.2f} native={rn:.1f}px canvas={rc:.1f}px Δ={delta:.1f}px")
    return out


def bbox_of(img: Image.Image) -> tuple[int, int, int, int, int] | None:
    """alpha>250 的外接框 → (x0,y0,x1,y1,直径=max(w,h))。"""
    a = np.asarray(img, dtype=np.int16)[:, :, 3] > 250
    ys, xs = np.where(a)
    if xs.size == 0:
        return None
    x0, x1, y0, y1 = int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())
    return x0, y0, x1, y1, max(x1 - x0 + 1, y1 - y0 + 1)


def alpha_shape_check(img: Image.Image) -> dict:
    """并集掩膜的前提：native PNG 必须「盘内实、盘外真透明」。

    bbox 尺寸对不代表 alpha 形状对：导出时漏 eraseColor(TRANSPARENT) 或用了 RGB_565，
    并集会膨胀到整幅 1456²，比例判据失去灵敏度，而 bbox 检查仍然通过（审地图 ②）。
    """
    al = np.asarray(img, dtype=np.int16)[:, :, 3]
    bb = bbox_of(img)
    if bb is None:
        return {"ok": False, "why": "全透明"}
    x0, y0, x1, y1, _ = bb
    inside = al[y0:y1 + 1, x0:x1 + 1]
    inner_frac = float((inside > 0).mean())   # alpha>0：线稿盘本就大量镂空，用 >250 会误判
    outer = al.copy()
    outer[y0:y1 + 1, x0:x1 + 1] = 0
    outer_frac_zero = float((outer == 0).mean())
    cover = ((x1 - x0 + 1) * (y1 - y0 + 1)) / float(al.size)
    # 全不透明导出时 bbox = 整幅，"盘外"为空集会真空通过——所以先判 bbox 覆盖率：
    # 覆盖率 >0.99 说明没有透明边界，并集掩膜会膨胀成整幅，alpha 形状判 BAD。
    ok = inner_frac >= 0.01 and outer_frac_zero >= 0.99 and cover <= 0.99
    return {"ok": ok, "inner": inner_frac, "outer_zero": outer_frac_zero, "cover": cover}


def compare(canvas_png: Path, native_png: Path, space: int, baseline: float | None, margin: float, mask_mode: str = "canvas", exclude_center: float = 0.0, block_min: int = 30, block_diff: int = 32) -> int:
    canvas = norm(open_rgba(canvas_png), space)
    native = norm(open_rgba(native_png), space)
    sigma = sigma_for(space)
    mask, d, n_mask = diff_with_mask(canvas, native, sigma, mask_mode)
    # C 区排除：中心三行读数随朝向实时变化（实测帧间 sig 就变），比它等于比「两个不同时刻的朝向」。
    # 判据表原文：C 文字区只判「三行都在、不叠字」，不比像素。
    if exclude_center > 0:
        yy, xx = np.mgrid[0:space, 0:space]
        cx = cy = space / 2.0
        r_dial = space * 0.37725 / 2.0          # 盘面半径（549/2）
        inside = ((xx - cx) ** 2 + (yy - cy) ** 2) <= (exclude_center * r_dial) ** 2
        dropped = int((mask & inside).sum())
        mask = mask & ~inside
        n_mask = int(mask.sum())
        print(f"EXCLUDE : 中心 r<{exclude_center:.2f}·r_dial 排除 {dropped} px（C 区读数不比）")
    over = (d > 8) & mask
    abs_px = int(over.sum())
    pct = abs_px / n_mask * 100.0 if n_mask else 0.0
    bl = blocks(over)

    print(f"PARAMS  : space={space} sigma={sigma:.2f} thresh=B+{margin}% mask={mask_mode} alpha>250")
    print(f"MASK    : {n_mask} px（分母；全画布 {space*space} px，比值 {space*space/max(n_mask,1):.2f}×）")
    print(f"PIXELS  : {abs_px} px over 8 -> {pct:.2f}%   mean={float(d[mask].mean()):.2f} max={int(d[mask].max())}")
    if bl:
        print("BLOCKS  : " + "; ".join(f"{n}px@({x:.0f},{y:.0f}) bbox=({x0},{y0})-({x1},{y1})"
                                       for n, x, y, x0, y0, x1, y1 in bl[:5]))
    else:
        print("BLOCKS  : 无")

    sc = shift_score(canvas, native)
    best = min(sc, key=lambda k: sc[k])
    neigh = min(sc[k] for k in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    margin_pct = (neigh - sc[(0, 0)]) / neigh * 100.0 if neigh > 0 else float("inf")
    print(f"SHIFT   : (0,0)={sc[(0,0)]:.3f} ±1邻居最优={neigh:.3f} 最佳={best} 余量={margin_pct:.1f}%")

    runs = ring_runs(canvas, native, space)
    print("RING    : native_only=" + ("; ".join(runs["native_only"]) or "无") +
          " | canvas_only=" + ("; ".join(runs["canvas_only"]) or "无"))
    rad = ring_radius_match(canvas, native, space)
    print("RADIUS  : " + ("; ".join(rad) if rad else "无可测环（存在性判据不可测，不计 PASS）"))
    radius_fail = [x for x in rad if "FAIL" in x]
    ring_measurable = bool(rad)          # 真实图上标不出环 ⇒ 该项不可测（M9-D2）

    # M9-D2（总监 2026-11-10 裁定）：主判改为「缺陷块绝对面积」——真实掩膜 215,021 px 下
    # 多画 10° 弧只有 0.32%，比例阈值会放过多达 40° 的局部缺陷；块判据对局部缺陷更紧。
    block_fail_detail = []
    for area, bx, by, x0, y0, x1, y1 in bl:
        if area < block_min:
            continue
        sub = d[y0:y1 + 1, x0:x1 + 1]
        if sub.size and int(sub.max()) >= block_diff:
            block_fail_detail.append((area, bx, by, int(sub.max())))
    block_fail = bool(block_fail_detail)

    fails = []
    if block_fail:
        a0, x0_, y0_, mx = block_fail_detail[0]
        fails.append(f"缺陷块 {a0}px@({x0_:.0f},{y0_:.0f}) maxdiff={mx} ≥{block_min}px/≥{block_diff}")
    print(f"VERDICT : ①缺陷块 {'FAIL' if block_fail else 'PASS'}"
          f"（阈值 面积≥{block_min}px 且 块内最大通道差≥{block_diff}）"
          + (f"；首个 {block_fail_detail[0][0]}px@({block_fail_detail[0][1]:.0f},{block_fail_detail[0][2]:.0f}) "
             f"max={block_fail_detail[0][3]}" if block_fail else ""))
    print(f"          （报告项，不作签署依据）占比 {pct:.2f}%、mean {float(d[mask].mean()):.2f}、max {int(d[mask].max())}")
    if best != (0, 0) or margin_pct < 15.0:
        fails.append(f"对齐 最佳={best} 余量={margin_pct:.1f}%")
        print(f"         ③对齐 FAIL（最佳 {best}，余量 {margin_pct:.1f}% < 15%）")
    else:
        print(f"         ③对齐 PASS（最佳 (0,0)，余量 {margin_pct:.1f}%）")
    if runs["native_only"] or runs["canvas_only"]:
        fails.append("环存在性有 ≥3° 差异段")
        print("         ④环 FAIL（双向）")
    elif not ring_measurable:
        print("         ④环 不可测（真实图未标出环半径，不计 PASS，也不判 FAIL）")
    else:
        print("         ④环 PASS（双向无 ≥3° 段）")
    if radius_fail:
        fails.append("环半径 Δ>1.5px")
        print(f"         ⑤半径 FAIL（{len(radius_fail)} 条超差）")
    elif not ring_measurable:
        print("         ⑤半径 不可测（同 ④）")
    else:
        print("         ⑤半径 PASS（Δ≤1.5px）")

    print("L2A: " + ("FAIL — " + "; ".join(fails) if fails else
                    ("PASS" if ring_measurable else "PASS(①③) / ④⑤ 不可测，不得据此签「画得像」")))
    return 1 if fails else 0


def control_baseline(space: int, dial: int = 0) -> float:
    """⑤ 同实现控制组：两侧都从 922 归一（同一插值），量出地板。"""
    asset = Path(r"E:\APPDetector\develop-int\app\android\app\src\main\assets\compass") / f"dial_full_{dial}.webp"
    img = open_rgba(asset).resize((922, 922), Image.BILINEAR)
    a = img.resize((space, space), Image.BILINEAR)
    b = img.resize((space, space), Image.BILINEAR)
    # 两条完全同管线 → 0；真正的地板要含「一侧从素材直出、一侧经 922」，见 m9-l2a-space.py
    _, d, n = diff_with_mask(a, b, sigma_for(space))
    return float(((d > 8)[(np.asarray(a)[:, :, 3] > 250)]).mean() * 100) if n else 0.0


def selftest(space: int) -> int:
    import tempfile

    size = space
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dr = ImageDraw.Draw(canvas)
    for frac in DIAL_RADII:
        r = frac * (size / 2.0) - 2
        dr.ellipse([size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r], outline=(212, 175, 55, 255), width=3)
    dr.line([size / 2, 40, size / 2, size - 40], fill=(200, 40, 40, 255), width=2)
    tmp = Path(tempfile.mkdtemp(prefix="l2a-cmp-"))
    canvas.save(tmp / "canvas.png")
    same = canvas.copy()
    same.save(tmp / "same.png")
    sh = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sh.paste(canvas, (1, 0))
    sh.save(tmp / "shift1.png")
    gap = canvas.copy()
    dg = ImageDraw.Draw(gap)
    r = DIAL_RADII[1] * (size / 2.0) - 2
    dg.arc([size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r], 0, 350, fill=(0, 0, 0, 0), width=6)
    gap.save(tmp / "gap10.png")
    extra = canvas.copy()
    de = ImageDraw.Draw(extra)
    r = DIAL_RADII[2] * (size / 2.0) - 2
    de.arc([size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r], 0, 20, fill=(255, 255, 255, 255), width=6)
    extra.save(tmp / "extra20.png")

    # 半径变异：把环整体外扩 3px（位置对、半径不对），Δr 判据必须红
    rad3 = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dr3 = ImageDraw.Draw(rad3)
    for frac in DIAL_RADII:
        r = frac * (size / 2.0) - 2 + 3
        dr3.ellipse([size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r], outline=(212, 175, 55, 255), width=3)
    rad3.save(tmp / "radius3.png")

    cases = [
        ("同图（绿）", tmp / "canvas.png", tmp / "same.png", 0),
        ("错位 1px（红）", tmp / "canvas.png", tmp / "shift1.png", 1),
        ("缺 10° 弧（红）", tmp / "gap10.png", tmp / "same.png", 1),
        ("多画 20° 弧（红，双向判）", tmp / "extra20.png", tmp / "same.png", 1),
        ("环外扩 3px（红，半径判）", tmp / "radius3.png", tmp / "same.png", 1),
    ]
    fails = 0
    for name, c, n, expect in cases:
        print(f"--- {name} ---")
        rc = compare(c, n, space, baseline=0.0, margin=2.0, mask_mode="union", exclude_center=0.0)
        ok = rc == expect
        print(f"[{'PASS' if ok else 'FAIL'}] {name}（exit={rc} 期望 {expect}）\n")
        if not ok:
            fails += 1
    print(f"L2A-SELFTEST: space={space} cases={len(cases)} fails={fails}")
    return 0 if fails == 0 else 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--canvas")
    ap.add_argument("--native")
    ap.add_argument("--space", type=int, default=SPACE_DEFAULT)
    ap.add_argument("--baseline", type=float, default=None, help="控制组基线 B（%%）；不给则比例判 SKIP")
    ap.add_argument("--margin", type=float, default=2.0)
    ap.add_argument("--control", action="store_true", help="打印同实现控制组基线 B（素材两管线）")
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--selftest-space", type=int, default=SPACE_DEFAULT)
    ap.add_argument("--exclude-center", type=float, default=0.0,
                    help="排除中心半径分数（×盘面半径）：C 区读数随朝向变，不参与像素比较")
    ap.add_argument("--mask", choices=["canvas", "union"], default="canvas",
                    help="canvas=只取 canvas alpha（screencap 原生图时唯一口径）；union=并集（原生需带 alpha 直出）")
    ap.add_argument("--bbox", action="store_true", help="打印两侧盘面外接框（几何契约验收用）")
    ap.add_argument("--expect-dial", type=float, default=None, help="期望盘面直径 px（如 549），±1 判 PASS")
    ap.add_argument("--expect-center", default=None, help="期望盘心 x,y（如 728,728），±1 判 PASS")
    ap.add_argument("--noise", nargs=2, metavar=("A", "B"), help="噪声地板模式：同一侧同管线两张图，只打占比")
    ap.add_argument("--noise-mask", choices=["canvas", "union"], default="union")
    a = ap.parse_args()
    if a.noise:
        p1, p2 = Path(a.noise[0]), Path(a.noise[1])
        im1, im2 = norm(open_rgba(p1), a.space), norm(open_rgba(p2), a.space)
        m, d, n = diff_with_mask(im1, im2, sigma_for(a.space), a.noise_mask)
        over = (d > 8) & m
        pct = int(over.sum()) / n * 100.0 if n else 0.0
        print(f"NOISE   : mode={a.noise_mask} space={a.space} mask={n} px  pct={pct:.3f}%  "
              f"max={int(d[m].max()) if n else 0}")
        return 0
    if a.bbox:
        if not (a.canvas and a.native):
            ap.error("--bbox 需要 --canvas 与 --native")
        fails = 0
        for tag, p in (("canvas", a.canvas), ("native", a.native)):
            im = norm(open_rgba(Path(p)), a.space)
            bb = bbox_of(im)
            if bb is None:
                print(f"BBOX {tag}: 全透明（不可用）")
                fails += 1
                continue
            x0, y0, x1, y1, dia = bb
            cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
            print(f"BBOX {tag}: x0={x0} y0={y0} x1={x1} y1={y1} 直径={dia} 盘心=({cx:.1f},{cy:.1f})")
            if a.expect_dial is not None and abs(dia - a.expect_dial) > 1:
                print(f"       FAIL 直径 {dia} 与期望 {a.expect_dial} 差 >1px")
                fails += 1
            if a.expect_center:
                ex, ey = [float(v) for v in a.expect_center.split(",")]
                if abs(cx - ex) > 1 or abs(cy - ey) > 1:
                    print(f"       FAIL 盘心 ({cx:.1f},{cy:.1f}) 与期望 ({ex},{ey}) 差 >1px")
                    fails += 1
            asc = alpha_shape_check(im)
            print(f"ALPHA {tag}: 盘内 alpha>0 占比={asc.get('inner', 0)*100:.1f}%  "
                  f"盘外 alpha==0 占比={asc.get('outer_zero', 0)*100:.2f}% bbox覆盖率={asc.get('cover', 0)*100:.1f}%  -> {'OK' if asc['ok'] else 'BAD'}")
            if tag == "native" and not asc["ok"]:
                print("       FAIL 原生图 alpha 形状不合格：并集掩膜会膨胀，比例判据失去灵敏度")
                fails += 1
        print("BBOX-CHECK: " + ("PASS" if fails == 0 else f"FAIL ({fails})"))
        return 0 if fails == 0 else 1
    if a.selftest:
        return selftest(a.selftest_space)
    if a.control:
        print(f"CONTROL-B({a.space}) = {control_baseline(a.space):.2f}%")
        return 0
    if not (a.canvas and a.native):
        ap.error("需要 --canvas 与 --native（或 --selftest / --control）")
    return compare(Path(a.canvas), Path(a.native), a.space, a.baseline, a.margin, a.mask, a.exclude_center)


if __name__ == "__main__":
    sys.exit(main())
