"""圈层几何测试：14 圈层的格数、格中心、边界推进、与 MECHANICS.md 表格逐行对齐。"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O


def parse_mechanics_ring_table():
    """从 MECHANICS.md 的 14 圈层表解析 (type, name, interval, offset)。

    行格式：`| TYPE_4 | 地盘正针二十四山 | 15.0 | 7.5 | true |`（MECHANICS.md:88-103）。
    """
    text = O.ROOT.joinpath("app", "MECHANICS.md").read_text(encoding="utf-8")
    rows = {}
    for line in text.splitlines():
        m = re.match(
            r"\|\s*TYPE_(\d+)\s*\|\s*([^|]+?)\s*\|\s*([\d.]+)\s*\|\s*([\d.]+)\s*\|",
            line)
        if m:
            rows[int(m.group(1))] = (m.group(2).strip(), float(m.group(3)), float(m.group(4)))
    return rows


class RingTableTest(unittest.TestCase):
    def test_all_14_rings_present(self):
        rows = parse_mechanics_ring_table()
        self.assertEqual(sorted(rows), list(range(1, 15)), "MECHANICS.md 圈层表应含 TYPE_1..TYPE_14")

    def test_oracle_matches_mechanics_md(self):
        rows = parse_mechanics_ring_table()
        for ring in O.RINGS:
            name, interval, offset = rows[ring["type"]]
            self.assertEqual(ring["name"], name, f"TYPE_{ring['type']} 名称")
            self.assertEqual(ring["interval"], interval, f"TYPE_{ring['type']} interval")
            self.assertEqual(ring["offset"], offset, f"TYPE_{ring['type']} offset")
            self.assertEqual(O.ring_count(ring) * interval, 360.0)

    def test_counts(self):
        counts = {O.ring_count(r) for r in O.RINGS}
        self.assertEqual(counts, {8, 24, 60, 64, 72, 120})


class RingGeometryTest(unittest.TestCase):
    def test_center_maps_to_its_division(self):
        for ring in O.RINGS:
            count = O.ring_count(ring)
            for k in range(count):
                center = O.ring_center(ring, k)
                self.assertEqual(
                    O.dex_index(center, ring["offset"], ring["interval"]), k,
                    f"TYPE_{ring['type']} k={k} center={center}")

    def test_center_plus_minus_quarter_stays(self):
        for ring in O.RINGS:
            count = O.ring_count(ring)
            for k in (0, 1, count // 2, count - 2, count - 1):
                center = O.ring_center(ring, k)
                delta = ring["interval"] * 0.49
                for h in (center - delta, center + delta):
                    self.assertEqual(
                        O.dex_index(h, ring["offset"], ring["interval"]), k,
                        f"TYPE_{ring['type']} k={k} h={h}")

    def test_boundaries_advance_one_division(self):
        for ring in O.RINGS:
            count = O.ring_count(ring)
            for k in range(0, count, max(1, count // 17)):
                lower = O.ring_lower(ring, k)
                self.assertEqual(
                    O.dex_index(lower, ring["offset"], ring["interval"]), k % count,
                    f"TYPE_{ring['type']} boundary k={k}")
                self.assertEqual(
                    O.dex_index(lower - 1e-4, ring["offset"], ring["interval"]),
                    (k - 1) % count, f"TYPE_{ring['type']} just-before k={k}")

    def test_ring_intervals_exact_binary(self):
        """5.625/3/5/6/15/45 都是可精确表示的二进制小数，不应出现浮点漂移。"""
        for ring in O.RINGS:
            self.assertEqual(ring["interval"] * O.ring_count(ring), 360.0,
                             f"TYPE_{ring['type']}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
