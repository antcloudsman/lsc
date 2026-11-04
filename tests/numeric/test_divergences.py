"""轮 B 回归：BUG-001（三盘读数）与 BUG-002（60 龙标签落位）。

2026-11-05 `409ad5f` 修复后，原先的 5 条 `@expectedFailure` 已转为正常用例：
- 人盘/天盘读数按 DEX 表旋转（TYPE_8 offset 0.0 + 癸起名单；TYPE_9 offset 0.0 + 子起名单）；
- 桌面 60 龙标签/刻度落到 (interval 6.0, offset 1.5) 的格中心/格界，读数公式不变。
若将来又出现同类偏差，按 README「已知缺陷（xfail 机制）」重新加标记。
"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O
import source_parse as SP


class PlatesRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main_activity = SP.read_text(O.MAIN_ACTIVITY_JAVA)
        cls.main_rs = SP.read_text(O.MAIN_RS)
        cls.java_exprs = SP.java_plate_exprs(cls.main_activity)
        cls.rust_hm = SP.rust_plate(cls.main_rs, 0.0, "hm")
        cls.rust_tm = SP.rust_plate(cls.main_rs, 0.0, "tm")

    def test_source_parse_ok(self):
        """解析健康检查：确保三盘用例不是因为解析失败而「假通过」。"""
        self.assertEqual(len(self.java_exprs), 2, "MainActivity 应有两处 plate(...) 三盘调用")
        for expr in self.java_exprs:
            for h in (0.0, 7.5, 22.5, 359.99):
                self.assertIn(SP.java_plate(h, expr), range(24))
        self.assertIn(self.rust_hm, range(24))
        self.assertIn(self.rust_tm, range(24))

    def test_java_human_plate_matches_spec(self):
        expr = self.java_exprs[0]  # plate(r.heading + 15f)
        mismatches = [(h, SP.java_plate(h, expr), O.plate_index(h, "human"))
                      for h in O.CORPUS if SP.java_plate(h, expr) != O.plate_index(h, "human")]
        self.assertEqual(mismatches, [], "人盘读数应为 floor((h+15)/15)%24")

    def test_java_heaven_plate_matches_spec(self):
        expr = self.java_exprs[1]  # plate(r.heading - 7.5f + 15f)
        mismatches = [(h, SP.java_plate(h, expr), O.plate_index(h, "heaven"))
                      for h in O.CORPUS if SP.java_plate(h, expr) != O.plate_index(h, "heaven")]
        self.assertEqual(mismatches, [], "天盘读数应为 floor(h/15)%24")

    def test_rust_human_plate_matches_spec(self):
        mismatches = [(h, SP.rust_plate(self.main_rs, h, "hm"), O.plate_index(h, "human"))
                      for h in O.CORPUS
                      if SP.rust_plate(self.main_rs, h, "hm") != O.plate_index(h, "human")]
        self.assertEqual(mismatches, [], "main.rs hm 应为 floor((h+15)/15)%24")

    def test_rust_heaven_plate_matches_spec(self):
        mismatches = [(h, SP.rust_plate(self.main_rs, h, "tm"), O.plate_index(h, "heaven"))
                      for h in O.CORPUS
                      if SP.rust_plate(self.main_rs, h, "tm") != O.plate_index(h, "heaven")]
        self.assertEqual(mismatches, [], "main.rs tm 应为 floor(h/15)%24")


class RingLabelAlignmentTest(unittest.TestCase):
    """「指针处标签 == 读数」不变量：分金/72 龙/60 龙全部通过（60 龙随 409ad5f 修复）。

    环上标签画在 (m, s) 对应角度，读数给出同一 (m, s)。指针处最近的标签应与读数同名。
    """

    @classmethod
    def setUpClass(cls):
        cls.main_rs = SP.read_text(O.MAIN_RS)
        cls.mrs = cls.main_rs
        cls.fenjin_expr = SP.rust_ring_label_expr(cls.main_rs, "fenjin")
        cls.d72_expr = SP.rust_ring_label_expr(cls.main_rs, "dragon72")

    def _angle(self, expr, **kw):
        return eval(expr, {"__builtins__": {}}, dict(kw)) % 360.0

    def _nearest(self, expr, candidates, h):
        def dist(kw):
            m, s = kw
            return abs(((h - self._angle(expr, m=m, s=s) + 180.0) % 360.0) - 180.0)
        return min(candidates, key=dist)

    def test_fenjin_labels_align_with_readout(self):
        cands = [(m, s) for m in range(24) for s in range(5)]
        bad = []
        for h in (0.0, 1.0, 2.0, 3.0, 4.0, 7.0, 8.0, 180.0, 359.0):
            r = SP.rust_reading(self.main_rs, h)
            nearest = self._nearest(self.fenjin_expr, cands, h)
            readout_name = O.wangxiang(O.borrow_branch(r["m"]), r["fs"])
            label_name = O.wangxiang(O.borrow_branch(nearest[0]), nearest[1])
            if readout_name != label_name:
                bad.append((h, label_name, readout_name))
        self.assertEqual(bad, [])

    def test_dragon72_labels_align_with_readout(self):
        cands = [(m, s) for m in range(24) for s in range(3)]
        bad = []
        for h in (0.0, 1.0, 2.0, 3.0, 4.0, 7.0, 8.0, 180.0, 359.0):
            r = SP.rust_reading(self.main_rs, h)
            nearest = self._nearest(self.d72_expr, cands, h)
            readout_name = O.wangxiang(O.borrow_branch(r["m"]), r["d72"])
            label_name = O.wangxiang(O.borrow_branch(nearest[0]), nearest[1])
            if readout_name != label_name:
                bad.append((h, label_name, readout_name))
        self.assertEqual(bad, [])


class Dragon60LabelRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main_rs = SP.read_text(O.MAIN_RS)
        cls.label_expr = SP.rust_ring_label_expr(cls.main_rs, "dragon60")
        cls.headings = [0.0, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0,
                        12.0, 180.0, 355.0, 359.0]

    def _label_angle(self, k):
        return eval(self.label_expr, {"__builtins__": {}}, {"k": k}) % 360.0

    def _nearest_label(self, h):
        return min(range(60), key=lambda k: abs(((h - self._label_angle(k) + 180.0) % 360.0) - 180.0))

    def test_label_expression_parsed(self):
        """标签角必须是可求值的 60 个递增值（结构检查，不锁定缺陷值）。"""
        angles = [self._label_angle(k) for k in range(60)]
        for a, b in zip(angles, angles[1:]):
            self.assertAlmostEqual((b - a) % 360.0, 6.0, places=6)
        self.assertAlmostEqual((angles[0] - angles[-1]) % 360.0, 6.0, places=6)

    def test_label_under_pointer_matches_readout(self):
        bad = []
        for h in self.headings:
            readout = O.JIAZI[SP.rust_reading(self.main_rs, h)["d60"]]
            label = O.JIAZI[self._nearest_label(h)]
            if readout != label:
                bad.append((h, label, readout))
        self.assertEqual(bad, [], "指针处标签应与读数一致（60 龙环整体偏 4.5°）")


if __name__ == "__main__":
    unittest.main(verbosity=2)
