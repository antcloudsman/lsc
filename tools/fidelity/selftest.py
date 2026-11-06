#!/usr/bin/env python3
"""Self-test for fidelity.py that needs no device and no screenshots.

Builds two synthetic 320x600 screenshots (one rectangle shifted by 5 px) and two
uiautomator dumps, then runs the real CLI end-to-end:
  pair -> exact diff pixels/bbox
  sample -> identical bg = 0 dE, shifted edge > 20 dE
  measure -> matched controls, dx/dy reported in px
  report -> HTML + summary generated; missing pair fails --strict
  collect --dry-run -> prints adb commands and writes nothing

Usage: python tools/fidelity/selftest.py [--keep]
Exit code 0 = PASS.
"""

from __future__ import annotations

import argparse
import csv
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

TOOL = Path(__file__).with_name("fidelity.py")
sys.path.insert(0, str(TOOL.parent))
import fidelity  # noqa: E402  (local tool module)
PASSED = 0


def check(condition: bool, message: str) -> None:
    global PASSED
    if not condition:
        print(f"[FAIL] {message}")
        raise AssertionError(message)
    PASSED += 1
    print(f"[PASS] {message}")


def run(args: list[str], expect: int = 0) -> str:
    process = subprocess.run(
        [sys.executable, str(TOOL), *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    output = (process.stdout or "") + (process.stderr or "")
    if process.returncode != expect:
        print(output)
        raise AssertionError(f"exit {process.returncode}, expected {expect}: {' '.join(args)}")
    return output


def make_images(directory: Path) -> tuple[Path, Path]:
    directory.mkdir(parents=True, exist_ok=True)
    orig = Image.new("RGB", (320, 600), (24, 20, 16))
    ImageDraw.Draw(orig).rectangle([40, 80, 139, 179], fill=(200, 30, 30))
    lc = Image.new("RGB", (320, 600), (24, 20, 16))
    ImageDraw.Draw(lc).rectangle([45, 80, 144, 179], fill=(200, 30, 30))
    orig_path, lc_path = directory / "orig.png", directory / "lc.png"
    orig.save(orig_path)
    lc.save(lc_path)
    return orig_path, lc_path


def write_dump(path: Path, x_offset: int, package: str) -> None:
    xml = f"""<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" package="{package}" bounds="[0,0][320,600]">
    <node index="0" class="android.widget.TextView" text="罗盘" resource-id="{package}:id/title"
          bounds="[{10 + x_offset},{20 + x_offset}][{210 + x_offset},{100 + x_offset}]"
          clickable="false" enabled="true"/>
    <node index="1" class="android.widget.Button" text="更多" resource-id="{package}:id/more"
          bounds="[100,200][300,280]" clickable="true" enabled="true"/>
  </node>
</hierarchy>
"""
    path.write_text(xml, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep", action="store_true", help="keep the temporary evidence tree")
    args = parser.parse_args()

    temp = Path(tempfile.mkdtemp(prefix="lc-fidelity-selftest-"))
    try:
        colors_a = np.array([[255, 0, 0], [10, 20, 30], [200, 200, 200]], dtype=np.uint8)
        colors_b = np.array([[0, 0, 255], [30, 20, 10], [201, 200, 199]], dtype=np.uint8)
        check(
            np.allclose(fidelity.delta_e2000(colors_a, colors_b), fidelity.delta_e2000(colors_b, colors_a)),
            "dE2000: symmetric",
        )
        check(np.allclose(fidelity.delta_e2000(colors_a, colors_a), 0.0), "dE2000: identity is 0")
        allow_probe = [
            {"pair": ["a", "b"], "reason": "r", "by": "x"},
            {"pair": ["b", "c"], "reason": "r", "by": "x"},
        ]
        check(
            fidelity._allow_duplicate(["a", "b", "c"], allow_probe) is not None,
            "allowlist: connected pairs cover a 3-state group",
        )
        check(fidelity._allow_duplicate(["a", "b", "c"], allow_probe[:1]) is None, "allowlist: uncovered state still fails")

        root = temp / "docs" / "fidelity"
        orig_png, lc_png = make_images(temp / "source")
        orig_xml, lc_xml = temp / "orig.xml", temp / "lc.xml"
        write_dump(orig_xml, 0, "space.xinzhi.globe")
        write_dump(lc_xml, 5, "com.luopan.fengshui")

        pair_dir = temp / "pair"
        run([
            "pair", "--orig", str(orig_png), "--lc", str(lc_png), "--state", "01-compass-main",
            "--out", str(pair_dir), "--threshold", "0",
        ])
        metrics = json.loads((pair_dir / "metrics.json").read_text(encoding="utf-8"))
        diff = metrics["diff"]
        check(diff["diff_pixels"] == 1000, f"pair: 1000 changed pixels (got {diff['diff_pixels']})")
        check(diff["bbox"] == [40, 80, 144, 179], f"pair: bbox [40,80,144,179] (got {diff['bbox']})")
        check(abs(diff["diff_ratio"] - 1000 / (320 * 600)) < 1e-6, "pair: ratio = changed/total")
        check(diff["mean_delta_e76"] is not None and diff["mean_delta_e76"] > 0, "pair: mean dE76 computed")
        check(diff["mean_delta_e2000"] is not None and diff["mean_delta_e2000"] > 0, "pair: mean dE2000 computed")
        for artifact in ("side_by_side.png", "diff.png", "diff_mask.png", "overlay.png"):
            check((pair_dir / artifact).exists(), f"pair: artifact {artifact}")

        offset_dir = temp / "pair-offset"
        run([
            "pair", "--orig", str(orig_png), "--lc", str(lc_png), "--state", "01-compass-main",
            "--out", str(offset_dir), "--threshold", "0", "--offset=-5,0",
        ])
        offset_metrics = json.loads((offset_dir / "metrics.json").read_text(encoding="utf-8"))
        check(
            offset_metrics["diff"]["diff_pixels"] == 0,
            f"pair: --offset -5,0 aligns the shifted rect (got {offset_metrics['diff']['diff_pixels']})",
        )

        sample_csv = temp / "samples.csv"
        run([
            "sample", "--orig", str(orig_png), "--lc", str(lc_png),
            "--points", "10,10,bg;142,100,edge", "--out", str(sample_csv),
        ])
        with sample_csv.open(newline="", encoding="utf-8") as handle:
            samples = list(csv.DictReader(handle))
        by_name = {row["name"]: row for row in samples}
        check(by_name["bg"]["delta_e76"] == "0.0", f"sample: identical bg dE=0 (got {by_name['bg']['delta_e76']})")
        check(by_name["bg"]["delta_e2000"] == "0.0", "sample: identical bg dE2000=0")
        check(float(by_name["edge"]["delta_e2000"]) > 20, "sample: shifted edge dE2000 > 20")
        check(by_name["edge"]["orig_hex"] == "#181410", "sample: orig hex recorded")

        measure_dir = temp / "measure"
        run([
            "measure", "--orig-xml", str(orig_xml), "--lc-xml", str(lc_xml),
            "--state", "01-compass-main", "--out", str(measure_dir),
        ])
        summary = json.loads((measure_dir / "controls-summary.json").read_text(encoding="utf-8"))
        check(summary["matched"] == 3, f"measure: 3 matched controls (got {summary['matched']})")
        check(summary["only_orig"] == 0 and summary["only_lc"] == 0, "measure: no unmatched controls")
        with (measure_dir / "control-diff.csv").open(newline="", encoding="utf-8") as handle:
            rows = list(csv.DictReader(handle))
        deltas = {(row["orig_text"], int(row["dx"]), int(row["dy"])) for row in rows}
        check(("罗盘", 5, 5) in deltas, "measure: title dx=5 dy=5")
        check(("更多", 0, 0) in deltas, "measure: unchanged button dx=0 dy=0")

        report_root = root
        for side, png in (("orig", orig_png), ("lc", lc_png)):
            (report_root / side).mkdir(parents=True, exist_ok=True)
            shutil.copyfile(png, report_root / side / "01-compass-main.png")
        shutil.copyfile(orig_xml, report_root / "orig" / "01-compass-main.xml")
        shutil.copyfile(lc_xml, report_root / "lc" / "01-compass-main.xml")
        output = run(["report", "--root", str(report_root), "--strict"])
        report_dir = report_root / "findings" / "report"
        check((report_dir / "index.html").exists(), "report: index.html generated")
        check("01-compass-main" in (report_dir / "index.html").read_text(encoding="utf-8"), "report: state listed")
        report_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        check(report_summary["states"][0]["status"] == "compared", "report: state compared")
        check(report_summary["states"][0]["controls"]["matched"] == 3, "report: control diff embedded")
        check("1/1 pair(s) compared" in output, "report: summary line")

        Image.new("RGB", (320, 600), (0, 128, 0)).save(report_root / "orig" / "02-missing.png")
        output = run(["report", "--root", str(report_root), "--strict"], expect=1)
        check("missing-lc" in output, "report: missing lc pair reported")
        missing_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        check(
            any(row["state"] == "02-missing" and row["status"] == "missing-lc" for row in missing_summary["states"]),
            "report: missing pair in summary.json",
        )

        shutil.copyfile(report_root / "orig" / "01-compass-main.png", report_root / "orig" / "03-duplicate.png")
        shutil.copyfile(report_root / "lc" / "01-compass-main.png", report_root / "lc" / "03-duplicate.png")
        output = run(["report", "--root", str(report_root)], expect=1)
        check("duplicate screenshot across states" in output, "report: duplicate screenshot is a hard error")
        dup_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        dup_groups = dup_summary["integrity"]["orig"]["duplicates"] + dup_summary["integrity"]["lc"]["duplicates"]
        check(len(dup_groups) == 2, f"report: duplicates recorded on both sides (got {len(dup_groups)})")
        check("证据完整性" in (report_dir / "index.html").read_text(encoding="utf-8"), "report: integrity section in HTML")

        allow_path = report_root / "duplicates-allow.json"
        valid_allow = {
            "allow": [
                {"pair": ["01-compass-main", "03-duplicate"], "reason": "selftest: same synthetic screen", "by": "LC服务端"}
            ]
        }
        allow_path.write_text(json.dumps(valid_allow, ensure_ascii=False), encoding="utf-8")
        run(["report", "--root", str(report_root)])  # 02-missing only fails under --strict
        allowed_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        allowed_groups = (
            allowed_summary["integrity"]["orig"]["duplicates"] + allowed_summary["integrity"]["lc"]["duplicates"]
        )
        check(
            len(allowed_groups) == 2 and all(group.get("allowed") for group in allowed_groups),
            "report: allowlisted duplicate passes on both sides",
        )

        allow_path.write_text(
            json.dumps({"allow": [{"pair": ["01-compass-main", "03-duplicate"], "by": "LC服务端"}]}),
            encoding="utf-8",
        )
        output = run(["report", "--root", str(report_root)], expect=1)
        check("reason" in output, "report: allowlist missing reason is an error")
        allow_path.write_text(json.dumps(valid_allow, ensure_ascii=False), encoding="utf-8")

        dump_allow_path = report_root / "dump-only-allow.json"
        valid_dump_allow = {
            "allow": [{"state": "05-dump-only", "reason": "selftest: FLAG_SECURE, dump only", "by": "LC服务端"}]
        }
        shutil.copyfile(orig_xml, report_root / "orig" / "05-dump-only.xml")
        dump_allow_path.write_text(json.dumps(valid_dump_allow, ensure_ascii=False), encoding="utf-8")
        run(["report", "--root", str(report_root)])  # 02-missing only fails under --strict
        dump_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        dump_row = next((row for row in dump_summary["states"] if row["state"] == "05-dump-only"), None)
        check(dump_row is not None and dump_row["status"] == "dump-only",
              "report: dump-only exemption skips PNG pairing")
        check(dump_row is not None and "error" not in dump_row, "report: dump-only state is not a missing pair")

        dump_allow_path.write_text(
            json.dumps({"allow": [{"state": "05-dump-only", "by": "LC服务端"}]}), encoding="utf-8"
        )
        output = run(["report", "--root", str(report_root)], expect=1)
        check("reason" in output, "report: dump-only allowlist missing reason is an error")

        dump_allow_path.write_text(
            json.dumps({"allow": [{"state": "06-no-dump", "reason": "mask attempt", "by": "LC服务端"}]}),
            encoding="utf-8",
        )
        output = run(["report", "--root", str(report_root)], expect=1)
        check("no dump" in output, "report: dump-only exemption cannot mask a missing capture")
        dump_allow_path.write_text(json.dumps(valid_dump_allow, ensure_ascii=False), encoding="utf-8")

        (report_root / "orig" / "05-dump-only.png").write_bytes(b"")
        output = run(["report", "--root", str(report_root)], expect=1)
        check("empty screenshot (0 bytes)" in output, "report: dump-only exemption does not skip 0-byte PNG check")
        (report_root / "orig" / "05-dump-only.png").unlink()

        lc_only_states = ["07-lc-only-a", "08-lc-only-b", "09-lc-only-c", "10-lc-only-d"]
        for index, name in enumerate(lc_only_states):
            Image.new("RGB", (320, 600), (10 + index * 40, 50, 200)).save(report_root / "lc" / f"{name}.png")
        run(["report", "--root", str(report_root)])
        before_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        missing_before = sum(1 for row in before_summary["states"] if row["status"] == "missing-orig")
        check(missing_before == 4, f"report: lc-only states are missing-orig before exemption (got {missing_before})")

        lc_only_allow_path = report_root / "lc-only-allow.json"
        valid_lc_only = {
            "allow": [
                {"state": state, "reason": "selftest: LC incremental, no original state", "by": "LC测试"}
                for state in lc_only_states
            ]
        }
        lc_only_allow_path.write_text(json.dumps(valid_lc_only, ensure_ascii=False), encoding="utf-8")
        run(["report", "--root", str(report_root)])
        after_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        missing_after = sum(1 for row in after_summary["states"] if row["status"] == "missing-orig")
        check(missing_after == 0, f"report: lc-only exemption removes 4 missing-orig rows (got {missing_after})")
        check(after_summary["counts"]["lc_only"] == 4, "report: summary counts lc-only")
        check(
            all(row["status"] == "lc-only" for row in after_summary["states"] if row["state"] in lc_only_states),
            "report: lc-only status applied",
        )

        lc_only_allow_path.write_text(
            json.dumps({"allow": [{"state": "11-ghost-lc", "reason": "x", "by": "LC测试"}]}), encoding="utf-8"
        )
        output = run(["report", "--root", str(report_root)], expect=1)
        check("no png/xml" in output, "report: lc-only entry without lc files is an error")

        lc_only_allow_path.write_text(
            json.dumps({"allow": [{"state": lc_only_states[0], "by": "LC测试"}]}), encoding="utf-8"
        )
        output = run(["report", "--root", str(report_root)], expect=1)
        check("reason" in output, "report: lc-only allowlist missing reason is an error")
        lc_only_allow_path.write_text(json.dumps(valid_lc_only, ensure_ascii=False), encoding="utf-8")

        (report_root / "orig" / "04-empty.png").write_bytes(b"")
        output = run(["report", "--root", str(report_root)], expect=1)
        check("empty screenshot (0 bytes)" in output, "report: empty screenshot is a hard error")
        empty_summary = json.loads((report_dir / "summary.json").read_text(encoding="utf-8"))
        check(
            "04-empty.png" in empty_summary["integrity"]["orig"]["empty"],
            "report: empty file recorded in integrity",
        )

        output = run([
            "collect", "--root", str(report_root), "--side", "lc", "--state", "03-collect-dry",
            "--serial", "FAKE-SERIAL", "--dry-run",
        ])
        check("screencap -p" in output, "collect: dry-run prints screencap")
        check("uiautomator dump" in output, "collect: dry-run prints uiautomator dump")

        missing_build = run([
            "collect", "--root", str(report_root), "--side", "lc", "--state", "03-collect-dry",
            "--serial", "FAKE-SERIAL",
        ], expect=1)
        check("--build-commit" in missing_build, "collect: lc capture rejects missing build provenance")

        fake_apk = temp / "fake-debug.apk"
        fake_apk.write_bytes(b"PK\x03\x04" + b"\x00" * (200 * 1024))
        bad_build = run([
            "collect", "--root", str(report_root), "--side", "lc", "--state", "03-collect-dry",
            "--serial", "FAKE-SERIAL", "--build-branch", "develop",
            "--build-commit", "abc", "--apk", str(fake_apk),
        ], expect=1)
        check("40-char" in bad_build, "collect: lc capture rejects a non-40-hex build commit")
        check(not (report_root / "lc" / "03-collect-dry.meta.json").exists(), "collect: dry-run writes nothing")

        print("")
        print(f"SELFTEST: PASS ({PASSED} checks)")
        return 0
    finally:
        if args.keep:
            print(f"kept: {temp}")
        else:
            shutil.rmtree(temp, ignore_errors=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError:
        print("SELFTEST: FAIL")
        sys.exit(1)

