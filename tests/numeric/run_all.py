#!/usr/bin/env python3
"""LC 数值回归一键入口。

用法（仓库根）：
    python tests/numeric/run_all.py

退出码：0 = 全绿、**零跳过**、且 `cargo test` 通过；1 = 有失败/错误/跳过/构建红。
必需组（Java 真机对拍、SensorMath JVM 断言）缺失时一律 FAIL——「跳过即绿」会把
唯一执行 Android 真机代码的用例静默变成不存在，F-01 已按此口径收紧。
F-02：圈层 780 格语义由 Rust `b2_all_780_cells` 真执行，入口必须跑 `cargo test`。
"""
import hashlib
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

# Windows 管道默认按本地代码页输出，中文会乱码；统一为 UTF-8。
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
APP = ROOT / "app"
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


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _newest_test_binary() -> Path | None:
    deps = APP / "target" / "debug" / "deps"
    if not deps.is_dir():
        return None
    cands = [p for p in deps.glob("luopan-*") if p.is_file() and p.suffix in (".exe", "")]
    if not cands:
        return None
    return max(cands, key=lambda p: p.stat().st_mtime)


def _docker_ready() -> bool:
    try:
        proc = subprocess.run(["docker", "version", "--format", "{{.Server.Version}}"],
                              capture_output=True, text=True)
        return proc.returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


def _docker_cargo_test() -> tuple[bool, str]:
    """在仓库自带的 Docker 桌面环境里跑 cargo test（宿主可能缺 MSVC link.exe）。"""
    compose = ROOT / "app" / "docker-compose.yml"
    if not compose.is_file():
        return False, "missing app/docker-compose.yml"
    command = ('export PATH=/usr/local/cargo/bin:/usr/local/rustup/bin:$PATH; '
               'cargo test --manifest-path /src/Cargo.toml')
    proc = subprocess.run(["docker", "compose", "-f", str(compose), "run", "--rm", "build",
                           "bash", "-c", command],
                          cwd=str(ROOT), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    for line in (proc.stdout or "").splitlines():
        if line.startswith(("running ", "test result:", "test ")):
            print(f"CARGO| {line}")
    if proc.returncode != 0:
        for line in (proc.stderr or "").splitlines()[-10:]:
            print(f"CARGO! {line}")
        return False, f"docker cargo test exit={proc.returncode}"
    return True, "docker"


def run_cargo_tests() -> bool:
    """F-02：真跑 `cargo test`（780 格由 b2_all_780_cells 执行）。"""
    cargo = shutil.which("cargo")
    if not cargo:
        print("CARGO: cargo not found -> FAIL（F-02 要求真跑 cargo test）")
        return False
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT,
                          capture_output=True, text=True).stdout.strip()
    version = subprocess.run([cargo, "--version"], cwd=ROOT,
                             capture_output=True, text=True).stdout.strip()
    print(f"CARGO: HEAD={head} version={version}")
    proc = subprocess.run(
        [cargo, "test", "--manifest-path", str(APP / "Cargo.toml")],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    # 只回显测试摘要行，避免把 10 条 warning 淹没「跑了几条、过没过」。
    for line in (proc.stdout or "").splitlines():
        if line.startswith(("running ", "test result:", "test ")):
            print(f"CARGO| {line}")
    if proc.returncode != 0:
        for line in (proc.stderr or "").splitlines()[-14:]:
            print(f"CARGO! {line}")
        print(f"CARGO: native cargo test exit={proc.returncode}")
        if _docker_ready():
            print("CARGO: native 链接失败（宿主缺 MSVC link.exe），改用仓库 Docker 桌面环境重跑")
            ok, detail = _docker_cargo_test()
            if ok:
                print(f"CARGO: PASS ({detail})")
                return True
            print(f"CARGO: FAIL ({detail})")
            return False
        print("CARGO: FAIL（宿主缺 SDK 且 docker 不可用）")
        return False
    binary = _newest_test_binary()
    if binary is not None:
        print(f"CARGO: test-binary={binary.relative_to(ROOT)} sha256={_sha256(binary)}")
    else:
        print("CARGO: test-binary=none（未找到 target/debug/deps/luopan-*）")
    print("CARGO: PASS")
    return True


def main() -> int:
    print(f"LC numeric suite — python {sys.version.split()[0]}")
    print(f"repo root: {ROOT}")
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

    cargo_ok = run_cargo_tests()
    ok = result.wasSuccessful() and not result.skipped and cargo_ok
    if ok:
        print("RESULT: PASS")
        return 0
    problems = []
    if not result.wasSuccessful():
        problems.append("有失败/错误")
    if result.skipped:
        problems.append(f"skipped={len(result.skipped)}（必须为 0 才判绿）")
    if not cargo_ok:
        problems.append("cargo test 未通过")
    print("RESULT: FAIL（" + "；".join(problems) + "）")
    return 1


if __name__ == "__main__":
    sys.exit(main())
