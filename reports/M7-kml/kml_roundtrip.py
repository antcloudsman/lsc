#!/usr/bin/env python3
"""KML 往返比对（LC 审核验，B 轮判据①）

比对规则（2026-11-08 总监裁定后）：
- lon / lat 必须逐点相等，容差 1e-6 度；
- alt：两边都写了才比（容差 1e-6）；一边有一边没有 => 记为 alt 不一致；
- 点数、名称必须一一对应。

用法：python kml_roundtrip.py round1.kml round2.kml
"""
import sys
import xml.etree.ElementTree as ET

KML = "{http://www.opengis.net/kml/2.2}"
TOL = 1e-6


def placemarks(path):
    root = ET.parse(path).getroot()
    out = []
    for pm in root.iter(KML + "Placemark"):
        name = (pm.findtext(KML + "name") or "").strip()
        node = pm.find(".//" + KML + "coordinates")
        raw = (node.text or "").strip() if node is not None else ""
        parts = [p for p in raw.replace(" ", "").split(",") if p != ""]
        if not parts:
            continue
        out.append({"name": name, "vals": [float(x) for x in parts], "raw": raw})
    return out


def main(a_path, b_path):
    a, b = placemarks(a_path), placemarks(b_path)
    print("placemarks: A=%d B=%d" % (len(a), len(b)))
    if len(a) != len(b):
        print("FAIL 点数不一致")
        return 1

    a_sorted = sorted(a, key=lambda x: (x["name"], x["raw"]))
    b_sorted = sorted(b, key=lambda x: (x["name"], x["raw"]))

    max_ll = 0.0
    max_alt = 0.0
    n_ll = 0
    n_alt = 0
    alt_mismatch = 0
    name_mismatch = 0

    for pa, pb in zip(a_sorted, b_sorted):
        if pa["name"] != pb["name"]:
            name_mismatch += 1
        va, vb = pa["vals"], pb["vals"]
        # lon, lat 始终存在
        for i in (0, 1):
            max_ll = max(max_ll, abs(va[i] - vb[i]))
            n_ll += 1
        has_alt_a = len(va) >= 3
        has_alt_b = len(vb) >= 3
        if has_alt_a != has_alt_b:
            alt_mismatch += 1
        elif has_alt_a:
            max_alt = max(max_alt, abs(va[2] - vb[2]))
            n_alt += 1

    print("lon/lat 比较值 = %d   max_abs_diff = %.3e  (容差 %.0e)  => %s"
          % (n_ll, max_ll, TOL, "PASS" if max_ll <= TOL else "FAIL"))
    print("alt   比较值 = %d   max_abs_diff = %.3e  alt 有无不一致 = %d => %s"
          % (n_alt, max_alt, alt_mismatch,
             "PASS" if (alt_mismatch == 0 and max_alt <= TOL) else "FAIL"))
    print("名称不一致 = %d" % name_mismatch)
    ok = max_ll <= TOL and alt_mismatch == 0 and max_alt <= TOL and name_mismatch == 0
    print("RESULT:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
