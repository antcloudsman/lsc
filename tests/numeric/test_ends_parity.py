"""两端实现对拍：

- Java：用本机 JDK17 直接编译并运行 `CompassData.java`（Android 端真实代码，只读）；
- Rust：从 `app/src/main.rs` 解析 `reading_for` 的表达式并求值（源码驱动镜像）。

覆盖：24 山、offset、分金格、72 龙格、60 龙、坐向、正兼向、0°/359.99°/负角/跨圈。
"""
import csv
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O
import source_parse as SP

JAVA_CANDIDATES = [
    os.environ.get("JAVA_HOME"),
    r"C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9",
]


def find_jdk():
    for home in JAVA_CANDIDATES:
        if not home:
            continue
        javac = os.path.join(home, "bin", "javac.exe" if os.name == "nt" else "javac")
        java = os.path.join(home, "bin", "java.exe" if os.name == "nt" else "java")
        if os.path.isfile(javac) and os.path.isfile(java):
            return javac, java
    for exe in ("javac", "java"):
        if shutil.which(exe):
            return shutil.which("javac"), shutil.which("java")
    return None, None


class JavaRealCodeTest(unittest.TestCase):
    """真实 Java 实现（CompassData.read）对拍 oracle。"""

    @classmethod
    def setUpClass(cls):
        javac, java = find_jdk()
        if not javac:
            raise unittest.SkipTest("未找到 JDK（javac），跳过 Java 真机代码对拍")
        cls.tmp = tempfile.mkdtemp(prefix="lc_numeric_java_")
        pkg = os.path.join(cls.tmp, "src", "com", "luopan", "fengshui")
        os.makedirs(pkg, exist_ok=True)
        shutil.copy(O.COMPASS_DATA_JAVA, os.path.join(pkg, "CompassData.java"))
        shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                  "java", "CompassProbe.java"),
                    os.path.join(pkg, "CompassProbe.java"))
        cls.headings = os.path.join(cls.tmp, "headings.txt")
        with open(cls.headings, "w", encoding="utf-8") as f:
            for h in O.CORPUS:
                f.write(f"{h!r}\n")
        cls.out_csv = os.path.join(cls.tmp, "out.csv")
        classes = os.path.join(cls.tmp, "classes")
        files = [os.path.join(pkg, "CompassData.java"), os.path.join(pkg, "CompassProbe.java")]
        proc = subprocess.run([javac, "-encoding", "UTF-8", "-d", classes] + files,
                              capture_output=True, text=True, encoding="utf-8", errors="replace")
        if proc.returncode != 0:
            raise AssertionError(f"javac 失败:\n{proc.stdout}\n{proc.stderr}")
        run = subprocess.run([java, "-cp", classes, "com.luopan.fengshui.CompassProbe",
                              cls.headings, cls.out_csv],
                             capture_output=True, text=True, encoding="utf-8", errors="replace")
        if run.returncode != 0:
            raise AssertionError(f"CompassProbe 运行失败:\n{run.stdout}\n{run.stderr}")
        with open(cls.out_csv, encoding="utf-8") as f:
            cls.rows = {float(r["h"]): r for r in csv.DictReader(f)}
        cls.classes = classes
        cls.java = java

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(getattr(cls, "tmp", ""), ignore_errors=True)

    def test_all_corpus_rows_present(self):
        self.assertEqual(len(self.rows), len(O.CORPUS))
        for h in O.CORPUS:
            self.assertIn(float(h), self.rows)

    def test_earth_mountain_offset(self):
        for h, row in self.rows.items():
            want = O.earth_reading(h)
            self.assertEqual(int(row["m"]), want["m"], f"h={h} 地盘山")
            self.assertAlmostEqual(float(row["off"]), want["off"], places=3,
                                   msg=f"h={h} 山内偏移")

    def test_divisions(self):
        for h, row in self.rows.items():
            want = O.earth_reading(h)
            self.assertEqual(int(row["fs"]), want["fs"], f"h={h} 分金格")
            self.assertEqual(int(row["d72"]), want["d72"], f"h={h} 72龙格")
            self.assertEqual(int(row["d60"]), want["d60"], f"h={h} 60龙索引")

    def test_sitting_and_names(self):
        for h, row in self.rows.items():
            want = O.earth_reading(h)
            self.assertEqual(int(row["sit"]), want["sit"], f"h={h} 坐")
            self.assertEqual(row["fenjin"], want["fenjin"], f"h={h} 分金名")
            self.assertEqual(row["d72name"], want["d72name"], f"h={h} 72龙名")
            self.assertEqual(row["d60name"], want["d60name"], f"h={h} 60龙名")

    def test_jian_label(self):
        for h, row in self.rows.items():
            want = O.earth_reading(h)
            self.assertEqual(row["jian"], want["jian"], f"h={h} 正兼向")

    def test_back_equals_sitting(self):
        """坐向 = 向+180°：对每个语料，sitting 名 == face((h+180)%360)。"""
        ring = O.RING_BY_TYPE[4]
        for h in O.CORPUS:
            row = self.rows[float(h)]
            back = O.dex_text(O.normalize(h + 180.0), ring, O.DEX_MOUNTAIN_ORDER)
            self.assertEqual(O.MOUNTAINS[int(row["sit"])], back, f"h={h}")


class RustSourceTest(unittest.TestCase):
    """桌面 main.rs 的读数表达式求值对拍 oracle 与 Java。"""

    @classmethod
    def setUpClass(cls):
        cls.src = SP.read_text(O.MAIN_RS)

    def test_earth_mountain_offset(self):
        for h in O.CORPUS:
            got = SP.rust_reading(self.src, h)
            want = O.earth_reading(h)
            self.assertEqual(got["m"], want["m"], f"h={h} 地盘山")
            self.assertAlmostEqual(got["off"], want["off"], places=6, msg=f"h={h} 偏移")
            self.assertEqual(got["fs"], want["fs"], f"h={h} 分金格")
            self.assertEqual(got["d72"], want["d72"], f"h={h} 72龙格")
            self.assertEqual(got["d60"], want["d60"], f"h={h} 60龙索引")
            self.assertEqual(got["sit"], want["sit"], f"h={h} 坐")

    def test_jian_range_boundary(self):
        for h, want in ((4.5, "正向"), (4.501, "兼向癸"), (355.5, "正向"),
                        (355.499, "兼向壬"), (0.0, "正向")):
            got = SP.rust_reading(self.src, h)["off"]
            self.assertEqual(O.jian_label(got, O.plate_index(h, "earth")), want, f"h={h}")

    def test_matches_java_earth_readings(self):
        if not hasattr(JavaRealCodeTest, "rows"):
            self.skipTest("Java 行未就绪")
        for h in O.CORPUS:
            row = JavaRealCodeTest.rows[float(h)]
            got = SP.rust_reading(self.src, h)
            self.assertEqual(got["m"], int(row["m"]), f"h={h} 地盘山两端不一致")
            self.assertEqual(got["fs"], int(row["fs"]), f"h={h} 分金格两端不一致")
            self.assertEqual(got["d72"], int(row["d72"]), f"h={h} 72龙两端不一致")
            self.assertEqual(got["d60"], int(row["d60"]), f"h={h} 60龙两端不一致")
            self.assertEqual(got["sit"], int(row["sit"]), f"h={h} 坐向两端不一致")


if __name__ == "__main__":
    unittest.main(verbosity=2)
