"""R2-013 A 项定性：读盘 sheet 行尾的「◆」到底是不是字符？

用法（仓库根目录）：
    python docs/fidelity/probes/r2-013_vip_badge_scan.py [--unpack E:\\APPDetector\\output\\.inspect\\unpack]

判据：如果 DEX/smali 与资源里都存在 U+25C6（◆）字符串，那它就是文本；否则它是图形。
结果（2026-11-07 实测）：mem_smali 7698 个 smali 无命中；mem_dump 30 个 dex 字节级无命中；
decoded/res 无命中 —— 故「◆」不是字符。
"""
import argparse
import os
import sys

NEEDLES = {
    "U+25C6 (dot)": b"\xe2\x97\x86",
    "U+2666 (diamond-suit)": b"\xe2\x99\xa6",
    "U+2756 (black-diamond-minus-x)": b"\xe2\x9d\x96",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--unpack", default=r"E:\APPDetector\output\.inspect\unpack")
    ap.add_argument("--max-mb", type=int, default=80)
    args = ap.parse_args()
    root = args.unpack
    if not os.path.isdir(root):
        print(f"[FAIL] unpack dir not found: {root}")
        return 2
    hits = {k: [] for k in NEEDLES}
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in filenames:
            p = os.path.join(dirpath, fn)
            try:
                if os.path.getsize(p) > args.max_mb * 1024 * 1024:
                    continue
                data = open(p, "rb").read()
            except OSError:
                continue
            for name, needle in NEEDLES.items():
                if needle in data:
                    hits[name].append(os.path.relpath(p, root))
    binary_ext = (".png", ".webp", ".jpg", ".jpeg", ".gif", ".zip", ".pag", ".jar", ".so", ".xz", ".idsig")
    rc = 0
    for name, paths in hits.items():
        print(f"== {name}: {len(paths)}")
        for rel in paths[:15]:
            print("   ", rel)

    # 判定只认「字符串载体」：dex / smali / 资源值 XML。
    # 位图、资源包、.pag 等二进制里命中 U+25C6 的字节序列是巧合，不能当字符串证据。
    def is_string_carrier(rel: str) -> bool:
        low = rel.lower()
        if low.endswith(binary_ext):
            return False
        return low.endswith((".dex", ".smali", ".xml", ".arsc", ".json", ".txt"))

    carriers = [rel for rel in hits["U+25C6 (dot)"] if is_string_carrier(rel)]
    binaries = [rel for rel in hits["U+25C6 (dot)"] if not is_string_carrier(rel)]
    print(f"-- 字符串载体命中 {len(carriers)} 个；二进制文件命中 {len(binaries)} 个（字节巧合）")
    if carriers:
        print("[FAIL] U+25C6 出现在字符串载体里 -> 该符号可能是字符，需重判")
        rc = 1
    else:
        print("[PASS] 字符串载体（dex/smali/资源 XML）里无 U+25C6 -> 「◆」不是字符，是图形")
    return rc


if __name__ == "__main__":
    sys.exit(main())
