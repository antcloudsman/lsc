"""B2 换表 golden：四个原版向量（当前教材实现应失败，B2 落地后转红再删 xfail）。

向量来源：LC逆向 `8368fbd` 换表手册 / `app/mechanics.json` `rings[].labels`
（780 条 `confirmed_dex`，逐条 code-unit 地址在 `.inspect/derived/ring_label_addrs.json`）：

| 位置 | 表 | 索引 | 原版文字 |
|---|---|---|---|
| 60 龙 0° | TYPE_7 | 59 | 壬子 庚子 |
| 60 龙 170° | TYPE_7 | 28 | 丙午 甲午 |
| 72 龙 0° | TYPE_5 | 71 | 戊子 |
| 120 分金 0° | TYPE_6 | 119 | 甲山 戊子 |

当前两端仍用 `JIAZI[]` / `wangxiang()` / `borrow_branch()` 推导（教材值），所以
`test_b2_vectors_use_dex_labels` 现在 expected failure；客户端 B2 换表后它会变成
unexpected success（红），届时删掉 `@expectedFailure` 即转正常回归。
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O
import source_parse as SP

VECTORS = {
    "60龙 0°": (7, 59, "壬子 庚子"),
    "60龙 170°": (7, 28, "丙午 甲午"),
    "72龙 0°": (5, 71, "戊子"),
    "120分金 0°": (6, 119, "甲山 戊子"),
}


class B2GoldenDataTest(unittest.TestCase):
    def test_vectors_from_mechanics_json(self):
        """四向量必须与 `rings[].labels` 逐字一致（数据侧，始终绿）。"""
        for name, (ring_type, index, want) in VECTORS.items():
            self.assertEqual(O.dex_labels(ring_type, index), want, name)


class B2GoldenConformanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main_rs = SP.read_text(O.MAIN_RS)

    @unittest.expectedFailure
    def test_b2_vectors_use_dex_labels(self):
        """B2 换表前：四处原版文字都还未出现在读/画里 → 预期失败。"""
        r0 = SP.rust_reading(self.main_rs, 0.0)
        r170 = SP.rust_reading(self.main_rs, 170.0)

        # 60 龙：读数文字
        self.assertIn(O.dex_labels(7, 59), O.JIAZI[r0["d60"]], "60龙 0° 应含「壬子 庚子」")
        self.assertIn(O.dex_labels(7, 28), O.JIAZI[r170["d60"]], "60龙 170° 应含「丙午 甲午」")

        # 72 龙：旺相名
        got72 = O.wangxiang(O.borrow_branch(r0["m"]), r0["d72"])
        self.assertIn(O.dex_labels(5, 71), got72, "72龙 0° 应含「戊子」")

        # 120 分金：山名 + 干支
        got120 = O.MOUNTAINS[r0["m"]] + "山 " + O.wangxiang(O.borrow_branch(r0["m"]), r0["fs"])
        self.assertIn(O.dex_labels(6, 119), got120, "120分金 0° 应含「甲山 戊子」")


if __name__ == "__main__":
    unittest.main(verbosity=2)
