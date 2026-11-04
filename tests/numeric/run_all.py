#!/usr/bin/env python3
"""LC 数值回归一键入口。

用法（仓库根 E:\\APPDetector\\output）：
    python tests/numeric/run_all.py

退出码：0 = 全绿（含已知缺陷 xfail），1 = 有失败/错误。
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


def main() -> int:
    print(f"LC numeric suite — python {sys.version.split()[0]}")
    print(f"repo root: {HERE.parents[1]}")
    suite = unittest.TestLoader().discover(str(HERE), pattern="test_*.py", top_level_dir=str(HERE))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    print("-" * 72)
    print(f"tests={result.testsRun} failures={len(result.failures)} errors={len(result.errors)} "
          f"skipped={len(result.skipped)} expected_failures={len(result.expectedFailures)} "
          f"unexpected_successes={len(result.unexpectedSuccesses)}")
    if result.wasSuccessful():
        print("RESULT: PASS")
        return 0
    print("RESULT: FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())
