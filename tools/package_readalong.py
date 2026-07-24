#!/usr/bin/env python3
"""Build a Muse Read-Along package from audiobook workflow outputs."""

from __future__ import annotations

import argparse
import json
import os
import re
import zipfile
from collections import OrderedDict
from pathlib import Path

AUDIO_SUFFIXES = (".m4a", ".m4b", ".mp3", ".ogg", ".opus", ".wav")


def read_alignment(path: Path) -> OrderedDict[str, dict]:
    chapters: OrderedDict[str, dict] = OrderedDict()
    with path.open("r", encoding="utf-8") as stream:
        for line_number, raw in enumerate(stream, 1):
            line = raw.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"alignment.jsonl line {line_number} is invalid JSON: {exc}") from exc
            chapter_id = str(item.get("chapter_id", "")).strip()
            if not chapter_id:
                raise ValueError(f"alignment.jsonl line {line_number} has no chapter_id")
            locator = item.get("source_locator") or {}
            chapter = chapters.setdefault(
                chapter_id,
                {
                    "id": chapter_id,
                    "title": chapter_id,
                    "index": len(chapters),
                    "href": locator.get("epub_href", ""),
                },
            )
            if locator.get("epub_href") and not chapter["href"]:
                chapter["href"] = locator["epub_href"]
    if not chapters:
        raise ValueError("alignment.jsonl has no alignment records")
    return chapters


def find_audio(audio_dir: Path, chapter_id: str) -> Path:
    candidates = [audio_dir / f"{chapter_id}{suffix}" for suffix in AUDIO_SUFFIXES]
    candidates.extend(sorted(audio_dir.glob(f"{chapter_id}.*")))
    for candidate in candidates:
        if candidate.is_file() and candidate.suffix.lower() in AUDIO_SUFFIXES:
            return candidate
    raise FileNotFoundError(f"no chapter audio found for {chapter_id} in {audio_dir}")


def safe_zip_name(name: str) -> str:
    normalized = name.replace("\\", "/").lstrip("/")
    if not normalized or normalized == "." or ".." in Path(normalized).parts:
        raise ValueError(f"unsafe archive path: {name}")
    return normalized


def add_file(archive: zipfile.ZipFile, source: Path, target: str) -> None:
    target = safe_zip_name(target)
    compression = zipfile.ZIP_STORED if source.suffix.lower() in AUDIO_SUFFIXES else zipfile.ZIP_DEFLATED
    archive.write(source, target, compress_type=compression)


def build_package(epub: Path, alignment: Path, audio_dir: Path, output: Path, title: str, author: str, provenance: Path | None) -> None:
    if not epub.is_file() or epub.suffix.lower() != ".epub":
        raise ValueError(f"EPUB not found or extension is not .epub: {epub}")
    if not alignment.is_file():
        raise FileNotFoundError(alignment)
    if not audio_dir.is_dir():
        raise NotADirectoryError(audio_dir)

    chapters = read_alignment(alignment)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    if temporary.exists():
        temporary.unlink()
    try:
        with zipfile.ZipFile(temporary, "w") as archive:
            add_file(archive, epub, "book.epub")
            add_file(archive, alignment, "alignment.jsonl")
            if provenance is not None:
                add_file(archive, provenance, "provenance.json")
            for chapter in chapters.values():
                audio = find_audio(audio_dir, chapter["id"])
                chapter["audio"] = f"audio/{audio.name}"
                add_file(archive, audio, chapter["audio"])
            manifest = {
                "version": 1,
                "title": title,
                "author": author,
                "epub": "book.epub",
                "alignment": "alignment.jsonl",
                "audio_root": "audio",
                "chapters": list(chapters.values()),
            }
            archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        os.replace(temporary, output)
    finally:
        if temporary.exists():
            temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--epub", type=Path, required=True)
    parser.add_argument("--alignment", type=Path, required=True)
    parser.add_argument("--audio-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--title", default=None)
    parser.add_argument("--author", default="")
    parser.add_argument("--provenance", type=Path, default=None)
    args = parser.parse_args()
    build_package(
        epub=args.epub,
        alignment=args.alignment,
        audio_dir=args.audio_dir,
        output=args.output,
        title=args.title or args.epub.stem,
        author=args.author,
        provenance=args.provenance,
    )
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
