#!/usr/bin/env python3
"""打印两端读数与 oracle 的对照表（缺陷证据收集用，只读）。

用法：python tests/numeric/tools/dump_readings.py [航向...]
默认打印 0/3/7.5/10/15/22.5 与 60 龙标签对拍。
"""
import os
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

import oracle as O
import source_parse as SP


def main():
    main_activity = SP.read_text(O.MAIN_ACTIVITY_JAVA)
    main_rs = SP.read_text(O.MAIN_RS)
    exprs = SP.java_plate_exprs(main_activity)
    print("MainActivity plate 表达式:", exprs)
    print("h      地盘   人盘(spec/实际)   天盘(spec/实际)   Java实际   Rust实际")
    for h in (0.0, 3.0, 7.5, 10.0, 15.0, 22.5, 352.5, 359.99):
        jh = SP.java_plate(h, exprs[0])
        jt = SP.java_plate(h, exprs[1])
        rh = SP.rust_plate(main_rs, h, "hm")
        rt = SP.rust_plate(main_rs, h, "tm")
        print(f"{h:6.2f} {O.plate_name(h, 'earth'):>3} "
              f"{O.plate_name(h, 'human')}/{O.MOUNTAINS[jh]:<3} "
              f"{O.plate_name(h, 'heaven')}/{O.MOUNTAINS[jt]:<3} "
              f"{O.MOUNTAINS[jh]}{O.MOUNTAINS[jt]} "
              f"{O.MOUNTAINS[rh]}{O.MOUNTAINS[rt]}")

    print("\n60 龙：读数 vs 指针处标签（标签画在 k*6，格中心在 4.5+6k）")
    for h in (0.0, 0.5, 1.0, 2.0, 3.0, 180.0, 359.0):
        d = SP.rust_reading(main_rs, h)["d60"]
        near = min(range(60), key=lambda k: abs(((h - k * 6.0 + 180) % 360) - 180))
        print(f"h={h:6.2f}  读数={O.JIAZI[d]}(k={d}, 格中心 {O.dragon60_center(d):.1f}°)  "
              f"指针处标签={O.JIAZI[near]}(k={near}, 画在 {near * 6.0:.1f}°)")


if __name__ == "__main__":
    main()
