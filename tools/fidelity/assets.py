#!/usr/bin/env python3
"""Reproducible extraction + sha256 manifest for original-APK assets.

Every file under docs/fidelity/assets/ must be traceable to one entry of the
original APK (space.xinzhi.globe 1.6.1) and carry a sha256, so Stage 1 evidence
can be re-checked by anyone with the same APK:

    python tools/fidelity/assets.py list-apk --apk <apk> --size 46x188
    python tools/fidelity/assets.py extract  --apk <apk> --sources docs/fidelity/assets-sources.json
    python tools/fidelity/assets.py manifest --dir docs/fidelity/assets
    python tools/fidelity/assets.py verify   --dir docs/fidelity/assets --apk <apk>

Only the Python standard library is used; PNG dimensions come from the IHDR
chunk, so extraction works without PIL/aapt2 and without touching the device.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import tempfile
import zipfile
from pathlib import Path

VERSION = "1.0.0"
MANIFEST_NAME = "MANIFEST.sha256"
PROVENANCE_NAME = "PROVENANCE.json"
INDEX_NAME = "INDEX.md"
RESERVED = {MANIFEST_NAME, PROVENANCE_NAME, INDEX_NAME}
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


class AssetError(RuntimeError):
    """A reproducible tool failure with a user-facing message."""


def info(message: str) -> None:
    print(f"[ -- ] {message}")


def passed(message: str) -> None:
    print(f"[PASS] {message}")


def failed(message: str) -> None:
    print(f"[FAIL] {message}")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def png_size(data: bytes) -> tuple[int, int] | None:
    """Read width/height straight from IHDR: no PIL dependency for the manifest."""
    if len(data) < 24 or not data.startswith(PNG_MAGIC) or data[12:16] != b"IHDR":
        return None
    width, height = struct.unpack(">II", data[16:24])
    return int(width), int(height)


def entry_name(entry: zipfile.ZipInfo) -> str:
    """APK zip names are UTF-8; zipfile decodes unflagged names as cp437."""
    if entry.flag_bits & 0x800:
        return entry.filename
    try:
        return entry.filename.encode("cp437").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return entry.filename


def safe_target(target: str) -> str:
    """Reject absolute paths and escapes so an extraction cannot write outside --out."""
    candidate = Path(target.replace("\\", "/"))
    if candidate.is_absolute() or ".." in candidate.parts or not candidate.parts:
        raise AssetError(f"target must be a relative path inside --out: {target!r}")
    return "/".join(candidate.parts)


def read_manifest(directory: Path) -> tuple[dict[str, str], list[str]]:
    """Parse sha256sum output ('<hex>  <path>'), tolerating a leading '*' binary marker."""
    path = directory / MANIFEST_NAME
    if not path.exists():
        raise AssetError(f"manifest not found: {path}; run the manifest command first")
    entries: dict[str, str] = {}
    errors: list[str] = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        digest, _, name = line.partition("  ")
        if not name and "*" in line:
            digest, _, name = line.partition(" *")
        digest, name = digest.strip().lower(), name.strip()
        if len(digest) != 64 or not name:
            errors.append(f"{MANIFEST_NAME}:{number}: expected '<sha256>  <path>'")
            continue
        if name in entries:
            errors.append(f"{MANIFEST_NAME}:{number}: duplicate entry for {name}")
            continue
        entries[name] = digest
    return entries, errors


def load_provenance(directory: Path) -> dict[str, dict]:
    path = directory / PROVENANCE_NAME
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise AssetError(f"cannot parse {path}: {error}") from error
    items = payload.get("items", payload)
    if isinstance(items, list):
        return {safe_target(str(item["target"])): item for item in items}
    if isinstance(items, dict):
        return {safe_target(str(key)): value for key, value in items.items()}
    raise AssetError(f"{path}: expected an items list or mapping")


def write_provenance(directory: Path, items: dict[str, dict], apk: Path | None) -> None:
    payload = {
        "tool": "fidelity-assets",
        "version": VERSION,
        "apk": {"path": str(apk) if apk else None, "sha256": sha256_file(apk) if apk else None},
        "items": [items[key] for key in sorted(items)],
    }
    (directory / PROVENANCE_NAME).write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def iter_assets(directory: Path) -> list[Path]:
    files = []
    for path in sorted(directory.rglob("*")):
        if path.is_file() and path.relative_to(directory).as_posix() not in RESERVED:
            files.append(path)
    return files


def apk_entries(path: Path) -> dict[str, zipfile.ZipInfo]:
    try:
        with zipfile.ZipFile(path) as archive:
            return {entry_name(entry): entry for entry in archive.infolist()}
    except (OSError, zipfile.BadZipFile) as error:
        raise AssetError(f"cannot read APK {path}: {error}") from error


# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

def cmd_list_apk(args: argparse.Namespace) -> int:
    apk = Path(args.apk)
    entries = apk_entries(apk)
    size = None
    if args.size:
        match = str(args.size).lower().split("x")
        if len(match) != 2 or not all(part.isdigit() for part in match):
            raise AssetError("--size must be WIDTHxHEIGHT, e.g. 46x188")
        size = (int(match[0]), int(match[1]))
    rows = []
    with zipfile.ZipFile(apk) as archive:
        for name, entry in sorted(entries.items()):
            if args.grep and args.grep.lower() not in name.lower():
                continue
            if args.ext and not name.lower().endswith(f".{args.ext.lower().lstrip('.')}"):
                continue
            if size is not None:
                if not name.lower().endswith(".png"):
                    continue
                measured = png_size(archive.read(entry))
                if measured != size:
                    continue
            rows.append((name, entry, archive.read(entry)))
    if not rows:
        raise AssetError("no APK entry matches the given filters")
    for name, entry, data in rows[: args.limit]:
        dimensions = png_size(data)
        shape = f"{dimensions[0]}x{dimensions[1]}" if dimensions else "-"
        digest = sha256_bytes(data) if args.sha else sha256_bytes(data)[:12]
        print(f"{digest}  {entry.file_size:>9}  {shape:>10}  {name}")
    if len(rows) > args.limit:
        info(f"{len(rows)} entries matched; showing the first {args.limit} (raise --limit)")
    passed(f"list-apk: {len(rows)} entry(ies) matched in {apk}")
    return 0


def cmd_extract(args: argparse.Namespace) -> int:
    apk = Path(args.apk)
    out = Path(args.out)
    sources_path = Path(args.sources)
    if not sources_path.exists():
        raise AssetError(f"sources file not found: {sources_path}")
    try:
        sources = json.loads(sources_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise AssetError(f"cannot parse {sources_path}: {error}") from error
    items = sources.get("items") or []
    if not items:
        raise AssetError(f"{sources_path}: no items")

    apk_sha = sha256_file(apk)
    pinned = (sources.get("apk_sha256") or "").strip().lower()
    if pinned and pinned != apk_sha:
        raise AssetError(
            f"APK sha256 mismatch: sources pin {pinned[:12]}…, file is {apk_sha[:12]}…; "
            "use the pinned original APK (space.xinzhi.globe 1.6.1)"
        )
    info(f"apk: {apk} sha256 {apk_sha}")

    entries = apk_entries(apk)
    wanted: list[tuple[str, zipfile.ZipInfo]] = []
    missing: list[str] = []
    targets: dict[str, str] = {}
    for item in items:
        name = str(item.get("entry", ""))
        target = safe_target(str(item.get("target", "")))
        if not name or not target:
            raise AssetError(f"each item needs 'entry' and 'target': {item!r}")
        if target in targets:
            raise AssetError(f"duplicate target {target!r} (also from {targets[target]})")
        targets[target] = name
        if name not in entries:
            missing.append(name)
        else:
            wanted.append((target, entries[name]))
    if missing:
        for name in missing:
            failed(f"entry not in APK: {name}")
        raise AssetError(f"{len(missing)} source entry(ies) missing from {apk}")

    provenance = load_provenance(out) if (out / PROVENANCE_NAME).exists() else {}
    written = identical = 0
    with zipfile.ZipFile(apk) as archive:
        for target, entry in wanted:
            data = archive.read(entry)
            destination = out / target
            digest = sha256_bytes(data)
            if destination.exists():
                current = sha256_file(destination)
                if current == digest:
                    identical += 1
                elif not args.force:
                    raise AssetError(
                        f"{target} exists with a different sha256 ({current[:12]}… != {digest[:12]}…); "
                        "re-run with --force to overwrite"
                    )
            if not destination.exists() or sha256_file(destination) != digest:
                if args.dry_run:
                    info(f"would write {target} ({len(data)} bytes, sha256 {digest[:12]}…)")
                else:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(data)
                    written += 1
            dimensions = png_size(data)
            provenance[target] = {
                "target": target,
                "entry": entry_name(entry),
                "apk_sha256": apk_sha,
                "entry_sha256": digest,
                "bytes": len(data),
                "image": {"width": dimensions[0], "height": dimensions[1]} if dimensions else None,
                "kind": str(item_kind(items, target) or ""),
                "note": str(item_note(items, target) or ""),
            }
            shape = f", {dimensions[0]}x{dimensions[1]}" if dimensions else ""
            passed(f"extract {target} <- {entry_name(entry)} ({len(data)} B{shape}, sha256 {digest[:12]}…)")
    if args.dry_run:
        info("dry-run: nothing written")
        return 0
    out.mkdir(parents=True, exist_ok=True)
    write_provenance(out, provenance, apk if not args.no_apk_record else None)
    info(f"extract: {written} written, {identical} already identical, {len(wanted)} item(s)")
    return 0


def item_kind(items: list[dict], target: str) -> str | None:
    for item in items:
        if safe_target(str(item.get("target", ""))) == target:
            return item.get("kind")
    return None


def item_note(items: list[dict], target: str) -> str | None:
    for item in items:
        if safe_target(str(item.get("target", ""))) == target:
            return item.get("note")
    return None


def cmd_manifest(args: argparse.Namespace) -> int:
    directory = Path(args.dir)
    if not directory.exists():
        raise AssetError(f"assets directory not found: {directory}")
    provenance = load_provenance(directory)
    files = iter_assets(directory)
    if not files:
        raise AssetError(f"no asset files under {directory} (nothing but {', '.join(sorted(RESERVED))})")
    lines = []
    rows = []
    missing_provenance = []
    empty = []
    total = 0
    for path in files:
        relative = path.relative_to(directory).as_posix()
        data = path.read_bytes()
        total += len(data)
        if not data:
            empty.append(relative)
        digest = sha256_bytes(data)
        lines.append(f"{digest}  {relative}")
        dimensions = png_size(data)
        record = provenance.get(relative)
        if record is None:
            missing_provenance.append(relative)
        rows.append({
            "target": relative,
            "bytes": len(data),
            "image": f"{dimensions[0]}x{dimensions[1]}" if dimensions else "-",
            "sha256": digest,
            "entry": (record or {}).get("entry", "-"),
            "kind": (record or {}).get("kind", "-"),
        })
    (directory / MANIFEST_NAME).write_text("\n".join(lines) + "\n", encoding="utf-8")
    md = [
        "# 原版素材索引（自动生成，勿手改）",
        "",
        f"- 工具：`tools/fidelity/assets.py` v{VERSION}",
        f"- 文件数：**{len(files)}**，合计 **{total}** 字节",
        f"- 校验：`python tools\\fidelity\\assets.py verify --dir docs\\fidelity\\assets`",
        "",
        "| # | 文件（`docs/fidelity/assets/` 下） | 字节 | 尺寸 | sha256 | APK 内条目 | 类别 |",
        "|---|---|---|---|---|---|---|",
    ]
    for number, row in enumerate(rows, 1):
        md.append(
            f"| {number} | `{row['target']}` | {row['bytes']} | {row['image']} | "
            f"`{row['sha256'][:16]}…` | `{row['entry']}` | {row['kind']} |"
        )
    (directory / INDEX_NAME).write_text("\n".join(md) + "\n", encoding="utf-8")
    passed(f"manifest: {len(files)} file(s), {total} bytes -> {MANIFEST_NAME} + {INDEX_NAME}")
    for relative in empty:
        failed(f"empty asset (0 bytes): {relative}")
    for relative in missing_provenance:
        failed(f"no provenance (APK entry + sha256) for: {relative}")
    if empty or missing_provenance:
        return 1
    return 0


def cmd_verify(args: argparse.Namespace) -> int:
    directory = Path(args.dir)
    if not directory.exists():
        raise AssetError(f"assets directory not found: {directory}")
    manifest, parse_errors = read_manifest(directory)
    provenance = load_provenance(directory)
    problems = list(parse_errors)

    actual = {}
    for path in iter_assets(directory):
        relative = path.relative_to(directory).as_posix()
        data = path.read_bytes()
        actual[relative] = data
        if not data:
            problems.append(f"empty asset (0 bytes): {relative}")

    if args.require_min and len(actual) < args.require_min:
        problems.append(f"only {len(actual)} asset(s); --require-min {args.require_min}")
    if not actual:
        problems.append("no asset files (manifest would be empty)")

    for relative, digest in sorted(manifest.items()):
        data = actual.get(relative)
        if data is None:
            problems.append(f"missing file (listed in {MANIFEST_NAME}): {relative}")
            continue
        if sha256_bytes(data) != digest:
            problems.append(f"sha256 mismatch: {relative}")
    for relative in sorted(actual):
        if relative not in manifest:
            problems.append(f"unlisted file (no manifest entry): {relative}")

    duplicates: dict[str, list[str]] = {}
    for relative, data in actual.items():
        duplicates.setdefault(sha256_bytes(data), []).append(relative)
    for digest, names in sorted(duplicates.items()):
        if len(names) > 1:
            message = f"duplicate asset content across targets: {', '.join(sorted(names))} ({digest[:12]}…)"
            if args.strict:
                problems.append(message)
            else:
                info(f"warning: {message}")

    apk = Path(args.apk) if args.apk else None
    archive = None
    if apk:
        if not apk.exists():
            raise AssetError(f"APK not found: {apk}")
        apk_sha = sha256_file(apk)
        info(f"apk: {apk} sha256 {apk_sha}")
        archive = zipfile.ZipFile(apk)
        entries = {entry_name(entry): entry for entry in archive.infolist()}

    try:
        for relative, data in sorted(actual.items()):
            record = provenance.get(relative)
            if record is None:
                problems.append(f"no provenance for: {relative}")
                continue
            digest = sha256_bytes(data)
            if record.get("entry_sha256") and record["entry_sha256"] != digest:
                problems.append(
                    f"provenance entry_sha256 != file sha256: {relative} "
                    f"({str(record['entry_sha256'])[:12]}… vs {digest[:12]}…)"
                )
            dimensions = png_size(data)
            recorded = record.get("image") or {}
            if dimensions and recorded:
                if (recorded.get("width"), recorded.get("height")) != dimensions:
                    problems.append(
                        f"recorded size {recorded.get('width')}x{recorded.get('height')} "
                        f"!= PNG {dimensions[0]}x{dimensions[1]}: {relative}"
                    )
            if record.get("bytes") is not None and record["bytes"] != len(data):
                problems.append(f"recorded bytes {record['bytes']} != {len(data)}: {relative}")
            if archive is not None:
                name = record.get("entry")
                if not name:
                    problems.append(f"provenance has no APK entry: {relative}")
                    continue
                entry = entries.get(name)
                if entry is None:
                    problems.append(f"APK entry not found: {name} (for {relative})")
                    continue
                source = archive.read(entry)
                if sha256_bytes(source) != digest:
                    problems.append(f"file does not match its APK entry: {relative} <- {name}")
                if record.get("apk_sha256") and record["apk_sha256"] != apk_sha:
                    problems.append(
                        f"provenance apk_sha256 is for a different APK: {relative} "
                        f"({str(record['apk_sha256'])[:12]}… != {apk_sha[:12]}…)"
                    )
    finally:
        if archive is not None:
            archive.close()

    for problem in problems:
        failed(problem)
    summary = f"verify: {len(actual)} file(s), {len(manifest)} manifest entr(ies), {len(provenance)} provenance row(s)"
    if problems:
        info(summary + f", {len(problems)} problem(s)")
        return 1
    passed(summary + "; sha256 + provenance + APK source all match")
    return 0


def cmd_selftest(args: argparse.Namespace) -> int:
    checks: list[tuple[bool, str]] = []

    def check(condition: bool, message: str) -> None:
        checks.append((bool(condition), message))
        print(("[PASS] " if condition else "[FAIL] ") + message)

    with tempfile.TemporaryDirectory() as raw:
        temp = Path(raw)
        apk = temp / "fake.apk"
        html = "assets/compassIntroduce/先天八卦_卦位.html"
        png = make_png(46, 188)
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr(html, "<html>卦位</html>")
            archive.writestr("res/a1.png", png)
            archive.writestr("assets/plain.txt", "plain")
        sources = temp / "sources.json"
        sources.write_text(json.dumps({
            "apk_sha256": sha256_file(apk),
            "items": [
                {"entry": html, "target": "compass-introduce/先天八卦_卦位.html", "kind": "knowledge-html"},
                {"entry": "res/a1.png", "target": "compass/pointer.png", "kind": "bitmap"},
            ],
        }, ensure_ascii=False), encoding="utf-8")
        out = temp / "assets"

        code = cmd_extract(argparse.Namespace(
            apk=str(apk), sources=str(sources), out=str(out), dry_run=True, force=False,
            no_apk_record=False,
        ))
        check(code == 0 and not out.exists(), "extract: --dry-run writes nothing")

        code = cmd_extract(argparse.Namespace(
            apk=str(apk), sources=str(sources), out=str(out), dry_run=False, force=False,
            no_apk_record=False,
        ))
        check(code == 0 and (out / "compass/pointer.png").exists(), "extract: files written")
        record = load_provenance(out).get("compass/pointer.png", {})
        check(record.get("entry") == "res/a1.png", "extract: provenance records the APK entry")
        check(record.get("image") == {"width": 46, "height": 188}, "extract: provenance records PNG size")
        check(record.get("apk_sha256") == sha256_file(apk), "extract: provenance pins the APK sha256")

        code = cmd_extract(argparse.Namespace(
            apk=str(apk), sources=str(sources), out=str(out), dry_run=False, force=False,
            no_apk_record=False,
        ))
        check(code == 0, "extract: re-running is idempotent (identical files skipped)")

        code = cmd_manifest(argparse.Namespace(dir=str(out)))
        check(code == 0 and (out / MANIFEST_NAME).exists(), "manifest: MANIFEST.sha256 written")
        check("先天八卦_卦位.html" in (out / INDEX_NAME).read_text(encoding="utf-8"), "manifest: INDEX.md lists the asset")
        code = cmd_verify(argparse.Namespace(dir=str(out), apk=str(apk), strict=True, require_min=2))
        check(code == 0, "verify: clean tree with APK source passes")

        (out / "compass/pointer.png").write_bytes(make_png(1, 1))
        code = cmd_verify(argparse.Namespace(dir=str(out), apk=str(apk), strict=False, require_min=0))
        check(code == 1, "verify: tampered file fails")
        code = cmd_extract(argparse.Namespace(
            apk=str(apk), sources=str(sources), out=str(out), dry_run=False, force=True,
            no_apk_record=False,
        ))
        check(code == 0, "extract: --force restores a tampered file")

        (out / "compass/empty.png").write_bytes(b"")
        code = cmd_manifest(argparse.Namespace(dir=str(out)))
        check(code == 1, "manifest: 0-byte asset is refused")
        (out / "compass/empty.png").unlink()

        (out / "stray.txt").write_text("not from the APK\n", encoding="utf-8")
        code = cmd_verify(argparse.Namespace(dir=str(out), apk=str(apk), strict=False, require_min=0))
        check(code == 1, "verify: unlisted file fails")
        (out / "stray.txt").unlink()

        (out / "compass/copy-of-pointer.png").write_bytes((out / "compass/pointer.png").read_bytes())
        code = cmd_verify(argparse.Namespace(dir=str(out), apk=str(apk), strict=True, require_min=0))
        check(code == 1, "verify --strict: duplicated content fails")
        (out / "compass/copy-of-pointer.png").unlink()

        bad_sources = temp / "bad.json"
        bad_sources.write_text(json.dumps({"apk_sha256": "0" * 64, "items": []}), encoding="utf-8")
        try:
            cmd_extract(argparse.Namespace(
                apk=str(apk), sources=str(bad_sources), out=str(out), dry_run=True, force=False,
                no_apk_record=False,
            ))
            check(False, "extract: mismatched APK pin is refused")
        except AssetError:
            check(True, "extract: mismatched APK pin is refused")

        bogus = temp / "bogus.json"
        bogus.write_text(json.dumps({"items": [{"entry": "res/does-not-exist.png", "target": "x.png"}]}),
                         encoding="utf-8")
        try:
            cmd_extract(argparse.Namespace(
                apk=str(apk), sources=str(bogus), out=str(out), dry_run=False, force=False,
                no_apk_record=False,
            ))
            check(False, "extract: missing APK entry is refused")
        except AssetError:
            check(True, "extract: missing APK entry is refused")

        mojibake = "先天八卦_卦位.html".encode("utf-8").decode("cp437")
        entry = zipfile.ZipInfo(mojibake)
        entry.flag_bits = 0
        check(entry_name(entry) == "先天八卦_卦位.html", "entry_name: cp437-decoded APK names are repaired")

        code = cmd_list_apk(argparse.Namespace(apk=str(apk), grep="a1", ext="png", size="46x188", limit=5, sha=True))
        check(code == 0, "list-apk: size filter finds the pointer bitmap")

    failures = [message for ok, message in checks if not ok]
    print()
    if failures:
        for message in failures:
            failed(f"selftest: {message}")
        return 1
    print(f"SELFTEST: PASS ({len(checks)} checks)")
    return 0


def make_png(width: int, height: int) -> bytes:
    """Minimal valid PNG (no compression beyond zlib) for selftest fixtures."""
    import zlib

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    raw = b"".join(b"\x00" + b"\x10\x20\x30" * width for _ in range(height))
    return (
        PNG_MAGIC
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="assets.py",
        description="Reproducible original-APK asset extraction + sha256 manifest for docs/fidelity/assets/.",
    )
    parser.add_argument("--version", action="version", version=f"%(prog)s {VERSION}")
    commands = parser.add_subparsers(dest="command", required=True)

    listing = commands.add_parser("list-apk", help="find APK entries by name / extension / pixel size")
    listing.add_argument("--apk", required=True)
    listing.add_argument("--grep", help="case-insensitive substring match on the entry path")
    listing.add_argument("--ext", help="only this extension, e.g. png")
    listing.add_argument("--size", help="only PNGs of this size, e.g. 46x188")
    listing.add_argument("--limit", type=int, default=40)
    listing.add_argument("--sha", action="store_true", help="print full sha256 instead of 12 chars")
    listing.set_defaults(func=cmd_list_apk)

    extract = commands.add_parser("extract", help="extract the entries listed in a sources JSON")
    extract.add_argument("--apk", required=True)
    extract.add_argument("--sources", required=True, help="JSON: {apk_sha256, items:[{entry,target,kind,note}]}")
    extract.add_argument("--out", default="docs/fidelity/assets")
    extract.add_argument("--dry-run", action="store_true")
    extract.add_argument("--force", action="store_true", help="overwrite files whose sha256 differs")
    extract.add_argument("--no-apk-record", action="store_true", help="do not record the APK path (CI)")
    extract.set_defaults(func=cmd_extract)

    manifest = commands.add_parser("manifest", help="write MANIFEST.sha256 + INDEX.md from the asset tree")
    manifest.add_argument("--dir", default="docs/fidelity/assets")
    manifest.set_defaults(func=cmd_manifest)

    verify = commands.add_parser("verify", help="re-check manifest, provenance and (optionally) the APK source")
    verify.add_argument("--dir", default="docs/fidelity/assets")
    verify.add_argument("--apk", help="also re-extract each entry from this APK and compare sha256")
    verify.add_argument("--strict", action="store_true", help="duplicate asset content across targets fails")
    verify.add_argument("--require-min", type=int, default=0, help="fail when fewer files exist")
    verify.set_defaults(func=cmd_verify)

    selftest = commands.add_parser("selftest", help="hermetic self-test on a synthetic APK")
    selftest.set_defaults(func=cmd_selftest)
    return parser


def main(argv: list[str] | None = None) -> int:
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except AssetError as error:
        failed(str(error))
        return 1


if __name__ == "__main__":
    sys.exit(main())
