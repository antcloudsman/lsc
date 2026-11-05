#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""F-16 验收：读盘 golden 顶行格式 vs App 的 Java float32 格式化，36000 航向全量对拍。

用法（仓库根）：
    python tests/at/verify_readsheet_fmt.py            # 全量 36000 点
    python tests/at/verify_readsheet_fmt.py --limit 1000

退出码：0 = 0 不符；1 = 有差异；2 = JDK 不可用（未验证，不得当通过）。
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import golden_readsheet  # noqa: E402

PROBE = HERE / "java" / "ReadSheetFmtProbe.java"
# 本机/CI 已知 JDK 位置；找不到再退回 PATH 上的 java/javac
KNOWN_JDKS = (
    Path(r"C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9"),
)


def find_jdk():
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    candidates.extend(KNOWN_JDKS)
    for base in candidates:
        if (base / "bin" / "java.exe").is_file() and (base / "bin" / "javac.exe").is_file():
            return base / "bin" / "java.exe", base / "bin" / "javac.exe"
    java = shutil.which("java")
    javac = shutil.which("javac")
    if java and javac:
        return Path(java), Path(javac)
    return None, None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=36000)
    args = ap.parse_args()

    java, javac = find_jdk()
    if not java:
        print("JDK: not found（需要 JDK17 的 java+javac）；本次未验证 -> exit 2")
        return 2

    mech_path = HERE.parents[1] / "app" / "mechanics.json"
    import json
    with open(mech_path, encoding="utf-8") as f:
        mech = json.load(f)

    tmp = Path(tempfile.mkdtemp(prefix="lc-fmt-"))
    try:
        # 源文件是 UTF-8（含中文注释/字符串），Windows 默认编码会让 javac 报错
        subprocess.run([str(javac), "-encoding", "UTF-8", str(PROBE), "-d", str(tmp)],
                       check=True, capture_output=True)
        # System.out 默认按平台编码（Windows=GBK）；强制 UTF-8 才能稳定解析
        proc = subprocess.run([str(java), "-Dfile.encoding=UTF-8", "-cp", str(tmp),
                               "ReadSheetFmtProbe"],
                              check=True, capture_output=True)
        lines = proc.stdout.decode("utf-8").splitlines()
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    if len(lines) != 36000:
        print(f"probe 输出行数异常：{len(lines)}（预期 36000）")
        return 1

    mismatches = []
    examples = []
    for line in lines[: args.limit]:
        i_s, h_s, back_s, head_java = line.split("\t", 3)
        i = int(i_s)
        heading = i / 100.0
        head_golden = golden_readsheet.golden(heading, mech)["head1"]
        if head_golden != head_java:
            mismatches.append(i)
            if len(examples) < 10:
                examples.append(
                    f"h={i_s} java=[{head_java}] golden=[{head_golden}]")

    print(f"jdk={java}")
    print(f"headings={min(args.limit, 36000)} mismatches={len(mismatches)}")
    for e in examples:
        print(f"  MISMATCH {e}")
    if mismatches:
        print(f"  first10={[round(m / 100, 2) for m in mismatches[:10]]}")
        return 1
    print("RESULT: 0 mismatch")
    return 0


if __name__ == "__main__":
    sys.exit(main())
