#!/usr/bin/env python3
"""LC 数值回归一键入口。

用法（仓库根）：
    python tests/numeric/run_all.py

退出码：0 = 全绿且**零跳过**；1 = 有失败/错误/跳过。
必需组（Java 真机对拍、SensorMath JVM 断言）缺失时一律 FAIL——「跳过即绿」会把
唯一执行 Android 真机代码的用例静默变成不存在，F-01 已按此口径收紧。
"""
import sys
import unittest
from pathlib import Path

# Windows 管道默认按本地代码页输出，中文会乱码；统一为 UTF-8。
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

# 这些组一旦跳过，套件不得判绿（缺 JDK / 缺被测文件都属于「没有验证」）。
REQUIRED_GROUPS = {
    "test_ends_parity.JavaRealCodeTest": "Java 真机代码对拍",
    "test_sensor_math.SensorMathJvmTest": "SensorMath JVM 断言",
}


def _id(test) -> str:
    getter = getattr(test, "id", None)
    return getter() if callable(getter) else str(test)


def _required_missing(result) -> list:
    """找出被跳过的必需组。类级 skip 的 id 形如 `setUpClass (test_x.Y)`。"""
    missing = []
    for test, reason in result.skipped:
        test_id = _id(test)
        for cls, label in REQUIRED_GROUPS.items():
            if test_id == cls or test_id.startswith(f"setUpClass ({cls})") \
                    or test_id.startswith(f"{cls}."):
                missing.append((cls, label, reason))
    return missing


def main() -> int:
    print(f"LC numeric suite — python {sys.version.split()[0]}")
    print(f"repo root: {HERE.parents[1]}")
    suite = unittest.TestLoader().discover(str(HERE), pattern="test_*.py", top_level_dir=str(HERE))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    print("-" * 72)
    print(f"tests={result.testsRun} failures={len(result.failures)} errors={len(result.errors)} "
          f"skipped={len(result.skipped)} expected_failures={len(result.expectedFailures)} "
          f"unexpected_successes={len(result.unexpectedSuccesses)}")

    missing = _required_missing(result)
    for test, reason in result.skipped:
        print(f"SKIP: {_id(test)} | {reason}")
    for cls, label, reason in missing:
        print(f"REQUIRED GROUP SKIPPED -> FAIL: {cls}（{label}）| {reason}")

    ok = result.wasSuccessful() and not result.skipped
    if ok:
        print("RESULT: PASS")
        return 0
    if result.skipped and result.wasSuccessful():
        print("RESULT: FAIL（有跳过；skipped 必须为 0 才判绿）")
        return 1
    print("RESULT: FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
