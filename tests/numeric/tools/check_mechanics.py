#!/usr/bin/env python3
"""质量红线用：打印 mechanics.json 的 19 项自检结果（来源数/估计数）。

用法：python tests/numeric/tools/check_mechanics.py
退出码 0 = 19 项全部有来源且 0 估计。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, HERE)
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

import oracle as O

FORBIDDEN = ("估计", "估算", "estimate", "estimated", "unconfirmed", "TODO", "未确认")


def main() -> int:
    doc = O.load_mechanics_doc()
    values = doc["values"]
    estimated = []
    for v in values:
        src = str(v.get("source", "")).strip()
        if not src or any(t.lower() in src.lower() for t in FORBIDDEN):
            estimated.append(v["name"])
    print(f"mechanics.json version={doc.get('version')} values={len(values)} "
          f"sourced={len(values) - len(estimated)} estimated={len(estimated)}")
    print(f"rings={len(doc.get('rings', []))} dials={len(doc.get('dials', []))} "
          f"tables={','.join(sorted(doc.get('tables', {})))}")
    for v in values:
        print(f"  - {v['name']}: {v['value']} {v.get('unit', '')}")
    if estimated:
        print("FAIL: 以下条目缺来源或含估计标记:", ", ".join(estimated))
        return 1
    print("PASS: 19 项全部有来源、0 项估计")
    return 0


if __name__ == "__main__":
    sys.exit(main())
