#!/usr/bin/env python3
"""Generate the approved original Orbital SFX set as uncompressed WAV.

No third-party audio assets or Python packages are used. By default, outputs
are written to build/mit-sound-stage for review. Pass --install to move the
current CC0 OGG files into the ignored staging backup and install the WAVs.
"""

from __future__ import annotations

import argparse
import math
import random
import shutil
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "core" / "assets" / "sounds"
STAGE = ROOT / "build" / "mit-sound-stage"
RATE = 22050

TARGETS = {
    "block": [
        "blockBreak1", "blockBreak2", "blockBreak3", "blockRepair", "blockRotate",
        "click", "coreLand", "coreLaunch", "door", "drillImpact", "shockwaveTower", "unitCreate",
    ],
    "environment": ["rain"],
    "explosions": ["unitExplode1", "unitExplode2", "unitExplode3", "wreckFall", "wreckFallBig"],
    "loops": [
        "loopCombustion", "loopHover", "loopHover2", "loopMachine", "loopMachine2",
        "loopMachineSpin", "loopMalign", "loopThruster", "loopUnitBuilding",
    ],
    "shoot": [
        "shootArtillery", "shootArtillerySap", "shootArtillerySapBig", "shootArtillerySmall",
        "shootCyclone", "shootDisperse", "shootHorizon", "shootReign", "shootRipple",
        "shootScatter", "shootScepter",
    ],
    "ui": ["waveSpawn"],
}


def seed_for(name: str) -> int:
    return sum((i + 1) * byte for i, byte in enumerate(name.encode("utf-8")))


def write_wav(path: Path, data: list[float]) -> None:
    peak = max((abs(x) for x in data), default=0.0) or 1.0
    gain = min(0.82 / peak, 1.0)
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        pcm = (struct.pack("<h", round(max(-1.0, min(1.0, x * gain)) * 32767)) for x in data)
        output.writeframes(b"".join(pcm))


def transient(name: str, duration: float, start_hz: float, end_hz: float, noise: float, harmonics: tuple[float, ...]) -> list[float]:
    rng = random.Random(seed_for(name))
    count = round(RATE * duration)
    phases = [0.0] * len(harmonics)
    out: list[float] = []
    for i in range(count):
        t = i / RATE
        progress = t / duration
        hz = start_hz + (end_hz - start_hz) * progress
        env = (1.0 - math.exp(-t * 95.0)) * math.exp(-t * (3.2 + 5.0 * (1.0 - noise)))
        tonal = 0.0
        for j, amount in enumerate(harmonics):
            phases[j] += 2.0 * math.pi * hz * (j + 1) / RATE
            tonal += amount * math.sin(phases[j])
        n = rng.uniform(-1.0, 1.0) * noise * math.exp(-t * 10.0)
        out.append(env * (tonal + n))
    return out


def mechanical(name: str, duration: float, hz: float, metallic: bool = False) -> list[float]:
    count = round(RATE * duration)
    # A periodic envelope makes the loop meet cleanly at its endpoints.
    out = []
    base_frequency = round(hz * duration) / duration
    for i in range(count):
        t = i / RATE
        p = t / duration
        pulse = 0.68 + 0.18 * math.sin(2 * math.pi * 3 * p) + 0.08 * math.sin(2 * math.pi * 6 * p)
        second_harmonic = 0.16 if metallic else 0.26
        third_harmonic = 0.18 if metallic else 0.12
        tone = 0.56 * math.sin(2 * math.pi * base_frequency * t)
        tone += second_harmonic * math.sin(2 * math.pi * base_frequency * 2.0 * t)
        tone += third_harmonic * math.sin(2 * math.pi * base_frequency * 3.0 * t)
        out.append(pulse * tone)
    return out


def rain_loop() -> list[float]:
    duration = 3.0
    count = round(RATE * duration)
    # Sum periodic, incommensurate-sounding components with phases chosen from
    # a seeded generator. Every component completes an integer number of cycles.
    rng = random.Random(seed_for("Orbital original rain loop"))
    components = []
    for bin_hz in range(80, 8000, 37):
        amplitude = (1.0 / math.sqrt(bin_hz)) * rng.uniform(0.55, 1.0)
        phase = rng.uniform(0, 2 * math.pi)
        components.append((bin_hz / duration, amplitude, phase))
    out = []
    for i in range(count):
        t = i / RATE
        hiss = sum(a * math.sin(2 * math.pi * f * t + p) for f, a, p in components)
        out.append(0.24 * hiss)
    return out


def synthesize(category: str, name: str) -> list[float]:
    n = seed_for(name)
    if name == "rain":
        return rain_loop()
    if category == "loops":
        durations = (2.0, 1.6, 2.4)
        hz = 55 + n % 76
        return mechanical(name, durations[n % len(durations)], hz, metallic=(n % 2 == 0))
    if category == "shoot":
        low = "Artillery" in name or "Reign" in name or "Scepter" in name
        start = 260 + n % 280 if low else 620 + n % 700
        end = 70 + n % 100 if low else 120 + n % 250
        duration = 0.16 + (n % 9) * 0.025
        return transient(name, duration, start, end, 0.18 + (n % 4) * 0.09, (0.58, 0.24, 0.12))
    if category == "explosions" or name in {"coreLand", "drillImpact", "shockwaveTower", "blockBreak1", "blockBreak2", "blockBreak3", "wreckFall", "wreckFallBig"}:
        duration = 0.48 + (n % 8) * 0.055
        base = 58 + n % 76
        return transient(name, duration, base, 28 + n % 42, 0.82, (0.60, 0.24, 0.10))
    if name == "coreLaunch":
        return transient(name, 0.62, 180, 920, 0.42, (0.56, 0.28, 0.12))
    if name == "unitCreate":
        return transient(name, 0.42, 330 + n % 170, 980 + n % 420, 0.05, (0.62, 0.28, 0.10))
    if name in {"click", "blockRotate", "door", "blockRepair"}:
        duration = 0.09 + (n % 5) * 0.025
        return transient(name, duration, 680 + n % 900, 260 + n % 420, 0.20, (0.70, 0.22, 0.08))
    if name == "waveSpawn":
        return transient(name, 0.58, 520, 1040, 0.03, (0.65, 0.25, 0.10))
    return transient(name, 0.32, 220 + n % 350, 90 + n % 170, 0.28, (0.62, 0.25, 0.13))


def target_paths() -> list[tuple[str, str]]:
    return [(category, name) for category, names in TARGETS.items() for name in names]


def generate() -> list[Path]:
    paths = []
    for category, name in target_paths():
        output = STAGE / "sounds" / category / f"{name}.wav"
        write_wav(output, synthesize(category, name))
        paths.append(output)
    return paths


def install(paths: list[Path]) -> None:
    expected = {(category, name) for category, name in target_paths()}
    generated = {(p.parent.name, p.stem) for p in paths}
    if generated != expected or len(paths) != 39:
        raise SystemExit(f"Refusing install: expected exactly 39 outputs; got {len(paths)}")

    backup = STAGE / "replaced-cc0-ogg"
    for category, name in target_paths():
        old = SOUNDS / category / f"{name}.ogg"
        new = STAGE / "sounds" / category / f"{name}.wav"
        if not old.is_file() or not new.is_file():
            raise SystemExit(f"Refusing partial install; missing {old} or {new}")

    for category, name in target_paths():
        old = SOUNDS / category / f"{name}.ogg"
        saved = backup / category / old.name
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(old), str(saved))
        shutil.move(str(STAGE / "sounds" / category / f"{name}.wav"), str(SOUNDS / category / f"{name}.wav"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--install", action="store_true", help="replace the 39 CC0 OGGs with the staged WAVs")
    args = parser.parse_args()
    outputs = generate()
    print(f"Generated {len(outputs)} original WAV assets at {STAGE / 'sounds'}")
    if args.install:
        install(outputs)
        print("Installed 39 WAV assets; previous OGGs were moved to ignored build staging backup.")


if __name__ == "__main__":
    main()
