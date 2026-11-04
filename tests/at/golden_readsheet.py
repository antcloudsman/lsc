# -*- coding: utf-8 -*-
"""AT-P0-2 P2-07/P2-08 golden：智能读盘弹层的期望文本。

数据**只**取自 app/mechanics.json（rings[].labels / rings[].text / tables.mountains），
不调用 Java——用来证明「界面显示的 14 行 = 唯一数值源」，而不是拿实现自证。

用法：python tests/at/golden_readsheet.py 94.04
输出：JSON（stdout，UTF-8）
"""
import json
import math
import os
import sys
from decimal import Decimal, ROUND_HALF_UP


def norm(x):
    x = x % 360.0
    return x


def jfmt1(x):
    """Java String.format("%.1f") 是 HALF_UP，python round 是 half-even，这里对齐 Java。"""
    return str(Decimal(repr(float(x))).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP))


def ring_index(ring, h):
    off = float(ring["offset"])
    iv = float(ring["interval"])
    x = ((h - off) % 360.0 + 360.0) % 360.0
    return int(math.floor(x / iv)) % len(ring["labels"])


def clean(s):
    return (s or "").replace("\u200c", "")


def golden(heading, mech):
    rings = mech["rings"]
    mountains = mech["tables"]["mountains"]
    h = norm(heading)
    back = norm(heading + 180.0)
    m = int(math.floor(norm(h + 7.5) / 15.0)) % 24
    sitting = (m + 12) % 24
    rows = []
    for i in range(14):
        r = rings[i]
        fi = ring_index(r, h)
        si = ring_index(r, back)
        face = clean(r["labels"][fi])
        sit = clean(r["labels"][si])
        rows.append({
            "type": r["type"],
            "name": clean(r["text"]),
            "face_index": fi,
            "sit_index": si,
            "face": face,
            "sit": sit,
            "text": "向：" + face + "    坐：" + sit,
        })
    return {
        "heading_in": heading,
        "h": h,
        "back": back,
        "mountains": mountains,
        "mountain_index": m,
        "sitting_index": sitting,
        "head1": "向北 %s°    坐南 %s°" % (jfmt1(h), jfmt1(back)),
        "head2": "坐%s山  向%s山" % (clean(mountains[sitting]), clean(mountains[m])),
        "rows": rows,
    }


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(os.path.dirname(here))
    mech_path = os.path.join(root, "app", "mechanics.json")
    with open(mech_path, encoding="utf-8") as f:
        mech = json.load(f)
    argv = [a for a in sys.argv[1:]]
    out_path = None
    if len(argv) >= 2 and argv[-2] == "--out":
        out_path = argv[-1]
        argv = argv[:-2]
    heading = float(argv[0]) if argv else 0.0
    out = golden(heading, mech)
    out["mechanics_sha256"] = __import__("hashlib").sha256(
        open(mech_path, "rb").read()).hexdigest()
    text = json.dumps(out, ensure_ascii=False, indent=1)
    if out_path:
        # 直接由 python 写 UTF-8 文件：不经 PowerShell 管道，避免控制台代码页把中文弄坏
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(text)
        sys.stdout.write("wrote %s\n" % out_path)
    else:
        sys.stdout.buffer.write(text.encode("utf-8"))


if __name__ == "__main__":
    main()
