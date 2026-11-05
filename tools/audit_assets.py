#!/usr/bin/env python3
"""Audita as licencas dos assets distribuidos com o Orbital.

Uso:
    python tools/audit_assets.py            # regenera o manifesto
    python tools/audit_assets.py --check    # valida e falha se houver pendencia

O script le as regras de tools/asset_license_rules.json e, quando disponivel,
os metadados dos audios com mutagen. Sem mutagen ele usa apenas as regras por
caminho/artista, mantendo a verificacao funcional.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RULES_PATH = ROOT / "tools" / "asset_license_rules.json"
MANIFEST_PATH = ROOT / "THIRD_PARTY_LICENSES" / "manifest.csv"

AUDIO_EXTENSIONS = {".ogg", ".mp3", ".wav"}

# Diretorios cujo conteudo e integralmente GPLv3 (Anuken/comunidade Orbital).
GPL_DIRECTORIES = [
    ("sprites", "core/assets/sprites"),
    ("baseparts", "core/assets/baseparts"),
    ("maps", "core/assets/maps"),
    ("shaders", "core/assets/shaders"),
    ("bloomshaders", "core/assets/bloomshaders"),
    ("cubemaps", "core/assets/cubemaps"),
    ("bundles", "core/assets/bundles"),
    ("planets", "core/assets/planets"),
    ("scripts", "core/assets/scripts"),
    ("config", "core/assets/config"),
    ("cursors", "core/assets/cursors"),
    ("icones-do-jogo", "core/assets/icons"),
]


def clean(value: object) -> str:
    """Remove BOM e espacos residuais de tags de audio."""
    if value is None:
        return ""
    text = str(value).replace("\ufeff", "").strip()
    return text


def load_rules() -> dict:
    with RULES_PATH.open(encoding="utf-8") as handle:
        return json.load(handle)


def read_audio_metadata(path: Path) -> dict:
    """Le autor/titulo do audio. Retorna vazio se mutagen nao estiver instalado."""
    try:
        import mutagen  # type: ignore
    except Exception:
        return {}

    try:
        audio = mutagen.File(str(path), easy=True)
        if audio is None:
            return {}
        return {
            "artist": clean((audio.get("artist") or [""])[0]),
            "title": clean((audio.get("title") or [""])[0]),
        }
    except Exception:
        return {}


def match_artist(rules: dict, artist: str) -> dict | None:
    if not artist:
        return None
    lowered = artist.casefold()
    for entry in rules["sounds"]["artists"]:
        if entry["match"].casefold() in lowered:
            return entry
    return None


def sound_entry(rules: dict, path: Path) -> dict:
    relative = path.relative_to(ROOT / "core" / "assets" / "sounds").as_posix()
    metadata = read_audio_metadata(path)
    artist = metadata.get("artist", "")
    title = metadata.get("title", "")

    custom = rules["sounds"]["paths"].get(relative)
    if custom:
        return {
            "autor": custom.get("author", artist or "-"),
            "licenca": custom["license"],
            "origem": custom["source"],
            "status": custom["status"],
        }

    if artist:
        rule = match_artist(rules, artist)
        if rule is None:
            return {
                "autor": artist,
                "licenca": "REVISAR",
                "origem": f"Autor nao mapeado nas regras: {artist}",
                "status": "REVISAR",
            }
        return {
            "autor": artist,
            "licenca": rule["license"],
            "origem": rule["source"],
            "status": rule["status"],
        }

    default = rules["sounds"]["default"]
    return {
        "autor": "Anuken/Mindustry",
        "licenca": default["license"],
        "origem": default["source"],
        "status": default["status"],
    }


def collect_asset_rows(rules: dict) -> list[dict]:
    rows: list[dict] = []

    # Musica
    music_dir = ROOT / "core" / "assets" / "music"
    for path in sorted(music_dir.iterdir()):
        if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS:
            default = rules["music"]["default"]
            rows.append({
                "categoria": "musica",
                "arquivo": path.relative_to(ROOT).as_posix(),
                "quantidade": 1,
                "autor": "Anuken",
                "licenca": default["license"],
                "origem": default["source"],
                "status": default["status"],
            })

    # Efeitos sonoros
    sounds_dir = ROOT / "core" / "assets" / "sounds"
    for path in sorted(sounds_dir.rglob("*")):
        if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS:
            entry = sound_entry(rules, path)
            rows.append({
                "categoria": "som",
                "arquivo": path.relative_to(ROOT).as_posix(),
                "quantidade": 1,
                **entry,
            })

    # Fontes
    fonts_dir = ROOT / "core" / "assets" / "fonts"
    for path in sorted(fonts_dir.iterdir()):
        if not path.is_file():
            continue
        rule = rules["fonts"].get(path.name)
        if rule is None:
            rows.append({
                "categoria": "fonte",
                "arquivo": path.relative_to(ROOT).as_posix(),
                "quantidade": 1,
                "autor": "-",
                "licenca": "REVISAR",
                "origem": "Fonte sem regra de licenca registrada",
                "status": "REVISAR",
            })
            continue
        rows.append({
            "categoria": "fonte",
            "arquivo": path.relative_to(ROOT).as_posix(),
            "quantidade": 1,
            "autor": rule["author"],
            "licenca": rule["license"],
            "origem": rule["source"],
            "status": rule["status"],
        })

    # Icones: uma linha por conjunto de origem
    config_path = ROOT / "core" / "assets-raw" / "fontgen" / "config.json"
    if config_path.exists():
        with config_path.open(encoding="utf-8") as handle:
            config = json.load(handle)
        counts: dict[str, int] = {}
        for glyph in config.get("glyphs", []):
            if not glyph.get("selected", True):
                continue
            source = glyph.get("src", "")
            counts[source] = counts.get(source, 0) + 1

        for source, count in sorted(counts.items()):
            rule = rules["icons"].get(source)
            if rule is None:
                rows.append({
                    "categoria": "icone",
                    "arquivo": f"core/assets/fonts/icon.ttf#{source}",
                    "quantidade": count,
                    "autor": "-",
                    "licenca": "REVISAR",
                    "origem": f"Conjunto de icones sem regra registrada: {source}",
                    "status": "REVISAR",
                })
                continue
            rows.append({
                "categoria": "icone",
                "arquivo": f"core/assets/fonts/icon.ttf#{source}",
                "quantidade": count,
                "autor": rule["author"],
                "licenca": rule["license"],
                "origem": rule["source"],
                "status": rule["status"],
            })

    # Conteudo GPLv3 em lote (sprites, mapas, shaders, etc.)
    default = rules["default"]
    for category, relative in GPL_DIRECTORIES:
        directory = ROOT / relative
        if not directory.exists():
            continue
        count = sum(1 for path in directory.rglob("*") if path.is_file())
        rows.append({
            "categoria": category,
            "arquivo": relative + "/",
            "quantidade": count,
            "autor": "Anuken/Mindustry e colaboradores",
            "licenca": default["license"],
            "origem": default["source"],
            "status": default["status"],
        })

    rows.sort(key=lambda row: (row["categoria"], row["arquivo"].casefold()))
    return rows


def write_manifest(rows: list[dict]) -> None:
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = ["categoria", "arquivo", "quantidade", "autor", "licenca", "origem", "status"]
    with MANIFEST_PATH.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def check(rows: list[dict], allowed: set[str]) -> int:
    problems = []
    for row in rows:
        if row["status"] != "OK":
            problems.append(f"{row['arquivo']}: status {row['status']}")
        elif row["licenca"] not in allowed:
            problems.append(f"{row['arquivo']}: licenca nao permitida ({row['licenca']})")

    if problems:
        print("Falha na auditoria de licencas:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    return 0


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

    parser = argparse.ArgumentParser(description="Auditoria de licencas dos assets do Orbital")
    parser.add_argument("--check", action="store_true", help="falha se houver asset pendente")
    args = parser.parse_args()

    if not RULES_PATH.exists():
        print(f"Regras nao encontradas: {RULES_PATH}", file=sys.stderr)
        return 2

    rules = load_rules()
    rows = collect_asset_rows(rules)
    if not args.check:
        write_manifest(rows)

    totals: dict[str, int] = {}
    for row in rows:
        totals[row["categoria"]] = totals.get(row["categoria"], 0) + int(row["quantidade"])

    print(f"Manifesto: {MANIFEST_PATH.relative_to(ROOT)}")
    print(f"Linhas: {len(rows)}")
    for category in sorted(totals):
        print(f"  {category}: {totals[category]}")

    if args.check:
        result = check(rows, set(rules["allowed"]))
        if result == 0:
            print("OK: todos os assets possuem licenca registrada.")
        return result

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
