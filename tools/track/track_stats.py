#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
F-15 轨迹四项统计 · JSONL 复算脚本（与 Android TrackStats.java 逐位同口径）。

用法：
  python tools/track/track_stats.py <track.jsonl>

输出（与 App 详情页显示同精度）：
  总里程 km 2 位小数 / 总用时 h:mm:ss / 平均速度 km/h 1 位 / 最高速度 km/h 1 位

算法（docs/features/F-15-轨迹-免费实现方案.md §4）：
- 预处理：acc>30m 丢弃；dt<=0 丢弃；p.seg 变化 / dt>120s / d/dt>40 m/s 断段
- 里程：同段相邻有效点 Haversine 累加（R=6371008.8），d<0.5m 不计
- 用时：逐段 dt 累加，排除连续 >=60s 且 d/dt<0.5 m/s 的停顿
- 平均：L / 运动用时
- 最高：中心差分窗口 >=2s，跨段不参与；峰值需有 ±10% 相邻候选，否则取最高候选
"""
import json
import math
import sys

R = 6371008.8
ACC_MAX = 30.0
JUMP_MPS = 40.0
LONG_GAP_MS = 120000
PAUSE_MS = 60000.0
PAUSE_MPS = 0.5
WINDOW_MS = 2000


def hav(lat1, lng1, lat2, lng2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    x = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(x))


def compute(points):
    segs, cur, prev = [], [], None
    for p in points:
        if p.get("acc", 0) > ACC_MAX:
            continue
        if prev is None:
            cur.append(p)
            prev = p
            continue
        if p["t"] <= prev["t"]:
            continue
        d = hav(prev["lat"], prev["lng"], p["lat"], p["lng"])
        dt = (p["t"] - prev["t"]) / 1000.0
        brk = p.get("seg", 0) != prev.get("seg", 0) or (p["t"] - prev["t"]) > LONG_GAP_MS or (dt > 0 and d / dt > JUMP_MPS)
        if brk:
            if cur:
                segs.append(cur)
            cur = []
        cur.append(p)
        prev = p
    if cur:
        segs.append(cur)

    dist = 0.0
    moving = 0.0
    first_t = last_t = 0
    n = 0
    for seg in segs:
        n += len(seg)
        for p in seg:
            if first_t == 0:
                first_t = p["t"]
            last_t = p["t"]
        for i in range(1, len(seg)):
            a, b = seg[i - 1], seg[i]
            d = hav(a["lat"], a["lng"], b["lat"], b["lng"])
            dt = (b["t"] - a["t"]) / 1000.0
            if d >= 0.5:
                dist += d
            if not (dt >= PAUSE_MS / 1000.0 and d / dt < PAUSE_MPS):
                moving += dt

    avg = (dist / moving) * 3.6 if moving > 0 else float("nan")
    cands = []
    for seg in segs:
        m = len(seg)
        if m < 3:
            continue
        for i in range(1, m - 1):
            a, b = i - 1, i + 1
            while (seg[b]["t"] - seg[a]["t"]) < WINDOW_MS and (a > 0 or b < m - 1):
                if a > 0:
                    a -= 1
                if b < m - 1:
                    b += 1
            win = seg[b]["t"] - seg[a]["t"]
            if win < WINDOW_MS:
                continue
            d = hav(seg[a]["lat"], seg[a]["lng"], seg[b]["lat"], seg[b]["lng"])
            cands.append(d / (win / 1000.0))

    vmax = float("nan")
    if cands:
        best = None
        for i, v in enumerate(cands):
            stable = any(i != j and abs(v - w) <= v * 0.10 for j, w in enumerate(cands))
            if stable and (best is None or v > best):
                best = v
        vmax = (best if best is not None else max(cands)) * 3.6

    return {
        "distance_km": dist / 1000.0,
        "duration_sec": moving,
        "avg_kmh": avg,
        "max_kmh": vmax,
        "points": n,
        "segments": len(segs),
        "first_t": first_t,
        "last_t": last_t,
    }


def fmt_duration(sec):
    s = int(round(max(0.0, sec)))
    return f"{s // 3600}:{(s % 3600) // 60:02d}:{s % 60:02d}"


def main():
    if len(sys.argv) != 2:
        print("usage: python tools/track/track_stats.py <track.jsonl>")
        return 2
    pts = []
    with open(sys.argv[1], "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                pts.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    r = compute(pts)
    avg = "—" if math.isnan(r["avg_kmh"]) else f"{r['avg_kmh']:.1f}"
    vmax = "—" if math.isnan(r["max_kmh"]) else f"{r['max_kmh']:.1f}"
    print(f"总里程 {r['distance_km']:.2f} km")
    print(f"总用时 {fmt_duration(r['duration_sec'])}")
    print(f"平均速度 {avg} km/h")
    print(f"最高速度 {vmax} km/h")
    print(f"点数 {r['points']} · 分段 {r['segments']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
