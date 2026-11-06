#!/usr/bin/env python3
"""LC fidelity toolkit - original-vs-ours screenshot comparison.

Subcommands
  pair     Align one original/LC screenshot pair, write side-by-side, diff images,
           metrics (diff pixels/ratio/bbox/mean RGB delta/mean dE76).
  sample   Sample pixel colours at points or a grid from one or two screenshots,
           print hex + RGB + dE76 (orig vs lc).
  measure  Parse uiautomator dumps into a control table and match original vs LC
           bounds, reporting dx/dy/dw/dh in px.
  report   Batch: compare every docs/fidelity/orig/<state>.png with
           docs/fidelity/lc/<state>.png, then build an HTML contact sheet.
  collect  One-command device capture (screenshot + uiautomator dump + meta.json)
           into the fidelity evidence tree. Supports --dry-run.

Evidence layout (default --root docs/fidelity):
  orig/<state>.png / .xml / .meta.json      original app capture
  lc/<state>.png   / .xml / .meta.json      our app capture
  findings/report/                          generated diffs + HTML
  findings/report/<state>/                   per-state pair outputs

Dependencies: Pillow (required), numpy (optional, enables dE76 and fast metrics).
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

try:
    import numpy as np
except ImportError:  # pragma: no cover - exercised only on minimal installs
    np = None

VERSION = "1.4.0"
STATE_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")
EXPECTED_SIZE = (1228, 2700)
DEFAULT_THRESHOLD = 8

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")


class FidelityError(RuntimeError):
    """A reproducible tool failure with a user-facing message."""


def info(message: str) -> None:
    print(f"[ -- ] {message}")


def passed(message: str) -> None:
    print(f"[PASS] {message}")


def failed(message: str) -> None:
    print(f"[FAIL] {message}")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_rgb(path: Path) -> Image.Image:
    if not path.exists():
        raise FidelityError(f"image not found: {path}")
    try:
        with Image.open(path) as image:
            return image.convert("RGB")
    except OSError as error:
        raise FidelityError(f"cannot decode image {path}: {error}") from error


def parse_rect(value: str, field: str) -> tuple[int, int, int, int]:
    parts = [part.strip() for part in value.split(",")]
    if len(parts) != 4:
        raise FidelityError(f"{field} must be x,y,w,h (got {value!r})")
    try:
        x, y, width, height = (int(part) for part in parts)
    except ValueError as error:
        raise FidelityError(f"{field} must be four integers (got {value!r})") from error
    if width <= 0 or height <= 0:
        raise FidelityError(f"{field} width/height must be positive (got {value!r})")
    return x, y, width, height


def parse_offset(value: str) -> tuple[int, int]:
    parts = [part.strip() for part in value.split(",")]
    if len(parts) != 2:
        raise FidelityError(f"--offset must be dx,dy (got {value!r})")
    try:
        return int(parts[0]), int(parts[1])
    except ValueError as error:
        raise FidelityError(f"--offset must be two integers (got {value!r})") from error


def crop_image(image: Image.Image, crop: tuple[int, int, int, int] | None, label: str) -> Image.Image:
    if crop is None:
        return image
    x, y, width, height = crop
    if x + width > image.width or y + height > image.height:
        raise FidelityError(
            f"{label} crop {crop} exceeds image size {image.width}x{image.height}"
        )
    return image.crop((x, y, x + width, y + height))


def _lab_from_srgb(rgb: "np.ndarray") -> "np.ndarray":
    """sRGB uint8 array (..., 3) -> CIE Lab (D65) float32."""
    c = rgb.astype(np.float32) / 255.0
    low = c <= 0.04045
    linear = np.where(low, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    x = linear[..., 0] * 0.4124564 + linear[..., 1] * 0.3575761 + linear[..., 2] * 0.1804375
    y = linear[..., 0] * 0.2126729 + linear[..., 1] * 0.7151522 + linear[..., 2] * 0.0721750
    z = linear[..., 0] * 0.0193339 + linear[..., 1] * 0.1191920 + linear[..., 2] * 0.9503041
    x /= 0.95047
    z /= 1.08883
    delta = 6.0 / 29.0

    def f(t: "np.ndarray") -> "np.ndarray":
        return np.where(t > delta ** 3, np.cbrt(t), t / (3 * delta ** 2) + 4.0 / 29.0)

    fx, fy, fz = f(x), f(y), f(z)
    return np.stack((116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)), axis=-1).astype(np.float32)


def delta_e76(rgb_a: "np.ndarray", rgb_b: "np.ndarray") -> "np.ndarray":
    lab_a = _lab_from_srgb(rgb_a)
    lab_b = _lab_from_srgb(rgb_b)
    return np.sqrt(((lab_a - lab_b) ** 2).sum(axis=-1))


def delta_e2000(rgb_a: "np.ndarray", rgb_b: "np.ndarray") -> "np.ndarray":
    """CIEDE2000 colour difference (the checksheet's deltaE2000)."""
    lab1 = _lab_from_srgb(rgb_a).astype(np.float64)
    lab2 = _lab_from_srgb(rgb_b).astype(np.float64)
    L1, a1, b1 = lab1[..., 0], lab1[..., 1], lab1[..., 2]
    L2, a2, b2 = lab2[..., 0], lab2[..., 1], lab2[..., 2]
    c1 = np.sqrt(a1 * a1 + b1 * b1)
    c2 = np.sqrt(a2 * a2 + b2 * b2)
    c_bar = (c1 + c2) / 2.0
    c7 = c_bar ** 7
    g = 0.5 * (1.0 - np.sqrt(c7 / (c7 + 25.0 ** 7)))
    a1p, a2p = (1.0 + g) * a1, (1.0 + g) * a2
    c1p = np.sqrt(a1p * a1p + b1 * b1)
    c2p = np.sqrt(a2p * a2p + b2 * b2)
    h1p = np.degrees(np.arctan2(b1, a1p)) % 360.0
    h2p = np.degrees(np.arctan2(b2, a2p)) % 360.0

    d_lp = L2 - L1
    d_cp = c2p - c1p
    dh = h2p - h1p
    dhp = np.where(dh > 180.0, dh - 360.0, np.where(dh < -180.0, dh + 360.0, dh))
    dhp = np.where(c1p * c2p == 0, 0.0, dhp)
    d_hp = 2.0 * np.sqrt(c1p * c2p) * np.sin(np.radians(dhp / 2.0))

    l_bar = (L1 + L2) / 2.0
    c_bar_p = (c1p + c2p) / 2.0
    h_sum = h1p + h2p
    h_bar = np.where(
        c1p * c2p == 0,
        h_sum,
        np.where(
            np.abs(h1p - h2p) <= 180.0,
            h_sum / 2.0,
            np.where(h_sum < 360.0, (h_sum + 360.0) / 2.0, (h_sum - 360.0) / 2.0),
        ),
    )
    t = (
        1.0
        - 0.17 * np.cos(np.radians(h_bar - 30.0))
        + 0.24 * np.cos(np.radians(2.0 * h_bar))
        + 0.32 * np.cos(np.radians(3.0 * h_bar + 6.0))
        - 0.20 * np.cos(np.radians(4.0 * h_bar - 63.0))
    )
    d_theta = 30.0 * np.exp(-(((h_bar - 275.0) / 25.0) ** 2))
    c7p = c_bar_p ** 7
    r_c = 2.0 * np.sqrt(c7p / (c7p + 25.0 ** 7))
    l50 = (l_bar - 50.0) ** 2
    s_l = 1.0 + 0.015 * l50 / np.sqrt(20.0 + l50)
    s_c = 1.0 + 0.045 * c_bar_p
    s_h = 1.0 + 0.015 * c_bar_p * t
    r_t = -np.sin(np.radians(2.0 * d_theta)) * r_c
    return np.sqrt(
        (d_lp / s_l) ** 2
        + (d_cp / s_c) ** 2
        + (d_hp / s_h) ** 2
        + r_t * (d_cp / s_c) * (d_hp / s_h)
    )


def _bbox(mask: "np.ndarray", offset_x: int, offset_y: int) -> list[int] | None:
    rows = np.any(mask, axis=1)
    cols = np.any(mask, axis=0)
    if not rows.any():
        return None
    ys = np.nonzero(rows)[0]
    xs = np.nonzero(cols)[0]
    return [
        int(xs[0]) + offset_x,
        int(ys[0]) + offset_y,
        int(xs[-1]) + offset_x,
        int(ys[-1]) + offset_y,
    ]


def _render_diff(orig: Image.Image, lc: Image.Image, mask: "np.ndarray",
                 region: tuple[int, int, int, int], lc_region: tuple[int, int, int, int],
                 out_diff: Path, out_mask: Path) -> None:
    width, height = orig.size
    x0, y0, x1, y1 = region
    lx0, ly0, lx1, ly1 = lc_region
    orig_arr = np.asarray(orig, dtype=np.float32)
    lc_arr = np.asarray(lc, dtype=np.float32)
    diff_arr = np.zeros((height, width, 3), dtype=np.uint8)
    region_diff = np.abs(orig_arr[y0:y1, x0:x1] - lc_arr[ly0:ly1, lx0:lx1])
    diff_arr[y0:y1, x0:x1] = np.clip(region_diff * 4.0, 0, 255).astype(np.uint8)
    Image.fromarray(diff_arr, "RGB").save(out_diff)

    mask_arr = np.full((height, width, 3), 255, dtype=np.uint8)
    mask_arr[y0:y1, x0:x1][mask] = (220, 30, 30)
    Image.fromarray(mask_arr, "RGB").save(out_mask)


def _side_by_side(orig: Image.Image, lc: Image.Image, label_orig: str, label_lc: str) -> Image.Image:
    gap = 20
    header = 34
    width = orig.width + gap + lc.width
    height = max(orig.height, lc.height) + header
    canvas = Image.new("RGB", (width, height), (18, 18, 20))
    canvas.paste(orig, (0, header))
    canvas.paste(lc, (orig.width + gap, header))
    draw = ImageDraw.Draw(canvas)
    draw.text((8, 10), label_orig, fill=(240, 240, 240))
    draw.text((orig.width + gap + 8, 10), label_lc, fill=(240, 240, 240))
    return canvas


def compare_pair(
    orig_path: Path,
    lc_path: Path,
    out_dir: Path,
    state: str,
    threshold: int = DEFAULT_THRESHOLD,
    crop: tuple[int, int, int, int] | None = None,
    offset: tuple[int, int] = (0, 0),
    ignore_rects: list[tuple[int, int, int, int]] | None = None,
) -> dict:
    """Compare one pair and write images + metrics. Raises FidelityError on size mismatch."""
    if np is None:
        raise FidelityError("numpy is required for pair/report metrics; install it with: python -m pip install numpy")
    orig = crop_image(load_rgb(orig_path), crop, "original")
    lc = crop_image(load_rgb(lc_path), crop, "lc")
    if orig.size != lc.size:
        raise FidelityError(
            f"{state}: size mismatch original={orig.width}x{orig.height} "
            f"lc={lc.width}x{lc.height}; capture both at 1228x2700 and same state"
        )
    ignore_rects = ignore_rects or []
    out_dir.mkdir(parents=True, exist_ok=True)

    width, height = orig.size
    dx, dy = offset
    orig_arr = np.asarray(orig, dtype=np.int16)
    lc_arr_full = np.asarray(lc, dtype=np.int16)

    x0, y0 = max(0, dx), max(0, dy)
    x1, y1 = min(width, width + dx), min(height, height + dy)
    if x1 <= x0 or y1 <= y0:
        raise FidelityError(f"{state}: offset {offset} leaves no overlapping pixels")
    # lc pixels sit at (x - dx, y - dy) for original coordinate (x, y)
    lc_arr = lc_arr_full[y0 - dy:y1 - dy, x0 - dx:x1 - dx]
    orig_arr = orig_arr[y0:y1, x0:x1]

    delta = np.abs(orig_arr - lc_arr)
    mask = delta.max(axis=2) > threshold
    compared = int(mask.size)

    ignored = 0
    for rx, ry, rw, rh in ignore_rects:
        ix0, iy0 = max(rx, x0), max(ry, y0)
        ix1, iy1 = min(rx + rw, x1), min(ry + rh, y1)
        if ix0 >= ix1 or iy0 >= iy1:
            info(f"{state}: ignore-rect {rx},{ry},{rw},{rh} does not overlap the image")
            continue
        local = mask[iy0 - y0:iy1 - y0, ix0 - x0:ix1 - x0]
        ignored += int(local.size)
        local[:] = False
    compared -= ignored

    diff_pixels = int(mask.sum())
    ratio = (diff_pixels / compared) if compared else 0.0
    mean_delta = [round(float(value), 3) for value in delta.mean(axis=(0, 1))]
    max_delta = int(delta.max())

    mean_de = max_de = None
    mean_de2000 = max_de2000 = None
    if np is not None:
        de = delta_e76(orig_arr.astype(np.uint8), lc_arr.astype(np.uint8))
        mean_de = round(float(de.mean()), 3)
        max_de = round(float(de.max()), 3)
        de2000 = delta_e2000(orig_arr.astype(np.uint8), lc_arr.astype(np.uint8))
        mean_de2000 = round(float(de2000.mean()), 3)
        max_de2000 = round(float(de2000.max()), 3)

    bbox = _bbox(mask, x0, y0) if np is not None else None

    diff_path = out_dir / "diff.png"
    mask_path = out_dir / "diff_mask.png"
    if np is not None:
        _render_diff(orig, lc, mask, (x0, y0, x1, y1), (x0 - dx, y0 - dy, x1 - dx, y1 - dy),
                     diff_path, mask_path)
    else:
        ImageChops.difference(orig, lc).point(lambda value: min(255, value * 4)).save(diff_path)

    _side_by_side(orig, lc, "ORIGINAL", "LC (ours)").save(out_dir / "side_by_side.png")
    Image.blend(orig, lc, 0.5).save(out_dir / "overlay.png")

    metrics = {
        "tool": "fidelity",
        "version": VERSION,
        "state": state,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "original": {
            "path": str(orig_path),
            "width": orig.width,
            "height": orig.height,
            "sha256": sha256_file(orig_path),
        },
        "lc": {
            "path": str(lc_path),
            "width": lc.width,
            "height": lc.height,
            "sha256": sha256_file(lc_path),
        },
        "alignment": {
            "crop": list(crop) if crop else None,
            "offset": [dx, dy],
            "ignore_rects": [list(rect) for rect in ignore_rects],
        },
        "diff": {
            "threshold": threshold,
            "compared_pixels": compared,
            "ignored_pixels": ignored,
            "diff_pixels": diff_pixels,
            "diff_ratio": round(ratio, 6),
            "bbox": bbox,
            "mean_abs_rgb_delta": mean_delta,
            "max_abs_rgb_delta": max_delta,
            "mean_delta_e76": mean_de,
            "max_delta_e76": max_de,
            "mean_delta_e2000": mean_de2000,
            "max_delta_e2000": max_de2000,
        },
        "artifacts": {
            "side_by_side": str((out_dir / "side_by_side.png").relative_to(out_dir)),
            "diff": str(diff_path.relative_to(out_dir)),
            "diff_mask": str(mask_path.relative_to(out_dir)),
            "overlay": "overlay.png",
        },
    }
    (out_dir / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return metrics


# ---------------------------------------------------------------------------
# uiautomator dump parsing / measurement
# ---------------------------------------------------------------------------

def _parse_bounds(value: str) -> tuple[int, int, int, int]:
    match = re.match(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$", value.strip())
    if not match:
        raise FidelityError(f"bad bounds attribute: {value!r}")
    x1, y1, x2, y2 = (int(part) for part in match.groups())
    return x1, y1, x2, y2


def parse_dump(xml_path: Path) -> list[dict]:
    if not xml_path.exists():
        raise FidelityError(f"dump not found: {xml_path}")
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError as error:
        raise FidelityError(f"cannot parse {xml_path}: {error}") from error
    controls = []
    for node in root.iter("node"):
        attrs = node.attrib
        bounds = attrs.get("bounds", "")
        if not bounds:
            continue
        x1, y1, x2, y2 = _parse_bounds(bounds)
        controls.append(
            {
                "class": attrs.get("class", ""),
                "text": attrs.get("text", ""),
                "resource_id": attrs.get("resource-id", ""),
                "content_desc": attrs.get("content-desc", ""),
                "package": attrs.get("package", ""),
                "clickable": attrs.get("clickable", ""),
                "enabled": attrs.get("enabled", ""),
                "checked": attrs.get("checked", ""),
                "selected": attrs.get("selected", ""),
                "x1": x1,
                "y1": y1,
                "x2": x2,
                "y2": y2,
                "w": x2 - x1,
                "h": y2 - y1,
                "cx": (x1 + x2) // 2,
                "cy": (y1 + y2) // 2,
            }
        )
    return controls


CONTROL_FIELDS = [
    "side", "index", "class", "text", "resource_id", "content_desc", "package",
    "clickable", "enabled", "checked", "selected", "x1", "y1", "x2", "y2", "w", "h", "cx", "cy",
]


def write_controls_csv(controls: list[dict], side: str, path: Path) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=CONTROL_FIELDS)
        writer.writeheader()
        for index, control in enumerate(controls):
            row = {"side": side, "index": index}
            row.update({field: control.get(field, "") for field in CONTROL_FIELDS if field not in row})
            writer.writerow(row)


def _control_key(control: dict) -> tuple:
    # resource-id is package-prefixed and the packages differ (space.xinzhi.globe vs
    # com.luopan.fengshui), so compare the local id after "/".
    if control["resource_id"]:
        return ("rid", control["resource_id"].split("/")[-1])
    return ("txt", control["class"], control["text"], control["content_desc"])


def _match_controls(orig: list[dict], lc: list[dict]) -> tuple[list[dict], int, int]:
    buckets: dict[tuple, list[dict]] = {}
    for control in orig:
        buckets.setdefault(_control_key(control), []).append(control)
    matched = []
    only_lc = 0
    for control in lc:
        bucket = buckets.get(_control_key(control))
        if not bucket:
            only_lc += 1
            continue
        source = bucket.pop(0)
        item = {
            "key": "|".join(part for part in _control_key(control) if part),
            "orig_text": source["text"],
            "lc_text": control["text"],
            "orig_bounds": f"[{source['x1']},{source['y1']}][{source['x2']},{source['y2']}]",
            "lc_bounds": f"[{control['x1']},{control['y1']}][{control['x2']},{control['y2']}]",
            "dx": control["x1"] - source["x1"],
            "dy": control["y1"] - source["y1"],
            "dw": control["w"] - source["w"],
            "dh": control["h"] - source["h"],
            "orig_class": source["class"],
            "lc_class": control["class"],
        }
        matched.append(item)
    only_orig = sum(len(items) for items in buckets.values())
    return matched, only_orig, only_lc


def measure_pair(orig_xml: Path | None, lc_xml: Path | None, out_dir: Path, state: str) -> dict:
    out_dir.mkdir(parents=True, exist_ok=True)
    summary = {"state": state, "matched": 0, "only_orig": 0, "only_lc": 0}
    orig = lc = None
    if orig_xml:
        orig = parse_dump(orig_xml)
        write_controls_csv(orig, "orig", out_dir / "controls-orig.csv")
        summary["orig_controls"] = len(orig)
    if lc_xml:
        lc = parse_dump(lc_xml)
        write_controls_csv(lc, "lc", out_dir / "controls-lc.csv")
        summary["lc_controls"] = len(lc)
    if orig is not None and lc is not None:
        matched, only_orig, only_lc = _match_controls(orig, lc)
        with (out_dir / "control-diff.csv").open("w", newline="", encoding="utf-8") as handle:
            fields = ["key", "orig_text", "lc_text", "orig_bounds", "lc_bounds", "dx", "dy", "dw", "dh",
                      "orig_class", "lc_class"]
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(matched)
        summary.update(
            {
                "matched": len(matched),
                "only_orig": only_orig,
                "only_lc": only_lc,
                "max_abs_dx": max((abs(item["dx"]) for item in matched), default=0),
                "max_abs_dy": max((abs(item["dy"]) for item in matched), default=0),
                "max_abs_dw": max((abs(item["dw"]) for item in matched), default=0),
                "max_abs_dh": max((abs(item["dh"]) for item in matched), default=0),
            }
        )
    (out_dir / "controls-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return summary


# ---------------------------------------------------------------------------
# HTML report
# ---------------------------------------------------------------------------

REPORT_CSS = """
:root { color-scheme: dark; }
body { margin: 0; background: #14161a; color: #e7e9ee; font: 14px/1.6 "Segoe UI", "Microsoft YaHei", sans-serif; }
main { max-width: 1600px; margin: 0 auto; padding: 24px; }
h1 { font-size: 22px; margin: 0 0 4px; }
h2 { font-size: 18px; margin: 28px 0 8px; border-bottom: 1px solid #2c313a; padding-bottom: 6px; }
.meta { color: #9aa3b2; font-size: 12px; margin-bottom: 18px; }
table { border-collapse: collapse; width: 100%; margin: 8px 0 16px; font-size: 13px; }
th, td { border: 1px solid #2c313a; padding: 5px 8px; text-align: left; vertical-align: top; }
th { background: #1d2128; }
.pass { color: #6fd08c; } .warn { color: #e0b85c; } .fail { color: #ef7d7d; }
.images { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.images figure { margin: 0; background: #1a1e24; padding: 8px; }
.images img { width: 100%; height: auto; display: block; border: 1px solid #2c313a; }
figcaption { color: #9aa3b2; font-size: 12px; margin-top: 4px; }
code { background: #1d2128; padding: 1px 5px; border-radius: 3px; }
.small { color: #9aa3b2; font-size: 12px; }
"""


def _esc(value: object) -> str:
    return (
        str(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def _rel(path: Path, start: Path) -> str:
    return Path(os.path.relpath(path, start)).as_posix()


def _scan_image_integrity(directory: Path) -> dict:
    """Find evidence that cannot be trusted: 0-byte PNGs and one image reused for several states."""
    result = {"duplicates": [], "empty": []}
    if not directory.exists():
        return result
    by_hash: dict[str, list[str]] = {}
    for png in sorted(directory.glob("*.png")):
        if png.name.startswith("_"):
            continue  # _launch-probe etc. are probes, not states
        size = png.stat().st_size
        if size == 0:
            result["empty"].append(png.name)
            continue
        by_hash.setdefault(sha256_file(png), []).append(png.name)
    for digest, names in sorted(by_hash.items()):
        if len(names) > 1:
            result["duplicates"].append(
                {"sha256": digest, "states": [Path(name).stem for name in names], "files": names}
            )
    return result


def write_report(rows: list[dict], out_dir: Path, root: Path, threshold: int, integrity: dict) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    summary = {
        "tool": "fidelity",
        "version": VERSION,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "root": str(root),
        "threshold": threshold,
        "integrity": integrity,
        "states": rows,
    }
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    total = len(rows)
    compared = sum(1 for row in rows if row["status"] == "compared")
    failed_rows = [row for row in rows if row["status"] != "compared"]

    html = [
        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">",
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
        "<title>LC fidelity report</title>",
        f"<style>{REPORT_CSS}</style></head><body><main>",
        "<h1>LC fidelity report</h1>",
        f"<div class=\"meta\">root <code>{_esc(root)}</code> | states {compared}/{total} compared | "
        f"threshold {threshold} | generated {time.strftime('%Y-%m-%d %H:%M:%S')}</div>",
        "<table><tr><th>state</th><th>status</th><th>diff px</th><th>ratio</th><th>bbox (x1,y1,x2,y2)</th>"
        "<th>mean dE2000</th><th>controls (matched/only orig/only lc)</th></tr>",
    ]
    for row in rows:
        status = row["status"]
        css = "pass" if status == "compared" else "fail"
        metrics = row.get("metrics") or {}
        diff = metrics.get("diff") or {}
        controls = row.get("controls") or {}
        control_text = "-"
        if controls:
            control_text = f"{controls.get('matched', 0)}/{controls.get('only_orig', 0)}/{controls.get('only_lc', 0)}"
        html.append(
            "<tr>"
            f"<td><a href=\"#{_esc(row['state'])}\">{_esc(row['state'])}</a></td>"
            f"<td class=\"{css}\">{_esc(status)}</td>"
            f"<td>{_esc(diff.get('diff_pixels', '-'))}</td>"
            f"<td>{_esc(diff.get('diff_ratio', '-'))}</td>"
            f"<td>{_esc(diff.get('bbox', '-'))}</td>"
            f"<td>{_esc(diff.get('mean_delta_e2000', diff.get('mean_delta_e76', '-')))}</td>"
            f"<td>{_esc(control_text)}</td>"
            "</tr>"
        )
    html.append("</table>")

    # Evidence integrity: duplicate screenshots / empty files are hard errors, not findings.
    html.append("<h2>证据完整性</h2>")
    integrity_rows = 0
    for side in ("orig", "lc"):
        for group in integrity.get(side, {}).get("duplicates", []):
            integrity_rows += 1
            html.append(
                "<table><tr><th>duplicate</th><td>"
                f"{_esc(side)}: {_esc(', '.join(group['states']))} share sha256 {_esc(group['sha256'][:16])}…"
                "</td></tr></table>"
            )
        for name in integrity.get(side, {}).get("empty", []):
            integrity_rows += 1
            html.append(f"<p class=\"fail\">empty screenshot (0 bytes): {_esc(side)}/{_esc(name)}</p>")
    if integrity_rows == 0:
        html.append("<p class=\"pass\">no duplicate or empty screenshots</p>")

    for row in rows:
        state = row["state"]
        html.append(f"<h2 id=\"{_esc(state)}\">{_esc(state)}</h2>")
        if row["status"] != "compared":
            html.append(f"<p class=\"fail\">{_esc(row.get('error', row['status']))}</p>")
            continue
        metrics = row["metrics"]
        diff = metrics["diff"]
        state_dir = out_dir / state
        html.append(
            "<table>"
            f"<tr><th>original</th><td><code>{_esc(metrics['original']['path'])}</code> "
            f"{metrics['original']['width']}x{metrics['original']['height']}</td></tr>"
            f"<tr><th>LC</th><td><code>{_esc(metrics['lc']['path'])}</code> "
            f"{metrics['lc']['width']}x{metrics['lc']['height']}</td></tr>"
            f"<tr><th>diff</th><td>{_esc(diff['diff_pixels'])} px / ratio {_esc(diff['diff_ratio'])} / "
            f"bbox {_esc(diff['bbox'])} / mean RGB {_esc(diff['mean_abs_rgb_delta'])} / "
            f"max RGB {_esc(diff['max_abs_rgb_delta'])} / mean dE2000 "
            f"{_esc(diff.get('mean_delta_e2000', diff.get('mean_delta_e76')))}</td></tr>"
            f"<tr><th>alignment</th><td>crop {_esc(metrics['alignment']['crop'])} / "
            f"offset {_esc(metrics['alignment']['offset'])} / ignore {_esc(metrics['alignment']['ignore_rects'])}</td></tr>"
            "</table>"
        )
        html.append("<div class=\"images\">")
        for caption, name in (("original", "side_by_side"), ("diff (x4)", "diff")):
            image_path = state_dir / f"{name}.png"
            html.append(
                f"<figure><img loading=\"lazy\" src=\"{_esc(_rel(image_path, out_dir))}\" alt=\"{_esc(state)} {caption}\">"
                f"<figcaption>{_esc(caption)}</figcaption></figure>"
            )
        html.append("</div>")
        control_diff = state_dir / "control-diff.csv"
        if control_diff.exists():
            html.append(
                f"<p class=\"small\">control diff: <a href=\"{_esc(_rel(control_diff, out_dir))}\">control-diff.csv</a>"
                f" | controls: {_esc(row.get('controls'))}</p>"
            )
    html.append(
        f"<p class=\"small\">Generated by <code>tools/fidelity/fidelity.py report</code> v{VERSION}; "
        "diff pixels use threshold on max per-channel |delta|.</p>"
    )
    html.append("</main></body></html>")
    (out_dir / "index.html").write_text("\n".join(html), encoding="utf-8")

    md = [
        "# LC fidelity summary",
        "",
        f"- root: `{root}`",
        f"- states compared: {compared}/{total}",
        f"- threshold: {threshold}",
        "",
        "## 证据完整性",
        "",
    ]
    duplicate_total = sum(len(integrity.get(side, {}).get("duplicates", [])) for side in ("orig", "lc"))
    empty_total = sum(len(integrity.get(side, {}).get("empty", [])) for side in ("orig", "lc"))
    if duplicate_total == 0 and empty_total == 0:
        md.append("- no duplicate or empty screenshots")
    else:
        for side in ("orig", "lc"):
            for group in integrity.get(side, {}).get("duplicates", []):
                md.append(
                    f"- DUPLICATE {side}: {', '.join(group['states'])} share sha256 `{group['sha256'][:16]}…`"
                )
            for name in integrity.get(side, {}).get("empty", []):
                md.append(f"- EMPTY {side}/{name} (0 bytes)")
    md.extend([
        "",
        "| state | status | diff px | ratio | bbox | mean dE2000 | controls (m/only orig/only lc) |",
        "|---|---|---|---|---|---|---|",
    ])
    for row in rows:
        metrics = row.get("metrics") or {}
        diff = metrics.get("diff") or {}
        controls = row.get("controls") or {}
        md.append(
            f"| {row['state']} | {row['status']} | {diff.get('diff_pixels', '-')} | "
            f"{diff.get('diff_ratio', '-')} | {diff.get('bbox', '-')} | "
            f"{diff.get('mean_delta_e2000', diff.get('mean_delta_e76', '-'))} | "
            f"{controls.get('matched', '-')}/{controls.get('only_orig', '-')}/{controls.get('only_lc', '-')} |"
        )
    (out_dir / "summary.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    passed(f"report: {compared}/{total} pair(s) compared -> {out_dir / 'index.html'}")
    if failed_rows:
        failed(f"report: {len(failed_rows)} pair(s) not compared (missing/size mismatch/error)")


# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

def cmd_pair(args: argparse.Namespace) -> int:
    orig = Path(args.orig)
    lc = Path(args.lc)
    state = args.state or orig.stem
    out_dir = Path(args.out) if args.out else Path("findings") / state
    crop = parse_rect(args.crop, "--crop") if args.crop else None
    offset = parse_offset(args.offset)
    ignore_rects = [parse_rect(value, "--ignore-rect") for value in (args.ignore_rect or [])]
    metrics = compare_pair(orig, lc, out_dir, state, args.threshold, crop, offset, ignore_rects)
    diff = metrics["diff"]
    passed(
        f"{state}: diff {diff['diff_pixels']} px, ratio {diff['diff_ratio']}, bbox {diff['bbox']}, "
        f"mean dE76 {diff['mean_delta_e76']} -> {out_dir}"
    )
    return 0


def cmd_sample(args: argparse.Namespace) -> int:
    orig = load_rgb(Path(args.orig))
    lc = load_rgb(Path(args.lc)) if args.lc else None
    if lc is not None and lc.size != orig.size:
        raise FidelityError(f"size mismatch original={orig.size} lc={lc.size}")
    width, height = orig.size

    points: list[tuple[int, int, str]] = []
    if args.points:
        for index, part in enumerate(args.points.split(";")):
            if not part.strip():
                continue
            bits = [bit.strip() for bit in part.split(",")]
            if len(bits) < 2:
                raise FidelityError(f"--points item {part!r} must be x,y[,name]")
            try:
                x, y = int(bits[0]), int(bits[1])
            except ValueError as error:
                raise FidelityError(f"--points item {part!r} must start with integers") from error
            points.append((x, y, bits[2] if len(bits) > 2 else f"p{index}"))
    elif args.grid:
        match = re.match(r"^(\d+)x(\d+)$", args.grid)
        if not match:
            raise FidelityError("--grid must be COLSxROWS, e.g. 6x8")
        cols, rows = int(match.group(1)), int(match.group(2))
        for row in range(rows):
            for col in range(cols):
                x = int((col + 0.5) * width / cols)
                y = int((row + 0.5) * height / rows)
                points.append((x, y, f"g{row}-{col}"))
    else:
        raise FidelityError("sample needs --points 'x,y[,name];...' or --grid COLSxROWS")

    orig_arr = np.asarray(orig) if np is not None else None
    lc_arr = np.asarray(lc) if (np is not None and lc is not None) else None
    if np is None:
        info("numpy is not installed: dE76 columns will be empty")

    rows = []
    for x, y, name in points:
        if not (0 <= x < width and 0 <= y < height):
            failed(f"sample {name}: ({x},{y}) outside {width}x{height}")
            continue
        rgb_orig = orig.getpixel((x, y))
        item = {"name": name, "x": x, "y": y, "orig_hex": "#{:02x}{:02x}{:02x}".format(*rgb_orig),
                "orig_rgb": "{} {} {}".format(*rgb_orig)}
        if lc is not None:
            rgb_lc = lc.getpixel((x, y))
            item.update(
                {
                    "lc_hex": "#{:02x}{:02x}{:02x}".format(*rgb_lc),
                    "lc_rgb": "{} {} {}".format(*rgb_lc),
                    "delta_e76": round(float(delta_e76(np.array([rgb_orig]), np.array([rgb_lc]))[0]), 3)
                    if orig_arr is not None else None,
                    "delta_e2000": round(float(delta_e2000(np.array([rgb_orig]), np.array([rgb_lc]))[0]), 3)
                    if orig_arr is not None else None,
                }
            )
        rows.append(item)

    fields = list(rows[0].keys()) if rows else ["name", "x", "y"]
    if args.out:
        with Path(args.out).open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
        passed(f"sample: {len(rows)} point(s) -> {args.out}")
    else:
        writer = csv.DictWriter(sys.stdout, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    return 0


def cmd_measure(args: argparse.Namespace) -> int:
    orig_xml = Path(args.orig_xml) if args.orig_xml else None
    lc_xml = Path(args.lc_xml) if args.lc_xml else None
    if not orig_xml and not lc_xml:
        raise FidelityError("measure needs at least one of --orig-xml / --lc-xml")
    out_dir = Path(args.out) if args.out else Path("findings") / (args.state or (orig_xml or lc_xml).stem)
    summary = measure_pair(orig_xml, lc_xml, out_dir, args.state or out_dir.name)
    if orig_xml and lc_xml:
        passed(
            f"{summary['state']}: controls matched {summary['matched']}, "
            f"only orig {summary['only_orig']}, only lc {summary['only_lc']}, "
            f"max |dx|={summary['max_abs_dx']} max |dy|={summary['max_abs_dy']} "
            f"max |dw|={summary['max_abs_dw']} max |dh|={summary['max_abs_dh']} -> {out_dir}"
        )
    else:
        passed(f"{summary['state']}: control tables -> {out_dir}")
    return 0


def cmd_report(args: argparse.Namespace) -> int:
    root = Path(args.root)
    orig_dir, lc_dir = root / "orig", root / "lc"
    out_dir = Path(args.out) if args.out else root / "findings" / "report"
    if not orig_dir.exists() and not lc_dir.exists():
        raise FidelityError(f"no orig/ or lc/ under {root}")
    states = set()
    for directory in (orig_dir, lc_dir):
        if directory.exists():
            states.update(path.stem for path in directory.glob("*.png") if not path.name.startswith("_"))
    if args.state:
        wanted = set(args.state)
        states = {state for state in states if state in wanted}
    if not states:
        raise FidelityError(f"no *.png pairs found under {root}")

    integrity = {"orig": _scan_image_integrity(orig_dir), "lc": _scan_image_integrity(lc_dir)}
    for side in ("orig", "lc"):
        for group in integrity[side]["duplicates"]:
            failed(
                f"duplicate screenshot across states ({side}): {', '.join(group['states'])} "
                f"share sha256 {group['sha256'][:16]}…; re-capture or mark the state as missing"
            )
        for name in integrity[side]["empty"]:
            failed(f"empty screenshot (0 bytes) ({side}): {name}; remove it or re-capture with a real screenshot")
    integrity_failed = any(
        integrity[side]["duplicates"] or integrity[side]["empty"] for side in ("orig", "lc")
    )

    crop = parse_rect(args.crop, "--crop") if args.crop else None
    ignore_rects = [parse_rect(value, "--ignore-rect") for value in (args.ignore_rect or [])]
    rows = []
    for state in sorted(states):
        orig_png, lc_png = orig_dir / f"{state}.png", lc_dir / f"{state}.png"
        row: dict = {"state": state}
        if not orig_png.exists():
            row.update({"status": "missing-orig", "error": f"missing {orig_png}"})
        elif not lc_png.exists():
            row.update({"status": "missing-lc", "error": f"missing {lc_png}"})
        else:
            try:
                metrics = compare_pair(
                    orig_png, lc_png, out_dir / state, state, args.threshold, crop, (0, 0), ignore_rects
                )
                row.update({"status": "compared", "metrics": metrics})
                orig_xml = orig_dir / f"{state}.xml"
                lc_xml = lc_dir / f"{state}.xml"
                if orig_xml.exists() and lc_xml.exists():
                    row["controls"] = measure_pair(orig_xml, lc_xml, out_dir / state, state)
                elif orig_xml.exists() or lc_xml.exists():
                    info(f"{state}: only one uiautomator dump present; control diff skipped")
            except FidelityError as error:
                row.update({"status": "error", "error": str(error)})
        if "error" in row:
            failed(f"{state}: {row['status']} - {row['error']}")
        rows.append(row)

    write_report(rows, out_dir, root, args.threshold, integrity)
    compared = sum(1 for row in rows if row["status"] == "compared")
    if integrity_failed:
        return 1
    if compared == len(rows):
        return 0
    return 0 if not args.strict else 1


def _run(command: list[str], capture: bool = False) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(command, check=True, capture_output=capture)
    except FileNotFoundError as error:
        raise FidelityError(f"command not found: {command[0]}") from error
    except subprocess.CalledProcessError as error:
        stderr = ""
        if capture and error.stderr:
            stderr = error.stderr.decode("utf-8", "replace").strip()
        raise FidelityError(f"command failed ({error.returncode}): {' '.join(command)} {stderr}") from error


def _adb_command(adb: str, serial: str | None, *parts: str) -> list[str]:
    command = [adb]
    if serial:
        command += ["-s", serial]
    return command + list(parts)


def _resolve_build(args: argparse.Namespace) -> dict | None:
    """LC evidence must record its build provenance (checksheet §5)."""
    if args.side != "lc":
        if args.build_commit or args.apk:
            info("orig side: --build-* / --apk ignored (original build provenance is not applicable)")
        return None
    branch = (args.build_branch or "develop").strip()
    commit = (args.build_commit or "").strip().lower()
    if branch != "develop":
        raise FidelityError(
            f"lc capture build branch must be develop (got {branch!r}); evidence built from another branch is invalid"
        )
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise FidelityError("lc capture requires --build-commit as a 40-char lowercase git sha (checksheet §5)")
    if not args.apk:
        raise FidelityError("lc capture requires --apk <path> so apk.sha256/apk.bytes are recorded (checksheet §5)")
    apk_path = Path(args.apk)
    if not apk_path.exists():
        raise FidelityError(f"apk not found: {apk_path}")
    size = apk_path.stat().st_size
    if size < 100 * 1024:
        raise FidelityError(f"apk looks too small ({size} bytes): {apk_path}")
    return {
        "build": {"branch": branch, "commit": commit},
        "apk": {"path": str(apk_path), "sha256": sha256_file(apk_path), "bytes": size},
    }


def cmd_collect(args: argparse.Namespace) -> int:
    if not STATE_RE.match(args.state):
        raise FidelityError(
            f"state id must be a plain evidence stem (got {args.state!r}); "
            "use the same stem as docs/fidelity/orig, e.g. compass-main-01"
        )
    adb = args.adb
    serial = args.serial or os.environ.get("ANDROID_SERIAL") or None
    side_dir = Path(args.root) / args.side
    png_path = side_dir / f"{args.state}.png"
    xml_path = side_dir / f"{args.state}.xml"
    meta_path = side_dir / f"{args.state}.meta.json"
    if side_dir.exists() and (png_path.exists() or xml_path.exists()) and not args.force:
        raise FidelityError(f"{args.state} already captured for {args.side}; pass --force to overwrite")

    commands: list[list[str]] = []
    if not args.skip_anim:
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            commands.append(_adb_command(adb, serial, "shell", "settings", "put", "global", key, "0"))
    commands.append(_adb_command(adb, serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP"))
    commands.append(_adb_command(adb, serial, "shell", "wm", "dismiss-keyguard"))
    commands.append(_adb_command(adb, serial, "exec-out", "screencap", "-p"))
    if not args.skip_dump:
        remote = f"/sdcard/lc-fidelity-{args.state}.xml"
        commands.append(_adb_command(adb, serial, "shell", "rm", "-f", remote))
        commands.append(_adb_command(adb, serial, "shell", "uiautomator", "dump", remote))
        commands.append(_adb_command(adb, serial, "pull", remote, str(xml_path)))
        commands.append(_adb_command(adb, serial, "shell", "rm", "-f", remote))

    if args.dry_run:
        info(f"dry-run: side={args.side} state={args.state}")
        for command in commands:
            print("[DRY] " + " ".join(command))
        if args.side == "lc":
            info("note: a real lc capture also requires --build-branch develop "
                 "--build-commit <40-hex> --apk <apk>")
        info(f"dry-run: would write {png_path} and {xml_path}")
        return 0

    build = _resolve_build(args)
    side_dir.mkdir(parents=True, exist_ok=True)
    if not args.skip_anim:
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            _run(_adb_command(adb, serial, "shell", "settings", "put", "global", key, "0"))
        passed("animations: 0 (window/transition/animator)")
    _run(_adb_command(adb, serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP"))
    _run(_adb_command(adb, serial, "shell", "wm", "dismiss-keyguard"))

    screenshot = _run(_adb_command(adb, serial, "exec-out", "screencap", "-p"), capture=True)
    data = screenshot.stdout or b""
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise FidelityError(f"screencap did not return a PNG ({len(data)} bytes)")
    png_path.write_bytes(data)
    with Image.open(png_path) as image:
        width, height = image.size
    passed(f"screenshot: {png_path} {width}x{height} {len(data)} B")
    if (width, height) != EXPECTED_SIZE:
        info(f"warning: capture is {width}x{height}, expected {EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]}")

    dump_written = False
    if not args.skip_dump:
        remote = f"/sdcard/lc-fidelity-{args.state}.xml"
        _run(_adb_command(adb, serial, "shell", "rm", "-f", remote))
        _run(_adb_command(adb, serial, "shell", "uiautomator", "dump", remote))
        _run(_adb_command(adb, serial, "pull", remote, str(xml_path)))
        _run(_adb_command(adb, serial, "shell", "rm", "-f", remote))
        if not xml_path.exists() or xml_path.stat().st_size == 0:
            raise FidelityError(f"uiautomator dump is empty: {xml_path}")
        try:
            dump_root = ET.parse(xml_path).getroot()
            packages = {node.attrib.get("package", "") for node in dump_root.iter("node")} - {""}
        except ET.ParseError as error:
            raise FidelityError(f"cannot parse dump {xml_path}: {error}") from error
        if args.pkg and packages and args.pkg not in packages:
            raise FidelityError(
                f"dump package mismatch: {sorted(packages)} does not contain {args.pkg}; "
                "refusing to mix another app's evidence"
            )
        if not packages:
            info("warning: dump has no package attribute; verify it belongs to the target app")
        dump_written = True
        passed(f"dump: {xml_path} {xml_path.stat().st_size} B")

    properties = {}
    for prop in ("ro.product.model", "ro.product.brand", "ro.build.version.release", "ro.build.version.sdk"):
        try:
            result = _run(_adb_command(adb, serial, "shell", "getprop", prop), capture=True)
            properties[prop] = (result.stdout or b"").decode("utf-8", "replace").strip()
        except FidelityError:
            properties[prop] = ""
    meta = {
        "tool": "fidelity",
        "version": VERSION,
        "state": args.state,
        "side": args.side,
        "captured_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "serial": serial or "",
        "package": args.pkg or "",
        "checksheet_id": args.checksheet_id or "",
        "build": build["build"] if build else None,
        "apk": build["apk"] if build else None,
        "image": {
            "file": png_path.name,
            "width": width,
            "height": height,
            "sha256": sha256_file(png_path),
        },
        "dump": {
            "file": xml_path.name,
            "sha256": sha256_file(xml_path),
        } if dump_written else None,
        "device": properties,
        "notes": "raw capture only; states/animations are set by the operator before this command",
    }
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    passed(f"meta: {meta_path}")
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="fidelity.py",
        description="LC fidelity toolkit: align, diff, sample, measure and report original-vs-ours screenshots.",
    )
    parser.add_argument("--version", action="version", version=f"%(prog)s {VERSION}")
    sub = parser.add_subparsers(dest="command", required=True)

    pair = sub.add_parser("pair", help="compare one original/LC screenshot pair")
    pair.add_argument("--orig", required=True)
    pair.add_argument("--lc", required=True)
    pair.add_argument("--state", help="state id (default: original filename stem)")
    pair.add_argument("--out", help="output directory (default: findings/<state>)")
    pair.add_argument("--threshold", type=int, default=DEFAULT_THRESHOLD,
                      help="max per-channel |delta| counted as a difference (default 8)")
    pair.add_argument("--crop", help="common crop x,y,w,h applied to both images")
    pair.add_argument("--offset", default="0,0", help="shift LC by dx,dy before comparison")
    pair.add_argument("--ignore-rect", action="append", metavar="x,y,w,h",
                      help="exclude a rectangle from metrics (repeatable; recorded in metrics.json)")
    pair.set_defaults(func=cmd_pair)

    sample = sub.add_parser("sample", help="sample colours at points or a grid")
    sample.add_argument("--orig", required=True)
    sample.add_argument("--lc")
    sample.add_argument("--points", help='x,y[,name];x,y[,name] (semicolon separated)')
    sample.add_argument("--grid", help="COLSxROWS, e.g. 6x8")
    sample.add_argument("--out", help="write CSV instead of stdout")
    sample.set_defaults(func=cmd_sample)

    measure = sub.add_parser("measure", help="parse uiautomator dumps and diff control bounds")
    measure.add_argument("--orig-xml")
    measure.add_argument("--lc-xml")
    measure.add_argument("--state")
    measure.add_argument("--out", help="output directory (default: findings/<state>)")
    measure.set_defaults(func=cmd_measure)

    report = sub.add_parser("report", help="batch-compare docs/fidelity/{orig,lc} and build HTML")
    report.add_argument("--root", default="docs/fidelity")
    report.add_argument("--out", help="report directory (default: <root>/findings/report)")
    report.add_argument("--threshold", type=int, default=DEFAULT_THRESHOLD)
    report.add_argument("--crop", help="common crop x,y,w,h applied to both images")
    report.add_argument("--ignore-rect", action="append", metavar="x,y,w,h")
    report.add_argument("--state", action="append", help="only these state ids (repeatable)")
    report.add_argument("--strict", action="store_true", help="exit 1 when any pair is not compared")
    report.set_defaults(func=cmd_report)

    collect = sub.add_parser("collect", help="capture screenshot + dump + meta from a device")
    collect.add_argument("--root", default="docs/fidelity")
    collect.add_argument("--side", required=True, choices=["orig", "lc"])
    collect.add_argument("--state", required=True)
    collect.add_argument("--serial")
    collect.add_argument("--pkg", help="package name recorded in meta.json and used to validate the dump")
    collect.add_argument("--checksheet-id", help="checksheet row id (PG-xx / ST-xx-...) recorded in meta.json")
    collect.add_argument("--build-branch", default="develop", help="build source branch (must be develop; default)")
    collect.add_argument("--build-commit", help="required for --side lc: 40-char git sha of the build source")
    collect.add_argument("--apk", help="required for --side lc: APK path; sha256/bytes recorded in meta")
    collect.add_argument("--adb", default="adb")
    collect.add_argument("--skip-anim", action="store_true", help="do not force animation scales to 0")
    collect.add_argument("--skip-dump", action="store_true")
    collect.add_argument("--force", action="store_true", help="overwrite an existing capture")
    collect.add_argument("--dry-run", action="store_true", help="print adb commands without executing")
    collect.set_defaults(func=cmd_collect)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if np is None:
        info("numpy not installed: dE76/bbox are unavailable, RGB deltas still work")
    try:
        return args.func(args)
    except FidelityError as error:
        failed(str(error))
        return 1


if __name__ == "__main__":
    sys.exit(main())
