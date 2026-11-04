"""轮 B 回归：BUG-001（三盘读数）。

2026-11-05 `409ad5f` 修复后，4 条 `@expectedFailure` 转为正常用例：
人盘/天盘读数按 DEX 表旋转（TYPE_8 offset 0.0 + 癸起名单；TYPE_9 offset 0.0 + 子起名单）。
B2（`662c006`）起圈层文字改走 `ring_text(type, h) -> RINGS[type].labels[i]`，
圈层不再整圈画字，「指针处标签 == 读数」不变量随之失效——文字校验移到
`test_b2_golden.py`（与 `rings[].labels` 逐条对拍）。
"""
import os
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


if __name__ == "__main__":
    unittest.main(verbosity=2)
