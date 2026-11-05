#!/usr/bin/env python3
"""Substitui glifos de licenca incompativel por equivalentes MIT.

Os desenhos vem do Bootstrap Icons (MIT). O script preserva codigos Unicode,
nomes de glifos e avanços horizontais em icon.ttf e font.woff.

Uso:
    python tools/replace_mit_glyphs.py --icons-dir <pasta com SVGs do Bootstrap Icons>

Este script e uma ferramenta de manutencao: os arquivos resultantes ja estao
versionados no repositorio. Ele existe para documentar e reproduzir a troca.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path import parse_path
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
FONTS = [
    ROOT / "core" / "assets" / "fonts" / "icon.ttf",
    ROOT / "core" / "assets" / "fonts" / "font.woff",
]
CONFIG = ROOT / "core" / "assets-raw" / "fontgen" / "config.json"

# nome CSS no config.json -> nome do arquivo SVG no Bootstrap Icons
GLYPH_MAP = {
    "home": "house",
    "image": "image",
    "star": "star",
    "resize": "arrows-angle-expand",
    "wrench": "wrench-adjustable",
    "add": "plus-lg",
    "edit": "pencil",
    "chart-bar": "bar-chart",
    "plane-outline": "airplane",
    "map": "map",
    "rotate": "arrow-repeat",
    "play": "play-fill",
    "pause": "pause-fill",
    "list": "list",
    "move": "arrows-move",
    "undo": "arrow-counterclockwise",
    "redo": "arrow-clockwise",
    "right-open-out": "box-arrow-right",
    "right-open": "chevron-right",
    "waves": "water",
    "power_": "power",
    "rain": "cloud-rain",
    "cloud-sun": "cloud-sun",
    "drizzle": "cloud-drizzle",
    "cloud-flash": "cloud-lightning",
    "cloud-wind": "wind",
    "chart-alt": "graph-up",
    "github_": "github",
    "warning": "exclamation-triangle",
}

SVG_PATH_RE = re.compile(r'<path[^>]*\sd="([^"]+)"')


def svg_paths(svg_file: Path) -> list[str]:
    text = svg_file.read_text(encoding="utf-8")
    paths = SVG_PATH_RE.findall(text)
    if not paths:
        raise RuntimeError(f"Nenhum <path> encontrado em {svg_file}")
    return paths


def build_glyph(svg_file: Path, advance: int, ascent: int = 850, descent: int = -150,
                fill: float = 0.85, upm: int = 1000):
    icon_size = (ascent - descent) * fill
    scale = icon_size / 16.0
    offset_x = (advance - icon_size) / 2.0
    offset_y = descent + ((ascent - descent) - icon_size) / 2.0 + icon_size

    pen = TTGlyphPen(None)
    quad_pen = Cu2QuPen(pen, max_err=0.8, reverse_direction=True)
    transform_pen = TransformPen(quad_pen, (scale, 0, 0, -scale, offset_x, offset_y))
    for path_data in svg_paths(svg_file):
        parse_path(path_data, transform_pen)
    return pen.glyph()


def replace_in_font(font_path: Path, icons_dir: Path, glyphs: list[dict]) -> int:
    font = TTFont(str(font_path))
    cmap = font.getBestCmap()
    glyf = font["glyf"]
    hmtx = font["hmtx"]
    replaced = 0

    for glyph in glyphs:
        code = glyph["code"]
        source = GLYPH_MAP[glyph["css"]]
        glyph_name = cmap.get(code)
        if glyph_name is None:
            raise RuntimeError(f"Codigo U+{code:04X} nao encontrado em {font_path.name}")

        advance, _ = hmtx[glyph_name]
        new_glyph = build_glyph(icons_dir / f"{source}.svg", advance)
        glyf[glyph_name] = new_glyph
        new_glyph.recalcBounds(glyf)
        hmtx[glyph_name] = (advance, new_glyph.xMin)
        replaced += 1

    font.save(str(font_path))
    return replaced


def main() -> int:
    parser = argparse.ArgumentParser(description="Substitui glifos por equivalentes MIT")
    parser.add_argument("--icons-dir", required=True, type=Path,
                        help="pasta com os SVGs do Bootstrap Icons")
    args = parser.parse_args()

    icons_dir = args.icons_dir.resolve()
    if not icons_dir.is_dir():
        print(f"Pasta de icones invalida: {icons_dir}", file=sys.stderr)
        return 2

    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    glyphs = [g for g in config["glyphs"] if g.get("css") in GLYPH_MAP]
    if len(glyphs) != len(GLYPH_MAP):
        found = {g["css"] for g in glyphs}
        missing = sorted(set(GLYPH_MAP) - found)
        print(f"Glifos ausentes no config.json: {missing}", file=sys.stderr)
        return 2

    for font_path in FONTS:
        count = replace_in_font(font_path, icons_dir, glyphs)
        print(f"{font_path.relative_to(ROOT)}: {count} glifos substituidos")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
