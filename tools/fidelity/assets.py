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
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

VERSION = "1.2.0"
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


def image_size(data: bytes) -> tuple[int, int] | None:
    """PNG IHDR or WebP header: sizes for the manifest without PIL."""
    if data.startswith(PNG_MAGIC) and len(data) >= 24 and data[12:16] == b"IHDR":
        width, height = struct.unpack(">II", data[16:24])
        return int(width), int(height)
    if len(data) >= 30 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        chunk = data[12:16]
        if chunk == b"VP8X" and len(data) >= 30:
            width = int.from_bytes(data[24:27], "little") + 1
            height = int.from_bytes(data[27:30], "little") + 1
            return width, height
        if chunk == b"VP8 " and len(data) >= 30 and data[23:26] == b"\x9d\x01\x2a":
            width = int.from_bytes(data[26:28], "little") & 0x3FFF
            height = int.from_bytes(data[28:30], "little") & 0x3FFF
            return width, height
        if chunk == b"VP8L" and len(data) >= 25 and data[20] == 0x2F:
            bits = int.from_bytes(data[21:25], "little")
            width = (bits & 0x3FFF) + 1
            height = ((bits >> 14) & 0x3FFF) + 1
            return width, height
    return None


def png_size(data: bytes) -> tuple[int, int] | None:
    """PNG-only size (kept for callers that must reject non-PNG payloads)."""
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


def repo_root() -> Path:
    """tools/fidelity/assets.py -> repository root (for --globs in the deps file)."""
    return Path(__file__).resolve().parents[2]


def load_deps(path: Path) -> dict:
    if not path.exists():
        raise AssetError(f"deps file not found: {path}")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise AssetError(f"cannot parse {path}: {error}") from error
    items = payload.get("items") or []
    if not items:
        raise AssetError(f"{path}: no items")
    seen = set()
    for item in items:
        identifier = str(item.get("id", "")).strip()
        if not identifier:
            raise AssetError(f"{path}: every item needs an id (A-xx)")
        if identifier in seen:
            raise AssetError(f"{path}: duplicate dependency id {identifier}")
        seen.add(identifier)
        if not item.get("requires") and not item.get("manual"):
            raise AssetError(f"{path}: {identifier} needs 'requires' or 'manual'")
    return payload


def _dep_matches(requirement: dict, actual: dict[str, bytes], provenance: dict[str, dict],
                 root: Path) -> tuple[list[str], list[str]]:
    """Return (matched relative paths, notes) for one dependency requirement."""
    matched: list[str] = []
    notes: list[str] = []
    targets = [safe_target(str(value)) for value in requirement.get("targets") or []]
    prefixes = [str(value).replace("\\", "/") for value in requirement.get("prefixes") or []]
    kinds = [str(value) for value in requirement.get("kinds") or []]
    for relative in sorted(actual):
        record = provenance.get(relative) or {}
        if relative in targets:
            matched.append(relative)
            continue
        if any(relative.startswith(prefix) for prefix in prefixes):
            matched.append(relative)
            continue
        if str(record.get("kind") or "") in kinds:
            matched.append(relative)
    for pattern in requirement.get("globs") or []:
        for path in sorted(root.glob(str(pattern))):
            if path.is_file():
                relative = path.relative_to(root).as_posix()
                if relative not in matched:
                    matched.append(relative)
    image = requirement.get("image")
    if image:
        wanted = str(image).lower()
        kept = []
        for relative in matched:
            dimensions = image_size(actual[relative])
            actual_size = f"{dimensions[0]}x{dimensions[1]}" if dimensions else None
            if actual_size == wanted:
                kept.append(relative)
            else:
                notes.append(f"{relative} 尺寸 {actual_size or '非 PNG'} != {wanted}")
        matched = kept
    return matched, notes


def check_deps(deps: dict, directory: Path, actual: dict[str, bytes],
               provenance: dict[str, dict]) -> tuple[list[dict], list[dict]]:
    """Per A-xx coverage: (rows, manual items). Each row carries its own pass/fail detail."""
    root = repo_root()
    rows: list[dict] = []
    manuals: list[dict] = []
    for item in deps.get("items", []):
        identifier = str(item["id"])
        name = str(item.get("name", ""))
        if item.get("manual"):
            manuals.append(item)
            continue
        requirement = item.get("requires") or {}
        matched, notes = _dep_matches(requirement, actual, provenance, root)
        required = int(requirement.get("count", 1) or 1)
        hint = []
        if requirement.get("kinds"):
            hint.append("kind " + "/".join(requirement["kinds"]))
        if requirement.get("prefixes"):
            hint.append("prefix " + "/".join(requirement["prefixes"]))
        if requirement.get("targets"):
            hint.append("target " + "/".join(str(value) for value in requirement["targets"]))
        if requirement.get("globs"):
            hint.append("glob " + "/".join(str(value) for value in requirement["globs"]))
        if requirement.get("image"):
            hint.append(f"尺寸 {requirement['image']}")
        rows.append({
            "id": identifier,
            "name": name,
            "ok": len(matched) >= required,
            "matched": matched,
            "required": required,
            "hint": " | ".join(hint),
            "notes": notes,
        })
    return rows, manuals


def print_deps(rows: list[dict], manuals: list[dict], source: str,
               available_kinds: list[str] | None = None) -> list[str]:
    """Print the A-xx table; return the ids that are not yet satisfied."""
    total = len(rows) + len(manuals)
    info(f"deps: {source}（{total} 项）")
    missing: list[str] = []
    for row in rows:
        label = f"{row['id']} {row['name']}".strip()
        detail = f"{len(row['matched'])}/{row['required']}"
        if row["matched"]:
            detail += "：" + ", ".join(row["matched"][:6]) + (" …" if len(row["matched"]) > 6 else "")
        if row["ok"]:
            passed(f"deps {label}: {detail}")
        else:
            missing.append(row["id"])
            if row["hint"]:
                detail += f"（{row['hint']}）"
            if row["notes"]:
                detail += "；" + "；".join(row["notes"][:3])
            elif not row["matched"] and available_kinds:
                detail += "；树里现有 kind: " + ", ".join(available_kinds[:8])
            failed(f"deps {label}: {detail}")
    for item in manuals:
        info(f"deps {item['id']} {item.get('name', '')}: 手工判据 — {item['manual']}")
    manual_ids = ", ".join(str(item["id"]) for item in manuals) or "无"
    asset_ok = len(rows) - len(missing)
    line = f"deps: 素材 {asset_ok}/{len(rows)} 齐"
    if missing:
        line += f"；缺 {', '.join(missing)}"
    line += f"；手工判据 {len(manuals)} 项（{manual_ids}）"
    if missing:
        failed(line)
    else:
        passed(line)
    return missing


def apk_entries(path: Path) -> dict[str, zipfile.ZipInfo]:
    try:
        with zipfile.ZipFile(path) as archive:
            return {entry_name(entry): entry for entry in archive.infolist()}
    except (OSError, zipfile.BadZipFile) as error:
        raise AssetError(f"cannot read APK {path}: {error}") from error


def find_aapt2() -> str | None:
    """aapt2 resolves a resource id to the (possibly obfuscated) APK entry path."""
    found = shutil.which("aapt2")
    if found:
        return found
    candidates: list[Path] = []
    for root in (
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
        os.environ.get("LOCALAPPDATA", "") and str(Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk"),
    ):
        if not root:
            continue
        build_tools = Path(root) / "build-tools"
        if build_tools.is_dir():
            candidates.extend(sorted(build_tools.glob("*/aapt2.exe"), reverse=True))
            candidates.extend(sorted(build_tools.glob("*/aapt2"), reverse=True))
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    return None


def resource_entry_map(apk: Path, aapt2: str | None = None) -> dict[str, dict]:
    """Map '0x7f0803da' -> {name, entry} via `aapt2 dump resources` (read-only)."""
    tool = aapt2 or find_aapt2()
    if not tool:
        return {}
    try:
        result = subprocess.run(
            [tool, "dump", "resources", str(apk)],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=300,
        )
    except (OSError, subprocess.SubprocessError):
        return {}
    if result.returncode != 0:
        return {}
    mapping: dict[str, dict] = {}
    current: str | None = None
    for line in result.stdout.splitlines():
        match = re.match(r"\s*resource\s+(0x[0-9a-fA-F]{8})\s+(\S+)", line)
        if match:
            current = match.group(1).lower()
            mapping[current] = {"name": match.group(2), "entry": None}
            continue
        if current:
            file_match = re.search(r"\(file\)\s+(\S+)\s+type=", line)
            if file_match:
                mapping[current]["entry"] = file_match.group(1)
                current = None
    return {key: value for key, value in mapping.items() if value.get("entry")}


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
                if not name.lower().endswith((".png", ".webp", ".jpg", ".jpeg")):
                    continue
                measured = image_size(archive.read(entry))
                if measured != size:
                    continue
            rows.append((name, entry, archive.read(entry)))
    if not rows:
        raise AssetError("no APK entry matches the given filters")
    for name, entry, data in rows[: args.limit]:
        dimensions = image_size(data)
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
    decoded_skipped: list[str] = []
    targets: dict[str, str] = {}
    for item in items:
        name = str(item.get("entry", ""))
        target = safe_target(str(item.get("target", "")))
        if not name or not target:
            raise AssetError(f"each item needs 'entry' and 'target': {item!r}")
        if target in targets:
            raise AssetError(f"duplicate target {target!r} (also from {targets[target]})")
        targets[target] = name
        if item.get("decoded") or item.get("decode"):
            # Compiled resources (vector drawables, binary XML) cannot be copied byte-for-byte:
            # they go through a decoder first and are registered by the stamp command.
            decoded_skipped.append(target)
            continue
        if name not in entries:
            missing.append(name)
        else:
            wanted.append((target, entries[name]))
    if missing:
        for name in missing:
            failed(f"entry not in APK: {name}")
        raise AssetError(f"{len(missing)} source entry(ies) missing from {apk}")
    for target in decoded_skipped:
        info(f"decoded item skipped (decode it, then run stamp): {target}")

    provenance = load_provenance(out) if (out / PROVENANCE_NAME).exists() else {}
    by_target = by_target_map(items)
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
            dimensions = image_size(data)
            source_item = by_target.get(target) or {}
            provenance[target] = {
                "target": target,
                "mode": "verbatim",
                "entry": entry_name(entry),
                "apk_sha256": apk_sha,
                "entry_sha256": digest,
                "bytes": len(data),
                "image": {"width": dimensions[0], "height": dimensions[1]} if dimensions else None,
                "kind": str(source_item.get("kind") or ""),
                "note": str(source_item.get("note") or ""),
                "meta": item_meta(source_item),
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


def by_target_map(items: list[dict]) -> dict[str, dict]:
    mapping: dict[str, dict] = {}
    for item in items:
        try:
            mapping[safe_target(str(item.get("target", "")))] = item
        except AssetError:
            continue
    return mapping


def item_meta(item: dict) -> dict | None:
    """Reviewer anchors from sources (resource/raster/size/use/enum/evidence/checksheet …)."""
    meta = {
        key: value for key, value in item.items()
        if key not in ("entry", "target", "kind", "note", "decoded", "decode") and value not in (None, "")
    }
    return meta or None


def cmd_stamp(args: argparse.Namespace) -> int:
    """Register decoded resources (vector drawables / binary XML) that cannot be copied verbatim.

    Contract (director, M2 Stage 1): provenance records three anchors — the sha256 of the
    original APK entry, the sha256 of the decoded file, and the resource id — plus the
    decoder name and version, because different apktool versions produce different XML.
    """
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
    decode = sources.get("decode") or {}
    apk_sha = sha256_file(apk)
    pinned = (sources.get("apk_sha256") or "").strip().lower()
    if pinned and pinned != apk_sha:
        raise AssetError(
            f"APK sha256 mismatch: sources pin {pinned[:12]}…, file is {apk_sha[:12]}…; "
            "use the pinned original APK (space.xinzhi.globe 1.6.1)"
        )
    entries = apk_entries(apk)
    provenance = load_provenance(out) if (out / PROVENANCE_NAME).exists() else {}
    stamped = problems = 0
    id_map: dict[str, dict] | None = None
    aapt2 = find_aapt2()
    with zipfile.ZipFile(apk) as archive:
        for item in items:
            if not (item.get("decoded") or item.get("decode")):
                continue
            target = safe_target(str(item.get("target", "")))
            raw_entry = str(item.get("entry", "")).strip()
            decoder = str(item.get("decoder") or decode.get("name") or decode.get("tool") or "").strip()
            decoder_version = str(item.get("decoder_version") or decode.get("version") or "").strip()
            resource_id = ""
            entry = None
            if re.fullmatch(r"0[xX][0-9a-fA-F]{8}", raw_entry):
                resource_id = raw_entry.lower()
                if id_map is None:
                    id_map = resource_entry_map(apk, aapt2)
                resolved = id_map.get(resource_id) or {}
                entry_path = resolved.get("entry") or str(item.get("apk_entry") or "").strip()
                entry = entries.get(entry_path) if entry_path else None
                if entry is None:
                    failed(
                        f"cannot resolve {resource_id} to an APK entry (for {target}); "
                        "pass apk_entry (e.g. res/Uy.xml) or make aapt2 available"
                    )
                    problems += 1
                    continue
            else:
                entry = entries.get(raw_entry)
                if entry is None:
                    failed(f"entry not in APK: {raw_entry!r} (for {target}); use a res entry or a 0x id")
                    problems += 1
                    continue
                entry_path = raw_entry
            if not decoder or not decoder_version:
                failed(
                    f"decoder name/version missing for {target}; "
                    "write decoder + decoder_version (top-level decode block or per item)"
                )
                problems += 1
                continue
            pinned_tool = str(decode.get("name") or decode.get("tool") or "").strip()
            pinned_version = str(decode.get("version") or "").strip()
            if pinned_tool and decoder != pinned_tool:
                failed(f"decoder mismatch for {target}: item {decoder!r} != pin {pinned_tool!r}")
                problems += 1
                continue
            if pinned_version and decoder_version != pinned_version:
                failed(
                    f"decoder version mismatch for {target}: item {decoder_version!r} "
                    f"!= pin {pinned_version!r}"
                )
                problems += 1
                continue
            destination = out / target
            if not destination.exists():
                failed(f"decoded file missing: {target}; decode it into that path first")
                problems += 1
                continue
            data = destination.read_bytes()
            if not data:
                failed(f"decoded file is empty (0 bytes): {target}")
                problems += 1
                continue
            dimensions = image_size(data)
            provenance[target] = {
                "target": target,
                "mode": "decoded",
                "entry": entry_path,
                "resource_id": resource_id or None,
                "apk_sha256": apk_sha,
                "entry_sha256": sha256_bytes(archive.read(entry)),
                "file_sha256": sha256_bytes(data),
                "bytes": len(data),
                "image": {"width": dimensions[0], "height": dimensions[1]} if dimensions else None,
                "kind": str(item.get("kind") or ""),
                "note": str(item.get("note") or ""),
                "meta": item_meta(item),
                "decoder": decoder,
                "decoder_version": decoder_version,
            }
            stamped += 1
            bound = f"{resource_id} -> {entry_path}" if resource_id else entry_path
            passed(f"stamp {target} <- {bound} ({decoder} {decoder_version}, {len(data)} B, sha256 {sha256_bytes(data)[:12]}…)")
    out.mkdir(parents=True, exist_ok=True)
    write_provenance(out, provenance, apk if not args.no_apk_record else None)
    info(f"stamp: {stamped} decoded item(s) recorded, {problems} problem(s)")
    return 1 if problems else 0


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
        dimensions = image_size(data)
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
            "resource": ((record or {}).get("meta") or {}).get("resource", "-"),
            "use": ((record or {}).get("meta") or {}).get("use", "-"),
            "carrier": (
                "矢量/解码" if str((record or {}).get("mode") or "") == "decoded"
                else ("位图" if image_size(data) else "文件")
            ),
        })
    (directory / MANIFEST_NAME).write_text("\n".join(lines) + "\n", encoding="utf-8")
    md = [
        "# 原版素材索引（自动生成，勿手改）",
        "",
        f"- 工具：`tools/fidelity/assets.py` v{VERSION}",
        f"- 文件数：**{len(files)}**，合计 **{total}** 字节",
        f"- 校验：`python tools\\fidelity\\assets.py verify --dir docs\\fidelity\\assets`",
        "",
        "| # | 文件（`docs/fidelity/assets/` 下） | 载体 | 字节 | 尺寸 | sha256 | APK 内条目 | 资源名 | 用途 | 类别 |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for number, row in enumerate(rows, 1):
        md.append(
            f"| {number} | `{row['target']}` | {row['carrier']} | {row['bytes']} | {row['image']} | "
            f"`{row['sha256'][:16]}…` | `{row['entry']}` | {row['resource']} | {row['use']} | {row['kind']} |"
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
            mode = str(record.get("mode") or "verbatim")
            if mode == "decoded":
                if record.get("file_sha256") and record["file_sha256"] != digest:
                    problems.append(
                        f"decoded file sha256 != recorded file_sha256: {relative} "
                        f"({str(record['file_sha256'])[:12]}… vs {digest[:12]}…)"
                    )
            elif record.get("entry_sha256") and record["entry_sha256"] != digest:
                problems.append(
                    f"provenance entry_sha256 != file sha256: {relative} "
                    f"({str(record['entry_sha256'])[:12]}… vs {digest[:12]}…)"
                )
            dimensions = image_size(data)
            recorded = record.get("image") or {}
            if dimensions and recorded:
                if (recorded.get("width"), recorded.get("height")) != dimensions:
                    problems.append(
                        f"recorded size {recorded.get('width')}x{recorded.get('height')} "
                        f"!= PNG {dimensions[0]}x{dimensions[1]}: {relative}"
                    )
            if record.get("bytes") is not None and record["bytes"] != len(data):
                problems.append(f"recorded bytes {record['bytes']} != {len(data)}: {relative}")
            if mode == "decoded":
                if not record.get("decoder") or not record.get("decoder_version"):
                    problems.append(f"decoded asset missing decoder name/version: {relative}")
                if not record.get("entry") or not record.get("entry_sha256"):
                    problems.append(f"decoded asset missing original entry sha256: {relative}")
                resource_id = str(record.get("resource_id") or "")
                if resource_id and not re.fullmatch(r"0x[0-9a-f]{8}", resource_id):
                    problems.append(f"invalid resource id {resource_id!r}: {relative}")
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
                source_digest = sha256_bytes(source)
                if mode == "verbatim":
                    if source_digest != digest:
                        problems.append(f"file does not match its APK entry: {relative} <- {name}")
                elif record.get("entry_sha256") and source_digest != record["entry_sha256"]:
                    problems.append(f"APK entry changed since stamp (sha256 differs): {relative} <- {name}")
                if record.get("apk_sha256") and record["apk_sha256"] != apk_sha:
                    problems.append(
                        f"provenance apk_sha256 is for a different APK: {relative} "
                        f"({str(record['apk_sha256'])[:12]}… != {apk_sha[:12]}…)"
                    )
    finally:
        if archive is not None:
            archive.close()
    if archive is None and any(
        str((record or {}).get("mode") or "") == "decoded" for record in provenance.values()
    ):
        info("decoded entries: original-entry sha256 not re-checked (pass --apk to re-check it)")

    deps_missing: list[str] = []
    deps_path = Path(args.deps) if getattr(args, "deps", None) else directory.parent / "assets-deps.json"
    if not getattr(args, "no_deps", False):
        if deps_path.exists():
            deps = load_deps(deps_path)
            rows, manuals = check_deps(deps, directory, actual, provenance)
            available = sorted({
                str((record or {}).get("kind") or "") for record in provenance.values()
            } - {""})
            deps_missing = print_deps(rows, manuals, str(deps.get("plan") or deps_path), available)
        else:
            info(f"warning: deps file not found ({deps_path}); A-xx coverage not checked")

    for problem in problems:
        failed(problem)
    summary = f"verify: {len(actual)} file(s), {len(manifest)} manifest entr(ies), {len(provenance)} provenance row(s)"
    problem_count = len(problems) + (1 if deps_missing else 0)
    if problem_count:
        info(summary + f", {problem_count} problem(s)")
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
            # Compiled vector drawable: binary AXML magic; decoded output can never match these bytes.
            archive.writestr("res/drawable/svg_layer.xml", b"\x03\x00\x08\x00" + b"\x00" * 16)
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

        def verify_text(**overrides) -> tuple[int, str]:
            import contextlib
            import io

            namespace = {
                "dir": str(out), "apk": str(apk), "strict": False, "require_min": 0,
                "deps": None, "no_deps": False,
            }
            namespace.update(overrides)
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                result = cmd_verify(argparse.Namespace(**namespace))
            return result, buffer.getvalue()

        partial_deps = temp / "deps-partial.json"
        partial_deps.write_text(json.dumps({
            "plan": "selftest §6.1",
            "items": [
                {"id": "A-01", "name": "pointer", "requires": {"targets": ["compass/pointer.png"], "image": "46x188"}},
                {"id": "A-02", "name": "drawer", "requires": {"kinds": ["icon-drawer"], "count": 2}},
                {"id": "A-10", "name": "re-capture", "manual": "selftest manual"},
            ],
        }, ensure_ascii=False), encoding="utf-8")
        code, text = verify_text(deps=str(partial_deps))
        check(code == 1, "verify --deps: incomplete A-xx dependency fails")
        check("deps A-01 pointer: 1/1" in text, "verify --deps: satisfied dependency reported")
        check("缺 A-02" in text, "verify --deps: missing dependency listed in the summary")
        check("A-10 re-capture: 手工判据" in text, "verify --deps: manual item reported, not gated")
        code, text = verify_text(deps=str(partial_deps), no_deps=True)
        check(code == 0, "verify --no-deps: integrity-only run stays green")

        complete_deps = temp / "deps-complete.json"
        complete_deps.write_text(json.dumps({"items": [
            {"id": "A-01", "name": "pointer", "requires": {"targets": ["compass/pointer.png"], "image": "46x188"}},
        ]}, ensure_ascii=False), encoding="utf-8")
        code, text = verify_text(deps=str(complete_deps))
        check(code == 0 and "素材 1/1 齐" in text, "verify --deps: complete dependency set passes")

        wrong_size = temp / "deps-size.json"
        wrong_size.write_text(json.dumps({"items": [
            {"id": "A-01", "name": "pointer", "requires": {"targets": ["compass/pointer.png"], "image": "46x46"}},
        ]}, ensure_ascii=False), encoding="utf-8")
        code, text = verify_text(deps=str(wrong_size))
        check(code == 1 and "尺寸" in text, "verify --deps: image size requirement is enforced")

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

        decoded_sources = temp / "sources-decoded.json"
        decoded_sources.write_text(json.dumps({
            "apk_sha256": sha256_file(apk),
            "decode": {"tool": "apktool", "version": "selftest", "command": "apktool d -f"},
            "items": [{
                "entry": "res/drawable/svg_layer.xml", "target": "drawer/svg_layer.xml",
                "kind": "icon-drawer", "decoded": True, "resource": "svg_layer", "use": "图层",
            }],
        }, ensure_ascii=False), encoding="utf-8")
        (out / "drawer").mkdir(parents=True, exist_ok=True)
        code = cmd_stamp(argparse.Namespace(apk=str(apk), sources=str(decoded_sources), out=str(out),
                                            no_apk_record=False))
        check(code == 1, "stamp: decoded file must exist before stamping")
        (out / "drawer/svg_layer.xml").write_text("<vector/>\n", encoding="utf-8")
        code = cmd_stamp(argparse.Namespace(apk=str(apk), sources=str(decoded_sources), out=str(out),
                                            no_apk_record=False))
        check(code == 0, "stamp: decoded resource registered")
        record = load_provenance(out)["drawer/svg_layer.xml"]
        check(record.get("mode") == "decoded" and record.get("entry") == "res/drawable/svg_layer.xml",
              "stamp: decoded mode + APK entry recorded")
        check((record.get("meta") or {}).get("resource") == "svg_layer", "stamp: source anchors kept in meta")
        code = cmd_manifest(argparse.Namespace(dir=str(out)))
        check(code == 0, "manifest: decoded resource included")
        code, text = verify_text(deps=str(complete_deps))
        check(code == 0, "verify: decoded resource passes with APK source check")
        (out / "drawer/svg_layer2.xml").write_text("<vector/>\n", encoding="utf-8")
        id_sources = temp / "sources-resid.json"
        id_sources.write_text(json.dumps({
            "apk_sha256": sha256_file(apk),
            "decode": {"tool": "apktool", "version": "selftest"},
            "items": [{
                "entry": "0x7f0803da", "apk_entry": "res/drawable/svg_layer.xml",
                "target": "drawer/svg_layer2.xml",
                "kind": "icon-drawer", "decoded": True, "resource": "svg_layer",
            }],
        }, ensure_ascii=False), encoding="utf-8")
        code = cmd_stamp(argparse.Namespace(apk=str(apk), sources=str(id_sources), out=str(out),
                                            no_apk_record=False))
        check(code == 0, "stamp: resource-id binding accepted for decoded resources")
        code = cmd_manifest(argparse.Namespace(dir=str(out)))
        code, text = verify_text(deps=str(complete_deps))
        check(code == 0, "verify: resource-id decoded asset passes")

        mismatch = temp / "sources-mismatch.json"
        mismatch.write_text(json.dumps({
            "apk_sha256": sha256_file(apk),
            "decode": {"tool": "apktool", "version": "2.9.3"},
            "items": [{
                "entry": "res/drawable/svg_layer.xml", "target": "drawer/svg_layer.xml",
                "kind": "icon-drawer", "decoded": True, "decoder": "apktool",
                "decoder_version": "2.8.0",
            }],
        }, ensure_ascii=False), encoding="utf-8")
        code = cmd_stamp(argparse.Namespace(apk=str(apk), sources=str(mismatch), out=str(out),
                                            no_apk_record=False))
        check(code == 1, "stamp: decoder version mismatch is rejected")
        (out / "drawer/svg_layer.xml").write_text("<vector><path/></vector>\n", encoding="utf-8")
        code, text = verify_text(deps=str(complete_deps))
        check(code == 1 and "decoded file sha256" in text, "verify: tampered decoded file fails")

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

    stamp = commands.add_parser(
        "stamp", help="register decoded resources (vector drawables / binary XML) already decoded into --out"
    )
    stamp.add_argument("--apk", required=True)
    stamp.add_argument("--sources", required=True)
    stamp.add_argument("--out", default="docs/fidelity/assets")
    stamp.add_argument("--no-apk-record", action="store_true")
    stamp.set_defaults(func=cmd_stamp)

    manifest = commands.add_parser("manifest", help="write MANIFEST.sha256 + INDEX.md from the asset tree")
    manifest.add_argument("--dir", default="docs/fidelity/assets")
    manifest.set_defaults(func=cmd_manifest)

    verify = commands.add_parser("verify", help="re-check manifest, provenance and (optionally) the APK source")
    verify.add_argument("--dir", default="docs/fidelity/assets")
    verify.add_argument("--apk", help="also re-extract each entry from this APK and compare sha256")
    verify.add_argument("--strict", action="store_true", help="duplicate asset content across targets fails")
    verify.add_argument("--require-min", type=int, default=0, help="fail when fewer files exist")
    verify.add_argument("--deps", help="A-xx dependency file (default: <dir>/../assets-deps.json)")
    verify.add_argument("--no-deps", action="store_true", help="skip the A-xx coverage report")
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
