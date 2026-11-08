#!/usr/bin/env python3
"""apk-integrity — 判断 APK 是否有「孤儿数据」（渐进构建留下的旧条目）。

背景（2026-11-08 技术总监实测）：
  33,980 KB 的 app-debug.apk：中央目录 556 条、压缩合计 13.1 MB、文件却 33.2 MB；
  local header 892 个 vs 中央目录 556 条 → 336 条孤儿数据 ≈ 21.6 MB。
  删掉旧 APK 重建：13,790,086 B，556 条目，压缩合计 13.1 MB —— 这才是真值。

判据（不靠肉眼）：
  1. local header 数（PK\\x03\\x04 扫描）应等于中央目录条目数；
  2. 文件大小 ≈ 本地头(30B×n) + 数据(压缩合计) + 中央目录 + EOCD 尾；
  3. 差额（未归属字节）> --tolerance（默认 1 MB）即判 FAIL：说明读到的是脏产物。

用法：
  python tools/device/apk-integrity.py app/android/app/build/outputs/apk/debug/app-debug.apk
  python tools/device/apk-integrity.py --json out.json <apk>

退出码：0 = 干净；1 = 有孤儿/账不平；2 = 参数错误或不是 ZIP。
"""

from __future__ import annotations

import argparse
import json
import os
import struct
import sys

EOCD_SIG = b"PK\x05\x06"
CDIR_SIG = b"PK\x01\x02"
LOCAL_SIG = b"PK\x03\x04"
EOCD_MIN = 22


def find_eocd(data: bytes) -> int:
    # EOCD 在尾部，最多 64KB 注释；从后往前找
    limit = max(0, len(data) - (EOCD_MIN + 65535))
    idx = data.rfind(EOCD_SIG, limit)
    return idx


def analyze(path: str) -> dict:
    size = os.path.getsize(path)
    with open(path, "rb") as fh:
        data = fh.read()

    eocd = find_eocd(data)
    if eocd < 0:
        return {"path": path, "ok": False, "reason": "EOCD not found (not a zip/apk)"}

    (entries, cd_size, cd_offset, comment_len) = struct.unpack_from("<HHII", data, eocd + 10)
    # 上面解的是 EOCD 偏移 10 起：总条目(H) 总条目(H) 中央目录大小(I) 中央目录偏移(I)
    # 重新精确解析：offset 8 是「本条总条目数」，10 是「中央目录条目数」
    total_entries = struct.unpack_from("<H", data, eocd + 10)[0]
    cd_size = struct.unpack_from("<I", data, eocd + 12)[0]
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]

    # 遍历中央目录，累加压缩后大小与本地头数量
    pos = cd_offset
    comp_sum = 0
    cd_entries = 0
    cd_end = cd_offset + cd_size
    while pos + 46 <= min(cd_end, len(data)) and data[pos : pos + 4] == CDIR_SIG:
        comp = struct.unpack_from("<I", data, pos + 20)[0]
        name_len = struct.unpack_from("<H", data, pos + 28)[0]
        extra_len = struct.unpack_from("<H", data, pos + 30)[0]
        cmt_len = struct.unpack_from("<H", data, pos + 32)[0]
        comp_sum += comp
        cd_entries += 1
        pos += 46 + name_len + extra_len + cmt_len

    local_count = data.count(LOCAL_SIG)
    accounted = local_count * 30 + comp_sum + cd_size + EOCD_MIN + comment_len
    orphan_bytes = size - accounted
    orphan_entries = local_count - cd_entries

    return {
        "path": path,
        "size": size,
        "size_mb": round(size / 1048576, 2),
        "central_entries": cd_entries,
        "eocd_entries": total_entries,
        "local_headers": local_count,
        "orphan_entries": orphan_entries,
        "compressed_sum_mb": round(comp_sum / 1048576, 2),
        "central_dir_mb": round(cd_size / 1048576, 2),
        "accounted": accounted,
        "orphan_bytes": orphan_bytes,
        "orphan_mb": round(orphan_bytes / 1048576, 2),
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Detect orphan data in a progressively-built APK")
    ap.add_argument("apk")
    ap.add_argument("--tolerance", type=int, default=1024 * 1024, help="unaccounted bytes allowed (default 1 MB)")
    ap.add_argument("--json")
    args = ap.parse_args(argv)

    if not os.path.isfile(args.apk):
        print(f"ERROR: not a file: {args.apk}", file=sys.stderr)
        return 2

    r = analyze(args.apk)
    if "reason" in r and not r.get("central_entries"):
        print(f"FAIL {args.apk}: {r['reason']}")
        return 2

    fails = []
    if r["orphan_entries"] != 0:
        fails.append(f"orphan local headers: {r['orphan_entries']} (local {r['local_headers']} vs central {r['central_entries']})")
    if r["orphan_bytes"] > args.tolerance:
        fails.append(f"unaccounted bytes: {r['orphan_bytes']} ({r['orphan_mb']} MB) > tolerance {args.tolerance}")

    print(
        "APK {path}\n"
        "  size       = {size} B ({size_mb} MB)\n"
        "  entries    = central {central_entries} / eocd {eocd_entries} / local {local_headers}\n"
        "  compressed = {compressed_sum_mb} MB ; central dir = {central_dir_mb} MB\n"
        "  accounted  = {accounted} B ; orphan = {orphan_bytes} B ({orphan_mb} MB)".format(**r)
    )

    if fails:
        for f in fails:
            print(f"FAIL {f}")
        print("VERDICT: DIRTY APK — delete old artifact and rebuild before judging size")
        rc = 1
    else:
        print("VERDICT: CLEAN — file accounting matches entries (safe to judge size)")
        rc = 0

    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump({**r, "verdict": "dirty" if fails else "clean", "fails": fails}, fh, ensure_ascii=False, indent=2)
        print(f"JSON: {args.json}")
    return rc


if __name__ == "__main__":
    sys.exit(main())
