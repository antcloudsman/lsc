"""三盘（地盘/人盘/天盘）与正兼向测试。

盘面模型（与两端绘制一致：标签 m 画在 m*15+offset，顶部固定指针对准航向 h）：
  m = floor((h - offset + 7.5) / 15) mod 24
证据：两端绘制代码（main.rs `draw_plate`、CompassView.java `onDraw` 的三盘循环）
把三盘分别画在 0 / −7.5 / +7.5；知识库条目（CompassData.KNOWLEDGE_BODIES）
说明三盘相差 ±7.5°。
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O

PLATE_KEYS = ("earth", "human", "heaven")

# 关键航向的三盘读数（由 oracle 公式计算，用于锁定边界语义）。
EXPECTED = {
    0.0: ("子", "癸", "子"),
    3.0: ("子", "癸", "子"),
    7.5: ("癸", "癸", "子"),
    10.0: ("癸", "癸", "子"),
    15.0: ("癸", "丑", "癸"),
    22.5: ("丑", "丑", "癸"),
    352.5: ("子", "子", "壬"),
    359.99: ("子", "子", "壬"),
}


class PlateTableTest(unittest.TestCase):
    def test_three_plates_key_headings(self):
        for h, names in EXPECTED.items():
            got = tuple(O.plate_name(h, p) for p in PLATE_KEYS)
            self.assertEqual(got, names, f"h={h}")

    def test_exactly_two_distinct_on_any_heading(self):
        """人盘 = 地盘+1 格、天盘 = 地盘-1 格 → 三盘恒有且仅有两个不同读数。

        设 h = 15q + r：天盘 = q，人盘 = q+1，地盘 = q 或 q+1。
        """
        for h in O.CORPUS:
            names = {O.plate_name(h, p) for p in PLATE_KEYS}
            self.assertEqual(len(names), 2, f"h={h} names={names}")
            self.assertEqual(O.plate_index(h, "human"), (O.plate_index(h, "heaven") + 1) % 24,
                             f"h={h}")

    def test_earth_and_human_relation(self):
        """人盘恒为天盘的下一格，地盘介于两者之间（r<7.5 取天盘，r>=7.5 取人盘）。"""
        for h in O.CORPUS:
            human = O.plate_index(h, "human")
            heaven = O.plate_index(h, "heaven")
            earth = O.plate_index(h, "earth")
            self.assertEqual(human, (heaven + 1) % 24, f"h={h}")
            self.assertIn(earth, (heaven, human), f"h={h}")

    def test_plate_offsets_from_mechanics(self):
        values = {v["name"]: v["value"] for v in O.load_mechanics()}
        self.assertEqual(values["earth plate (地盘正针) offset"], 0)
        self.assertEqual(values["human plate (人盘中针) offset"], -7.5)
        self.assertEqual(values["heaven plate (天盘缝针) offset"], 7.5)
        self.assertEqual(O.PLATES, {"earth": 0.0, "human": -7.5, "heaven": 7.5})


class DexListEquivalenceTest(unittest.TestCase):
    """DEX 的「offset + 文本名单」与「盘面旋转」两种写法必须等价。

    - TYPE_4（offset 7.5，名单从 癸 起）≡ 地盘；
    - TYPE_8（offset 0.0，名单从 癸 起）≡ 人盘；
    - TYPE_9（offset 0.0，名单从 子 起）≡ 天盘。
      注：TYPE_9 的文本名单顺序 MECHANICS.md 未导出，此处按「天盘 +7.5°」的
      知识库方位反推，属推定口径（DEX offset=0.0 已知）。
    """

    def test_type4_with_dex_order_is_earth(self):
        ring = O.RING_BY_TYPE[4]
        for h in O.CORPUS:
            self.assertEqual(O.dex_text(h, ring, O.DEX_MOUNTAIN_ORDER),
                             O.plate_name(h, "earth"), f"h={h}")

    def test_type8_with_dex_order_is_human(self):
        ring = O.RING_BY_TYPE[8]
        for h in O.CORPUS:
            self.assertEqual(O.dex_text(h, ring, O.DEX_MOUNTAIN_ORDER),
                             O.plate_name(h, "human"), f"h={h}")

    def test_type9_with_zi_order_is_heaven(self):
        ring = O.RING_BY_TYPE[9]
        for h in O.CORPUS:
            self.assertEqual(O.dex_text(h, ring, O.MOUNTAINS),
                             O.plate_name(h, "heaven"), f"h={h}")


class ZhengJianTest(unittest.TestCase):
    """正向 = 每山中央 9°（|offset| <= 4.5），兼向 = 其余（3°..7.5° 两侧）。"""

    def test_center_is_zheng(self):
        for m in range(24):
            self.assertEqual(O.earth_reading(m * 15.0)["jian"], "正向", f"m={m}")

    def test_boundary_4_5_inclusive(self):
        self.assertEqual(O.earth_reading(4.5)["jian"], "正向")       # 子山 +4.5
        self.assertAlmostEqual(O.earth_reading(4.5)["off"], 4.5, places=6)
        self.assertEqual(O.earth_reading(355.5)["jian"], "正向")     # 子山 -4.5
        self.assertAlmostEqual(O.earth_reading(355.5)["off"], -4.5, places=6)

    def test_just_outside_4_5_is_jian(self):
        r = O.earth_reading(4.501)
        self.assertEqual(r["jian"], "兼向癸")
        r = O.earth_reading(4.6)
        self.assertEqual(r["jian"], "兼向癸")
        r = O.earth_reading(355.499)
        self.assertEqual(r["jian"], "兼向壬")
        r = O.earth_reading(355.4)
        self.assertEqual(r["jian"], "兼向壬")

    def test_jian_range_is_outer_3_degrees(self):
        """兼向区 = 山界两侧各 3°（九度外 = 15-9=6 → 每侧 3°）。"""
        values = {v["name"]: v["value"] for v in O.load_mechanics()}
        self.assertEqual(values["zheng xiang range (正向)"], 9)
        self.assertEqual(values["jian xiang range (兼向)"], 3)
        # 子山正向范围 [−4.5, +4.5]，两侧兼向 [4.5,7.5] / [−7.5,−4.5]。
        self.assertEqual(O.earth_reading(4.5)["jian"], "正向")
        self.assertEqual(O.earth_reading(7.49)["jian"], "兼向癸")
        self.assertEqual(O.earth_reading(352.51)["jian"], "兼向壬")


class FenjinDragonSlotTest(unittest.TestCase):
    def test_fenjin_slots_5_per_mountain(self):
        for m in range(24):
            center = m * 15.0
            slots = []
            for s in range(5):
                h = center - 6.0 + 3.0 * s  # 5 格中心：-6,-3,0,3,6
                slots.append(O.earth_reading(h)["fs"])
            self.assertEqual(slots, [0, 1, 2, 3, 4], f"m={m}")

    def test_dragon72_slots_3_per_mountain(self):
        for m in range(24):
            center = m * 15.0
            slots = [O.earth_reading(center - 5.0 + 5.0 * s)["d72"] for s in range(3)]
            self.assertEqual(slots, [0, 1, 2], f"m={m}")

    def test_60_dragons_60_divisions(self):
        seen = {O.earth_reading(h)["d60"] for h in [i * 6.0 for i in range(60)]}
        self.assertEqual(seen, set(range(60)))
        self.assertEqual(O.earth_reading(0.0)["d60name"], "癸亥")
        self.assertEqual(O.earth_reading(4.5)["d60name"], "甲子")


if __name__ == "__main__":
    unittest.main(verbosity=2)
