"""读数公式边界测试：face = floor(((h-off+360)%360)/interval)，back = face((h+180)%360)。

依据：MECHANICS.md:149-161（DEX `CompassReadTypeEnum.getFaceText/getBackText`）。
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O


class NormalizeTest(unittest.TestCase):
    def test_known_values(self):
        cases = [
            (0.0, 0.0), (360.0, 0.0), (720.5, 0.5), (-0.01, 359.99),
            (-360.0, 0.0), (-720.0, 0.0), (359.99, 359.99), (1080.25, 0.25),
        ]
        for raw, want in cases:
            self.assertAlmostEqual(O.normalize(raw), want, places=9, msg=f"h={raw}")

    def test_result_in_range(self):
        for h in O.CORPUS:
            v = O.normalize(h)
            self.assertGreaterEqual(v, 0.0)
            self.assertLess(v, 360.0)


class FaceFormulaTest(unittest.TestCase):
    def test_24_mountain_boundaries(self):
        """地盘 24 山：interval 15 / offset 7.5（DEX TYPE_4）。"""
        ring = O.RING_BY_TYPE[4]
        cases = [
            (0.0, "子"), (3.0, "子"), (7.499, "子"), (7.5, "癸"), (7.501, "癸"),
            (14.999, "癸"), (15.0, "癸"), (22.5, "丑"), (352.499, "壬"),
            (352.5, "子"), (359.99, "子"), (360.0, "子"), (367.5, "癸"),
        ]
        for h, want in cases:
            got = O.dex_text(h, ring, O.DEX_MOUNTAIN_ORDER)
            self.assertEqual(got, want, f"h={h}")

    def test_boundary_is_lower_inclusive(self):
        """每个圈层：h=offset+interval*k 属于第 k 格；再少一点属于第 k-1 格。"""
        eps = 1e-4
        for ring in O.RINGS:
            texts = list(range(O.ring_count(ring)))
            for k in (0, 1, 2, 3, 7, 11):
                kk = k % O.ring_count(ring)
                lower = O.ring_lower(ring, kk)
                self.assertEqual(
                    O.dex_index(lower, ring["offset"], ring["interval"]), kk,
                    f"TYPE_{ring['type']} k={kk} lower={lower}")
                prev = (kk - 1) % O.ring_count(ring)
                self.assertEqual(
                    O.dex_index(lower - eps, ring["offset"], ring["interval"]), prev,
                    f"TYPE_{ring['type']} k={kk} lower-eps")

    def test_index_never_out_of_range(self):
        for ring in O.RINGS:
            count = O.ring_count(ring)
            for h in O.CORPUS:
                idx = O.dex_index(h, ring["offset"], ring["interval"])
                self.assertGreaterEqual(idx, 0, f"TYPE_{ring['type']} h={h}")
                self.assertLess(idx, count, f"TYPE_{ring['type']} h={h} idx={idx}")

    def test_359_99_is_last_division(self):
        for ring in O.RINGS:
            self.assertEqual(
                O.dex_index(359.99, ring["offset"], ring["interval"]),
                O.ring_count(ring) - 1, f"TYPE_{ring['type']}")

    def test_zero_for_zero_offset(self):
        for ring in O.RINGS:
            want = 0 if ring["offset"] == 0.0 else O.ring_count(ring) - 1
            self.assertEqual(
                O.dex_index(0.0, ring["offset"], ring["interval"]), want,
                f"TYPE_{ring['type']} offset={ring['offset']}")


class BackFormulaTest(unittest.TestCase):
    def test_back_180_on_24_mountains(self):
        ring = O.RING_BY_TYPE[4]
        cases = [(0.0, "午"), (90.0, "酉"), (180.0, "子"), (270.0, "卯"),
                 (45.0, "坤"), (359.99, "午")]
        for h, want in cases:
            got = O.dex_back_text(h, ring, O.DEX_MOUNTAIN_ORDER)
            self.assertEqual(got, want, f"h={h}")

    def test_back_equals_sitting_index(self):
        """back 的文本 == MOUNTAINS[(m+12)%24]（+180° 与 +12 格等价）。

        注意 DEX 名单从 癸 起，索引 i 对应重建顺序的 (i+1) 位。
        """
        ring = O.RING_BY_TYPE[4]
        for h in O.CORPUS:
            m = O.plate_index(h, "earth")
            back_name = O.dex_text(O.normalize(h + 180.0), ring, O.DEX_MOUNTAIN_ORDER)
            self.assertEqual(back_name, O.MOUNTAINS[(m + 12) % 24], f"h={h}")

    def test_back_wraps_at_360(self):
        ring = O.RING_BY_TYPE[4]
        for h in (0.0, 0.001, 359.99, 359.999):
            self.assertEqual(
                O.dex_text(h, ring, O.DEX_MOUNTAIN_ORDER),
                O.dex_text(O.normalize(h + 360.0), ring, O.DEX_MOUNTAIN_ORDER))


class SpecialRingsTest(unittest.TestCase):
    def test_64_hexagrams_exact_division(self):
        self.assertEqual(64 * 5.625, 360.0)  # 5.625 = 45/8，二进制可精确表示
        ring = O.RING_BY_TYPE[11]
        self.assertEqual(O.ring_count(ring), 64)
        for h, want in [(0.0, 0), (5.624, 0), (5.625, 1), (359.99, 63), (360.0, 0)]:
            self.assertEqual(O.dex_index(h, ring["offset"], ring["interval"]), want, f"h={h}")

    def test_60_dragons_offset_1_5(self):
        """透地平分六十龙：offset 1.5，不是 0（MECHANICS.md:160-161）。"""
        ring = O.RING_BY_TYPE[7]
        self.assertEqual(ring["interval"], 6.0)
        self.assertEqual(ring["offset"], 1.5)
        cases = [(0.0, 59), (1.499, 59), (1.5, 0), (7.499, 0), (7.5, 1), (359.99, 59)]
        for h, want in cases:
            self.assertEqual(O.dex_index(h, ring["offset"], ring["interval"]), want, f"h={h}")
        self.assertEqual(O.JIAZI[59], "癸亥")

    def test_120_fenjin_offset_1_5(self):
        ring = O.RING_BY_TYPE[6]
        self.assertEqual(ring["interval"], 3.0)
        self.assertEqual(ring["offset"], 1.5)
        for h, want in [(0.0, 119), (1.499, 119), (1.5, 0), (4.5, 1)]:
            self.assertEqual(O.dex_index(h, ring["offset"], ring["interval"]), want, f"h={h}")

    def test_72_dragons_offset_2_5(self):
        ring = O.RING_BY_TYPE[5]
        self.assertEqual(ring["interval"], 5.0)
        self.assertEqual(ring["offset"], 2.5)
        for h, want in [(0.0, 71), (2.499, 71), (2.5, 0), (7.5, 1)]:
            self.assertEqual(O.dex_index(h, ring["offset"], ring["interval"]), want, f"h={h}")

    def test_bagua_45_degrees(self):
        self.assertEqual(O.ring_count(O.RING_BY_TYPE[1]), 8)
        houtian = [O.bagua_name(h, "houtian") for h in (0, 45, 90, 135, 180, 225, 270, 315)]
        xiantian = [O.bagua_name(h, "xiantian") for h in (0, 45, 90, 135, 180, 225, 270, 315)]
        self.assertEqual(houtian, ["坎", "艮", "震", "巽", "离", "坤", "兑", "乾"])
        self.assertEqual(xiantian, ["坤", "震", "离", "兑", "乾", "巽", "坎", "艮"])

    def test_ring_intervals_match_mechanics(self):
        """14 圈层的 (interval, offset) 必须与 mechanics.json / MECHANICS.md 一致。"""
        expected = {
            "64 hexagrams degrees each": 5.625,
            "bagua degrees each": 45.0,
            "fenjin degrees each": 3.0,
            "72 dragons degrees each": 5.0,
            "60 dragons degrees each": 6.0,
            "degrees per mountain": 15.0,
        }
        values = {v["name"]: v["value"] for v in O.load_mechanics()}
        for name, want in expected.items():
            self.assertEqual(values[name], want, name)


if __name__ == "__main__":
    unittest.main(verbosity=2)
