#!/usr/bin/env python3
"""KML 逐点字段命中统计（LC 审核验，B-1 复验）

按产品 v1.3 口径统计：
- 逐点必须五项：lc:id / lc:type / lc:sit / lc:heading / lc:created（期望命中数 = 点数）
- lc:elev 按值有无：收藏点无海拔列 => 本轮期望命中数 = 0
- coordinates：无海拔点必须是 2 项、且不得以 ",0" 结尾

用法：python kml_keys.py a.kml [b.kml ...]
"""
import sys
import xml.etree.ElementTree as ET

K = "{http://www.opengis.net/kml/2.2}"
MUST = ["lc:id", "lc:type", "lc:sit", "lc:heading", "lc:created"]
OPT = "lc:elev"


def stats(path):
    root = ET.parse(path).getroot()
    pms = list(root.iter(K + "Placemark"))
    hits = {k: 0 for k in MUST + [OPT]}
    coords = []
    for pm in pms:
        for ed in pm.findall(K + "ExtendedData"):
            for d in ed.iter(K + "Data"):
                name = d.get("name")
                if name in hits:
                    hits[name] += 1
        for c in pm.iter(K + "coordinates"):
            if c.text:
                coords.append(c.text.strip().replace(" ", ""))
    return len(pms), hits, coords


def main(paths):
    rc = 0
    for p in paths:
        n, hits, coords = stats(p)
        three = sum(1 for c in coords if len([x for x in c.split(",") if x]) == 3)
        two = sum(1 for c in coords if len([x for x in c.split(",") if x]) == 2)
        zero_tail = sum(1 for c in coords if c.endswith(",0"))
        print("== %s" % p)
        print("   placemarks = %d" % n)
        print("   五项逐点命中 = %s" % {k: hits[k] for k in MUST})
        print("   lc:elev 命中 = %d（本轮期望 0）" % hits[OPT])
        print("   coordinates 2 项=%d / 3 项=%d / 以 ,0 结尾=%d（期望 0）"
              % (two, three, zero_tail))

        miss = [k for k in MUST if hits[k] != n]
        if miss:
            print("   FAIL 五项未逐点命中: %s" % miss)
            rc = 1
        if hits[OPT] > n:
            print("   FAIL lc:elev 命中数超过点数")
            rc = 1
        if zero_tail != 0:
            print("   FAIL 存在 ,0 结尾（无海拔点不得补 alt）")
            rc = 1
    return rc


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
