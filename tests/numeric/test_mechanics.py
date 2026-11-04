"""mechanics.json 质量红线：19 项、全部有来源、0 项估计；并与两端常量表同源。"""
import os
import re
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O
import source_parse as SP

FORBIDDEN_SOURCE_TOKENS = ("估计", "估算", "estimate", "estimated", "unconfirmed",
                           "TODO", "待确认", "未确认")
EXPECTED_NUMBERS = {
    "mountains per circle": 24,
    "degrees per mountain": 15,
    "earth plate (地盘正针) offset": 0,
    "human plate (人盘中针) offset": -7.5,
    "heaven plate (天盘缝针) offset": 7.5,
    "120 fenjin divisions": 120,
    "fenjin degrees each": 3,
    "72 dragons (穿山七十二龙) divisions": 72,
    "72 dragons degrees each": 5,
    "60 dragons (透地平分六十龙) divisions": 60,
    "60 dragons degrees each": 6,
    "64 hexagrams degrees each": 5.625,
    "bagua degrees each": 45,
    "jian xiang range (兼向)": 3,
    "zheng xiang range (正向)": 9,
    "compass dial types": 5,
}


class MechanicsJsonTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.values = O.load_mechanics()
        cls.by_name = {v["name"]: v for v in cls.values}

    def test_exactly_19_items(self):
        self.assertEqual(len(self.values), 19)

    def test_names_unique_and_groups_known(self):
        self.assertEqual(len(self.by_name), 19)
        for v in self.values:
            self.assertIn(v["group"], {"compass", "plates", "divisions", "bagua"})
            self.assertIn("value", v)

    def test_all_have_source_and_none_estimated(self):
        for v in self.values:
            src = str(v.get("source", "")).strip()
            self.assertTrue(src, f"{v['name']} 缺 source")
            low = src.lower()
            for token in FORBIDDEN_SOURCE_TOKENS:
                self.assertNotIn(token.lower(), low, f"{v['name']} source 含估计标记: {src}")

    def test_numeric_values_match_oracle(self):
        for name, want in EXPECTED_NUMBERS.items():
            self.assertIn(name, self.by_name, name)
            self.assertEqual(self.by_name[name]["value"], want, name)

    def test_derived_counts(self):
        self.assertEqual(round(360 / self.by_name["fenjin degrees each"]["value"]), 120)
        self.assertEqual(round(360 / self.by_name["72 dragons degrees each"]["value"]), 72)
        self.assertEqual(round(360 / self.by_name["60 dragons degrees each"]["value"]), 60)
        self.assertEqual(round(360 / self.by_name["64 hexagrams degrees each"]["value"]), 64)
        self.assertEqual(round(360 / self.by_name["bagua degrees each"]["value"]), 8)
        self.assertEqual(round(360 / self.by_name["degrees per mountain"]["value"]), 24)

    def test_order_and_bagua_strings(self):
        self.assertEqual(self.by_name["24 mountains order"]["value"], "".join(O.MOUNTAINS))
        self.assertEqual(self.by_name["magnetic north mountain"]["value"], "子")
        self.assertEqual(self.by_name["bagua five elements"]["value"],
                         "乾兑金 坤艮土 震巽木 坎水 离火")


class BothEndsTableParityTest(unittest.TestCase):
    """桌面 main.rs 与 Android CompassData.java 的静态表必须逐项相同，且等于 oracle。"""

    @classmethod
    def setUpClass(cls):
        cls.rs = SP.read_text(O.MAIN_RS)
        cls.java = SP.read_text(O.COMPASS_DATA_JAVA)

    def _assert_tables(self, rust_name, java_name, oracle_list, length):
        rust = SP.rust_array(self.rs, rust_name, length)
        java = SP.java_array(self.java, java_name)
        self.assertEqual(rust, oracle_list, f"Rust {rust_name} 与 oracle 不一致")
        self.assertEqual(java, oracle_list, f"Java {java_name} 与 oracle 不一致")
        self.assertEqual(rust, java, f"两端 {rust_name}/{java_name} 不一致")

    def test_24_mountains(self):
        self._assert_tables("MOUNTAINS", "MOUNTAINS", O.MOUNTAINS, 24)

    def test_wuxing(self):
        self._assert_tables("WUXING", "WUXING", O.WUXING, 24)

    def test_yinyang(self):
        self._assert_tables("YINYANG", "YINYANG", O.YINYANG, 24)

    def test_houtian(self):
        self._assert_tables("HOUTIAN_GUA", "HOUTIAN", O.HOUTIAN, 24)

    def test_xiantian(self):
        self._assert_tables("XIANTIAN_GUA", "XIANTIAN", O.XIANTIAN, 24)

    def test_jiazi(self):
        self._assert_tables("JIAZI", "JIAZI", O.JIAZI, 60)

    def test_nayin(self):
        self._assert_tables("NAYIN", "NAYIN", O.NAYIN, 60)

    def test_ring_constants_present_in_both_ends(self):
        """64 卦 5.625°、60 龙 offset 1.5 必须两端都在（数值同源的最低要求）。"""
        for text, label in ((self.rs, "main.rs"), (self.java, "CompassData.java")):
            self.assertIn("5.625", text, label)
            self.assertIn("1.5", text, label)
        self.assertIn("HEXAGRAM64_DEG = 5.625f", self.java)
        doc = O.load_mechanics_doc()
        hx = [v for v in doc["values"] if v["name"] == "64 hexagrams degrees each"]
        self.assertEqual(len(hx), 1)
        self.assertEqual(hx[0]["value"], 5.625)


class SingleSourceTest(unittest.TestCase):
    """mechanics.json v2 是唯一数值源：values / rings / dials / tables 四段自洽。"""

    @classmethod
    def setUpClass(cls):
        cls.doc = O.load_mechanics_doc()
        cls.rs = SP.read_text(O.MAIN_RS)
        cls.java = SP.read_text(O.COMPASS_DATA_JAVA)

    def test_versioned_sections(self):
        self.assertGreaterEqual(self.doc.get("version", 0), 2)
        self.assertEqual(len(self.doc["values"]), 19)
        self.assertEqual(len(self.doc["rings"]), 14)
        self.assertEqual(len(self.doc["dials"]), 5)

    def test_rings_section_matches_oracle(self):
        by_type = {r["type"]: r for r in self.doc["rings"]}
        self.assertEqual(sorted(by_type), list(range(1, 15)))
        for ring in O.RINGS:
            item = by_type[ring["type"]]
            self.assertEqual(item["text"], ring["name"], f"TYPE_{ring['type']} text")
            self.assertEqual(item["interval"], ring["interval"], f"TYPE_{ring['type']} interval")
            self.assertEqual(item["offset"], ring["offset"], f"TYPE_{ring['type']} offset")
            self.assertTrue(str(item.get("source", "")).strip(), f"TYPE_{ring['type']} 缺 source")

    def test_rings_labels_complete(self):
        """LC逆向 118357a：14 张文字表共 780 条，全部 confirmed_dex。"""
        expected = {1: 8, 2: 8, 3: 8, 4: 24, 5: 72, 6: 120, 7: 60,
                    8: 24, 9: 24, 10: 120, 11: 64, 12: 64, 13: 64, 14: 120}
        total = 0
        for ring in self.doc["rings"]:
            labels = ring.get("labels", [])
            self.assertEqual(len(labels), expected[ring["type"]], f"TYPE_{ring['type']}")
            self.assertEqual(ring.get("labels_status"), "confirmed_dex", f"TYPE_{ring['type']}")
            total += len(labels)
        self.assertEqual(total, 780)

    def test_tables_section_matches_oracle(self):
        t = self.doc["tables"]
        self.assertEqual(t["mountains"], O.MOUNTAINS)
        self.assertEqual(t["wuxing"], O.WUXING)
        self.assertEqual(t["yinyang"], O.YINYANG)
        self.assertEqual(t["houtian"], O.HOUTIAN)
        self.assertEqual(t["xiantian"], O.XIANTIAN)
        self.assertEqual(t["jiazi"], O.JIAZI)
        self.assertEqual(t["nayin"], O.NAYIN)
        self.assertEqual(t["branches"], O.BRANCHES)
        self.assertEqual(t["yang_stems"], O.YANG_STEMS)
        self.assertEqual(t["yin_stems"], O.YIN_STEMS)
        self.assertEqual(dict(t["gua_wuxing"]), O.GUA_WUXING)

    def test_dials_match_android_compass_types(self):
        java_types = SP.java_array(self.java, "COMPASS_TYPES")
        self.assertEqual([d["text"] for d in self.doc["dials"]], java_types)

    def test_no_estimated_markers_in_values(self):
        for v in self.doc["values"]:
            low = str(v["source"]).lower()
            for token in FORBIDDEN_SOURCE_TOKENS:
                self.assertNotIn(token.lower(), low, v["name"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
