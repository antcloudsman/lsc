"""P0-1 读数可信包：`SensorMath` 的 JVM 断言（纯静态类，javac 直接跑）。

钉死总监裁决的倾角公式：`tilt = acos(cos(pitch)·cos(roll))`，
`tilt(10,10) = 14.1060°`（不是 13.9，也不是 sqrt(p²+r²)=14.1421）。
同时锁偏角管线：trueNorth=true 才加偏角、clamp ±30、normalize 回绕。
"""
import math
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import oracle as O

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


def expected_tilt(pitch_deg, roll_deg):
    c = math.cos(math.radians(pitch_deg)) * math.cos(math.radians(roll_deg))
    c = max(-1.0, min(1.0, c))
    return math.degrees(math.acos(c))


class SensorMathJvmTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not O.SENSOR_MATH_JAVA.exists():
            raise unittest.SkipTest("SensorMath.java 不在本树（P0-1 f2cf102 未合入），跳过")
        javac, java = find_jdk()
        if not javac:
            raise unittest.SkipTest("未找到 JDK（javac），跳过 SensorMath JVM 断言")
        cls.tmp = tempfile.mkdtemp(prefix="lc_sensor_math_")
        pkg = os.path.join(cls.tmp, "src", "com", "luopan", "fengshui")
        os.makedirs(pkg, exist_ok=True)
        shutil.copy(O.SENSOR_MATH_JAVA, os.path.join(pkg, "SensorMath.java"))
        shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                 "java", "SensorMathProbe.java"),
                    os.path.join(pkg, "SensorMathProbe.java"))
        classes = os.path.join(cls.tmp, "classes")
        proc = subprocess.run(
            [javac, "-encoding", "UTF-8", "-d", classes,
             os.path.join(pkg, "SensorMath.java"), os.path.join(pkg, "SensorMathProbe.java")],
            capture_output=True, text=True, encoding="utf-8", errors="replace")
        if proc.returncode != 0:
            raise AssertionError(f"javac 失败:\n{proc.stdout}\n{proc.stderr}")
        run = subprocess.run([java, "-cp", classes, "com.luopan.fengshui.SensorMathProbe"],
                             capture_output=True, text=True, encoding="utf-8", errors="replace")
        if run.returncode != 0:
            raise AssertionError(f"SensorMathProbe 运行失败:\n{run.stdout}\n{run.stderr}")
        cls.out = run.stdout.strip().splitlines()

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(getattr(cls, "tmp", ""), ignore_errors=True)

    def test_tilt_formula(self):
        line = [l for l in self.out if l.startswith("tilt ")][0].split()
        got = [float(x) for x in line[1:]]
        for (p, r), value in zip([(0, 0), (10, 0), (0, 10), (10, 10)], got):
            self.assertAlmostEqual(value, expected_tilt(p, r), places=3,
                                   msg=f"tilt({p},{r})")
        self.assertAlmostEqual(got[3], 14.106, places=3)
        self.assertGreater(abs(got[3] - 13.9), 0.1, "13.9 无出处，不许回退")

    def test_clamp_declination(self):
        line = [l for l in self.out if l.startswith("clamp ")][0].split()
        self.assertEqual([float(x) for x in line[1:]], [30.0, -30.0])

    def test_declination_pipeline(self):
        line = [l for l in self.out if l.startswith("decl ")][0].split()
        got = [float(x) for x in line[1:]]
        self.assertAlmostEqual(got[0], 352.49, places=2, msg="trueNorth 应加偏角并回绕")
        self.assertAlmostEqual(got[1], 0.0, places=2, msg="trueNorth=false 不加偏角")
        self.assertAlmostEqual(got[2], 99.0, places=2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
