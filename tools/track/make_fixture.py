#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
F-15 合成轨迹夹具生成器（真机注入用，不依赖 GPS 走路）。

生成一对文件，格式与 App 落盘一致：
  <out>/<id>.jsonl     每行 {"t","lat","lng","acc","spd","seg","src"}
  <out>/<id>.meta.json 列表只读它，stats 用 track_stats.compute 预填

默认：200 点 · 每点向北 1 m · 1 s 一点 · 1 m/s 匀速 · 起点 (36.25, 117.10)。
注入方式（设备）：
  adb shell run-as com.luopan.fengshui mkdir -p files/tracks
  adb push <id>.jsonl <id>.meta.json /data/local/tmp/
  adb shell run-as com.luopan.fengshui cp /data/local/tmp/<id>.* files/tracks/

用法：
  python tools/track/make_fixture.py [--id fixture-straight200] [--points 200]
                                     [--step-m 1.0] [--interval-s 1.0]
                                     [--lat 36.25] [--lng 117.10] [--out tools/track/fixtures]
"""
import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from track_stats import compute, fmt_duration, R  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", default="fixture-straight200")
    ap.add_argument("--points", type=int, default=200)
    ap.add_argument("--step-m", type=float, default=1.0)
    ap.add_argument("--interval-s", type=float, default=1.0)
    ap.add_argument("--lat", type=float, default=36.25)
    ap.add_argument("--lng", type=float, default=117.10)
    ap.add_argument("--acc", type=float, default=5.0, help="每条点精度（米），需 <=30 才不被丢弃")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures"))
    a = ap.parse_args()

    os.makedirs(a.out, exist_ok=True)
    dlat = a.step_m / R * 180.0 / math.pi
    t0 = 1759600000000  # 固定基准时间，保证复算可重复
    pts = []
    for i in range(a.points):
        pts.append({
            "t": int(t0 + i * a.interval_s * 1000),
            "lat": round(a.lat + i * dlat, 9),
            "lng": round(a.lng, 9),
            "acc": a.acc,
            "spd": a.step_m / a.interval_s,
            "seg": 0,
            "src": "fixture",
        })

    jsonl = os.path.join(a.out, a.id + ".jsonl")
    with open(jsonl, "w", encoding="utf-8", newline="\n") as f:
        for p in pts:
            f.write(json.dumps(p, ensure_ascii=False, separators=(",", ":")) + "\n")

    r = compute(pts)
    meta = {
        "id": a.id,
        "startedAt": pts[0]["t"],
        "stoppedAt": pts[-1]["t"],
        "status": "finished",
        "lastSeq": len(pts),
        "intervalMs": int(a.interval_s * 1000),
        "minDistanceM": 3.0,
        "segments": r["segments"],
        "points": r["points"],
        "distanceM": r["distance_km"] * 1000.0,
        "durationSec": r["duration_sec"],
        "avgKmh": r["avg_kmh"],
        "maxKmh": r["max_kmh"],
        "samplingChanges": [],
    }
    metap = os.path.join(a.out, a.id + ".meta.json")
    with open(metap, "w", encoding="utf-8", newline="\n") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"jsonl  {jsonl}")
    print(f"meta   {metap}")
    print(f"点数 {r['points']} · 分段 {r['segments']}")
    print(f"总里程 {r['distance_km']:.2f} km  ({r['distance_km'] * 1000:.3f} m)")
    print(f"总用时 {fmt_duration(r['duration_sec'])}")
    print(f"平均速度 {r['avg_kmh']:.1f} km/h")
    print(f"最高速度 {r['max_kmh']:.1f} km/h")
    return 0


if __name__ == "__main__":
    sys.exit(main())
