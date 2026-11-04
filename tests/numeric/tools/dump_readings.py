#!/usr/bin/env python3
"""打印两端读数与 `rings[].labels` 对照表（证据收集/回归用，只读）。

用法：python tests/numeric/tools/dump_readings.py
- 三盘：Java（MainActivity 表达式）/ Rust（main.rs 表达式）/ oracle；
- 圈层文字：B2 起 = `ring_text(type, h) -> rings[].labels[i]`，此处按同一公式从
  `mechanics.json` 复算（`i = floor(((h-offset+360)%360)/interval)`）。
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

    doc = O.load_mechanics_doc()
    rings = {r["type"]: r for r in doc["rings"]}
    headings = (0.0, 15.0, 170.0)
    print("\n圈层文字（rings[].labels，B2 换表后 = 界面读数文字）")
    print(f"{'ring':<8}" + "".join(f"{h:>16.1f}°" for h in headings))
    for name, t in (("后天卦", 2), ("先天卦", 1), ("分金", 6),
                    ("72龙", 5), ("60龙", 7), ("正兼向", 14)):
        cells = []
        for h in headings:
            i = O.dex_index(h, rings[t]["offset"], rings[t]["interval"])
            cells.append(f"{rings[t]['labels'][i]:>15}")
        print(f"{name:<8}" + "".join(cells))


if __name__ == "__main__":
    main()
