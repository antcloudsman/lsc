"""B2 换表回归：14 圈层 780 条文字以 `rings[].labels` 为准。

来源：LC逆向 `8368fbd` 换表手册（`getTextList` 逐条地址）+ 客户端 `662c006` 落地。
校验四层：

1. mechanics.json 的四个原版向量（数据侧）；
2. 生成的 `RINGS`（Rust `main.rs` / Java `CompassData.java`）与 mechanics.json **逐条一致**（780 条）；
3. `ring_text(type, heading)` 按 `(offset, interval)` 取 `labels` 下标（源码语义）；
4. 读数面板确实调用 `ring_text(1/2/5/6/7/14, h)`（两端），60 龙等文字不再走 `JIAZI[]`。

四向量：60 龙 0°=`壬子 庚子`、170°=`丙午 甲午`、72 龙 0°=`戊子`、120 分金 0°=`甲山 戊子`。
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


def mech_rings() -> dict:
    doc = O.load_mechanics_doc()
    return {r["type"]: {"text": r["text"], "interval": r["interval"],
                        "offset": r["offset"], "labels": r["labels"]}
            for r in doc["rings"]}


class B2VectorsTest(unittest.TestCase):
    def test_vectors_from_mechanics_json(self):
        for name, (ring_type, index, want) in VECTORS.items():
            self.assertEqual(O.dex_labels(ring_type, index), want, name)

    def test_vectors_in_rust_rings(self):
        rings = SP.parse_rust_rings(SP.read_text(O.MAIN_RS))
        for name, (ring_type, index, want) in VECTORS.items():
            self.assertEqual(rings[ring_type]["labels"][index], want, f"Rust {name}")

    def test_vectors_in_java_rings(self):
        rings = SP.parse_java_rings(SP.read_text(O.COMPASS_DATA_JAVA))
        for name, (ring_type, index, want) in VECTORS.items():
            self.assertEqual(rings[ring_type]["labels"][index], want, f"Java {name}")


class B2GeneratedTablesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mech = mech_rings()
        cls.rust = SP.parse_rust_rings(SP.read_text(O.MAIN_RS))
        cls.java = SP.parse_java_rings(SP.read_text(O.COMPASS_DATA_JAVA))

    def test_rust_rings_match_mechanics(self):
        self.assertEqual(sorted(self.rust), list(range(1, 15)))
        for t, want in self.mech.items():
            got = self.rust[t]
            self.assertEqual(got["text"], want["text"], f"Rust TYPE_{t} text")
            self.assertEqual(got["interval"], want["interval"], f"Rust TYPE_{t} interval")
            self.assertEqual(got["offset"], want["offset"], f"Rust TYPE_{t} offset")
            self.assertEqual(got["labels"], want["labels"], f"Rust TYPE_{t} labels")

    def test_java_rings_match_mechanics(self):
        self.assertEqual(sorted(self.java), list(range(1, 15)))
        for t, want in self.mech.items():
            got = self.java[t]
            self.assertEqual(got["text"], want["text"], f"Java TYPE_{t} text")
            self.assertEqual(got["interval"], want["interval"], f"Java TYPE_{t} interval")
            self.assertEqual(got["offset"], want["offset"], f"Java TYPE_{t} offset")
            self.assertEqual(got["labels"], want["labels"], f"Java TYPE_{t} labels")

    def test_both_ends_identical(self):
        self.assertEqual(self.rust, self.java)

    def test_total_780_labels(self):
        self.assertEqual(sum(len(r["labels"]) for r in self.mech.values()), 780)


class B2RingTextSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main_rs = SP.read_text(O.MAIN_RS)
        cls.java = SP.read_text(O.COMPASS_DATA_JAVA)

    def test_ring_index_uses_offset_interval(self):
        """索引公式在 `ring_index` 里：(normalize(h)-offset)/interval 取整。"""
        body = SP.fn_body(self.main_rs, "ring_index")
        for token in ("ring.offset", "ring.interval", "normalize_deg", "% n"):
            self.assertIn(token, body, f"ring_index 体缺 {token!r}")
        jbody = SP.java_method_body(self.java, "ringIndex")
        for token in ("r.offset", "r.interval", "Math.floor", ".length"):
            self.assertIn(token, jbody, f"Java ringIndex 体缺 {token!r}")

    def test_ring_text_delegates_to_ring_index(self):
        body = SP.fn_body(self.main_rs, "ring_text")
        self.assertIn("labels[", body, "ring_text 未从 labels 取值")
        self.assertIn("ring_index(", body, "ring_text 未委托 ring_index")
        jbody = SP.java_method_body(self.java, "ringText")
        self.assertIn(".labels[", jbody, "Java ringText 未从 labels 取值")
        self.assertIn("ringIndex(", jbody, "Java ringText 未委托 ringIndex")
        self.assertIn("ring_text_clean", self.main_rs)
        self.assertIn("\\u{200c}", self.main_rs, "U+200C 清洗")

    def test_current_cell_center_formula(self):
        """当前格中心 = offset + (i+0.5)*interval（高亮/落位共用）。"""
        for body in (SP.fn_body(self.main_rs, "ring_center"),
                     SP.java_method_body(self.java, "ringCenter")):
            self.assertIn("0.5", body, "格中心缺 +0.5*interval")
            self.assertRegex(body, r"offset", "格中心缺 offset")

    def test_client_unit_vectors_present(self):
        for ring_type, heading, want in ((7, "0.0", "壬子 庚子"), (7, "170.0", "丙午 甲午"),
                                         (5, "0.0", "戊子"), (6, "0.0", "甲山 戊子")):
            pattern = (rf'assert_eq!\(ring_text\({ring_type}, {heading}\), '
                       rf'"{want}"')
            self.assertRegex(self.main_rs, pattern, f"main.rs 缺 TYPE_{ring_type} 向量断言")

    def test_readout_calls_ring_text(self):
        for t in (1, 2, 5, 6, 7, 14):
            self.assertIn(f"ring_text_clean({t}, rd.heading)", self.main_rs,
                          f"main.rs 读数未用 ring_text_clean({t}, ...)")
            self.assertIn(f"ringText({t}, h)", self.java,
                          f"CompassData.java 读数未用 ringText({t}, ...)")


if __name__ == "__main__":
    unittest.main(verbosity=2)
