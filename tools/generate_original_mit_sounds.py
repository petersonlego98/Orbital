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
PRESENCE = 0.60  # high-shelf gain above ~450 Hz; calibrated by measurement
LOW_CUT = 0.25   # attenuation of the sub-450 Hz band; calibrated by measurement

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

# Gunfire/explosion redesign approved for review on 2026-11-06. Only these 21
# assets use the new synthesis; the other 18 keep the original transient().
REVIEW_TARGETS = {
    "shoot": [
        "shootArtillery", "shootArtillerySap", "shootArtillerySapBig", "shootArtillerySmall",
        "shootCyclone", "shootDisperse", "shootHorizon", "shootReign", "shootRipple",
        "shootScatter", "shootScepter",
    ],
    "explosions": ["unitExplode1", "unitExplode2", "unitExplode3", "wreckFall", "wreckFallBig"],
    "block": ["blockBreak1", "blockBreak2", "blockBreak3", "drillImpact", "shockwaveTower"],
}
REVIEW_SET = {(category, name) for category, names in REVIEW_TARGETS.items() for name in names}
REVIEW = STAGE / "review-armas"


def seed_for(name: str) -> int:
    return sum((i + 1) * byte for i, byte in enumerate(name.encode("utf-8")))


def write_wav(path: Path, data: list[float], normalize: bool = True) -> None:
    peak = max((abs(x) for x in data), default=0.0) or 1.0
    gain = min(0.82 / peak, 1.0) if normalize else 1.0
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


def softlimit(x: float) -> float:
    """Asymptotic soft ceiling at 1.0, transparent below 0.82."""
    ceiling = 0.82
    a = abs(x)
    if a <= ceiling:
        return x
    return math.copysign(ceiling + (1.0 - ceiling) * (1.0 - math.exp(-(a - ceiling) / (1.0 - ceiling))), x)


def finish(data: list[float], target_rms: float, limiter: bool = False) -> list[float]:
    """DC removal, end fade and average-energy normalisation.

    The RMS ceiling is what keeps a high-rate weapon from piling up loud,
    overlapping voices. For gunshots a soft ceiling handles the transient
    instead of a peak-derived gain: a crest factor around 10 would otherwise
    make the peak the limiting term and cancel any spectral rebalancing.
    """
    mean = sum(data) / len(data)
    data = [x - mean for x in data]
    fade = max(1, round(0.005 * RATE))
    for k in range(fade):
        data[len(data) - 1 - k] *= k / fade
    peak = max((abs(x) for x in data), default=0.0) or 1.0
    rms = math.sqrt(sum(x * x for x in data) / len(data)) or 1.0
    if limiter:
        gain = target_rms / rms
        return [softlimit(x * gain) for x in data]
    gain = min(0.80 / peak, target_rms / rms)
    return [max(-1.0, min(1.0, x * gain)) for x in data]


def bandpass_body(source: list[float], hz: float, q: float, decay: float) -> list[float]:
    """Two-pole resonant band-pass excited by noise: the low 'chest' of a blast."""
    w0 = 2.0 * math.pi * hz / RATE
    alpha = math.sin(w0) / (2.0 * q)
    a0 = 1.0 + alpha
    a1 = -2.0 * math.cos(w0)
    a2 = 1.0 - alpha
    x1 = x2 = y1 = y2 = 0.0
    out = []
    for i, x in enumerate(source):
        y = (alpha * x - alpha * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1 = x1, x
        y2, y1 = y1, y
        out.append(y * math.exp(-(i / RATE) / decay))
    return out


def lowpass1(data: list[float], hz: float) -> list[float]:
    alpha = 1.0 - math.exp(-2.0 * math.pi * hz / RATE)
    state = 0.0
    out = []
    for x in data:
        state += alpha * (x - state)
        out.append(state)
    return out


def damped_modes(count: int, modes: list[tuple[float, float, float, float]], rng: random.Random) -> list[float]:
    """Inharmonic, individually decaying barrel resonances excited at t = 0."""
    out = [0.0] * count
    for hz, amp, tau, drift in modes:
        phase = rng.uniform(0.0, 2.0 * math.pi)
        for i in range(count):
            t = i / RATE
            f = hz * (1.0 + drift * math.exp(-t / 0.010))
            phase += 2.0 * math.pi * f / RATE
            out[i] += amp * math.sin(phase) * math.exp(-t / tau)
    return out


def firearm(name: str) -> list[float]:
    """Procedural gunshot structured like a recording, not like a synth tone.

    Layers: supersonic N-wave shock, a short mid-band bore definition,
    inharmonic barrel modes, turbulent gas release, three progressively darker
    early reflections and a delayed metallic casing ejection. The v4 tuning
    restores presence above 300 Hz without reintroducing a sustained hiss.
    """
    rng = random.Random(seed_for(name) + 0x5A17)
    heavy = any(token in name for token in ("Artillery", "Reign", "Scepter"))
    rapid = any(token in name for token in ("Cyclone", "Ripple", "Scatter", "Disperse", "Horizon"))

    duration = (0.190 + rng.uniform(0.0, 0.045)) if heavy else (0.105 + rng.uniform(0.0, 0.032))
    if "Small" in name:
        duration = min(duration, 0.145)
    count = round(RATE * duration)

    f0 = (118.0 + rng.uniform(0.0, 26.0)) * (0.80 if heavy else 1.0)
    modes = [
        (f0,        0.55, 0.034 if heavy else 0.024, 0.010),
        (f0 * 1.87, 0.30, 0.021 if heavy else 0.015, 0.014),
        (f0 * 3.14, 0.18, 0.013, 0.018),
        (f0 * 4.70, 0.10, 0.009, 0.022),
        (f0 * 0.52, 0.22 if heavy else 0.12, 0.030, 0.006),
    ]
    body = damped_modes(count, modes, rng)

    white = [rng.uniform(-1.0, 1.0) for _ in range(count)]
    tail_decay = (0.085 if heavy else 0.048) + rng.uniform(0.0, 0.014)
    eject_at = 0.019 + rng.uniform(0.0, 0.009)
    lower = lowpass1(white, 900.0)
    upper = lowpass1(white, 6000.0)
    mid_lower = lowpass1(white, 600.0)
    mid_upper = lowpass1(white, 2500.0)
    tail_low = lowpass1(white, 280.0)

    dry: list[float] = []
    lp_tail = 0.0
    for i in range(count):
        t = i / RATE
        w = white[i]

        # N-wave: pressure spike followed by rarefaction, ~1.5 ms in total.
        shock = 2.10 * math.exp(-((t - 0.00035) / 0.00026) ** 2)
        shock -= 1.20 * math.exp(-((t - 0.00125) / 0.00048) ** 2)
        grit = (upper[i] - lower[i]) * math.exp(-t / 0.0032) * 1.20
        # Short mid-band bore definition: presence without a sustained hiss.
        definition = (mid_upper[i] - mid_lower[i]) * math.exp(-t / 0.010) * 0.45

        # Gas release stays open into the mid band for ~45 ms. Closing it at
        # 260 Hz within 24 ms is what made the earlier version sound muffled;
        # this is still noise with a decaying envelope, not a sustained tone.
        fc = 3600.0 * math.exp(-t / 0.045) + 700.0
        lp_tail += (1.0 - math.exp(-2.0 * math.pi * fc / RATE)) * (w - lp_tail)
        turbulence = 1.0 + 0.22 * math.sin(2 * math.pi * 37.0 * t) + 0.13 * math.sin(2 * math.pi * 91.0 * t)
        tail = (lp_tail - tail_low[i]) * math.exp(-t / tail_decay) * turbulence * (0.40 if heavy else 0.34)

        dt = t - eject_at
        mech = 0.0
        if abs(dt) < 0.010:
            for hz, amp in ((1850.0, 0.095), (2930.0, 0.065), (4100.0, 0.038)):
                mech += amp * math.sin(2 * math.pi * hz * dt) * math.exp(-abs(dt) / 0.0020)
            mech *= 1.30 if rapid else 1.0

        dry.append(shock + grit + definition + body[i] + tail + mech)

    out = list(dry)
    for delay, amp, cutoff in ((0.007, 0.42, 4200.0), (0.016, 0.28, 2800.0), (0.031, 0.16, 1700.0)):
        reflection = lowpass1(dry, cutoff)
        shift = round(delay * RATE)
        for i in range(count - shift):
            out[i + shift] += reflection[i] * amp

    # Real gun recordings compress slightly on the transient; keep it mild so
    # the shock wave is not flattened by the RMS normalisation in finish().
    for i in range(round(0.006 * RATE)):
        v = out[i]
        out[i] = v / (1.0 + 0.10 * abs(v))

    # Presence shelf above ~500 Hz. finish() normalises by RMS, so increasing
    # the gain of the mid layers alone is cancelled out; the spectral balance
    # itself has to change. This restores definition without a sustained hiss,
    # because the boosted material is transient (shock, grit, reflections,
    # casing ejection) rather than a continuous tone.
    low = lowpass1(out, 450.0)
    out = [v - LOW_CUT * lo + PRESENCE * (v - lo) for v, lo in zip(out, low)]

    return finish(out, 0.115 if heavy else 0.100, limiter=True)


def impact(name: str) -> list[float]:
    """Original explosion/impact in the same sonic language as firearm()."""
    rng = random.Random(seed_for(name) + 0x71C3)
    big = "Big" in name or "Explode" in name or "shockwave" in name.lower()
    small = "Break" in name or "drill" in name.lower()

    if small:
        duration = 0.115 + rng.uniform(0.0, 0.075)
    elif big:
        duration = 0.300 + rng.uniform(0.0, 0.140)
    else:
        duration = 0.220 + rng.uniform(0.0, 0.100)
    count = round(RATE * duration)

    body_hz = 46.0 + rng.uniform(0.0, 34.0)
    crack_decay = 0.005 + rng.uniform(0.0, 0.004)
    body_decay = 0.055 if small else (0.160 if big else 0.090)
    tail_decay = 0.075 if small else (0.220 if big else 0.130)
    white = [rng.uniform(-1.0, 1.0) for _ in range(count)]
    body = bandpass_body(white, body_hz, 1.3, body_decay)

    lower_alpha = 1.0 - math.exp(-2.0 * math.pi * 500.0 / RATE)
    upper_alpha = 1.0 - math.exp(-2.0 * math.pi * 2600.0 / RATE)
    out: list[float] = []
    lp_lower = lp_upper = lp_tail = phase = 0.0
    for i in range(count):
        t = i / RATE
        w = white[i]
        lp_lower += lower_alpha * (w - lp_lower)
        lp_upper += upper_alpha * (w - lp_upper)
        crack = (lp_upper - lp_lower) * math.exp(-t / crack_decay) * 0.75

        freq = body_hz * 1.7 * (0.45 + 0.55 * math.exp(-t / 0.020))
        phase += 2.0 * math.pi * freq / RATE
        sub = math.sin(phase) * math.exp(-t / body_decay) * 0.36

        fc = 1900.0 * math.exp(-t / 0.055) + 160.0
        lp_tail += (1.0 - math.exp(-2.0 * math.pi * fc / RATE)) * (w - lp_tail)
        tail = lp_tail * math.exp(-t / tail_decay) * 0.40
        out.append(crack + body[i] * 3.0 + sub + tail)
    return finish(out, 0.160 if big else 0.130)


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
    if (category, name) in REVIEW_SET:
        return firearm(name) if category == "shoot" else impact(name)
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
    """Install the 21 reviewed WAVs over existing WAVs, idempotently.

    The original installer moved CC0 OGGs away; those files no longer exist
    (they were replaced on 2026-11-05), so it could not run a second time.
    Only the reviewed assets are touched; the other 18 stay byte-identical.
    """
    expected = {(category, name) for category, name in target_paths()}
    generated = {(p.parent.name, p.stem) for p in paths}
    if generated != expected or len(paths) != 39:
        raise SystemExit(f"Refusing install: expected exactly 39 staged outputs; got {len(paths)}")

    targets = [(category, name) for category, names in REVIEW_TARGETS.items() for name in names]
    if len(targets) != 21:
        raise SystemExit(f"Refusing install: expected 21 review targets; got {len(targets)}")

    # Validate every staged file and destination before writing anything.
    for category, name in targets:
        staged = STAGE / "sounds" / category / f"{name}.wav"
        dest = SOUNDS / category / f"{name}.wav"
        if not staged.is_file() or not dest.is_file():
            raise SystemExit(f"Refusing partial install; missing {staged} or {dest}")
        with wave.open(str(staged), "rb") as handle:
            if (handle.getnchannels(), handle.getsampwidth(), handle.getframerate()) != (1, 2, RATE):
                raise SystemExit(f"Refusing install: invalid WAV format in {staged}")

    backup = STAGE / "replaced-v3-wav"
    for category, name in targets:
        dest = SOUNDS / category / f"{name}.wav"
        saved = backup / category / dest.name
        saved.parent.mkdir(parents=True, exist_ok=True)
        if not saved.exists():
            shutil.copy2(str(dest), str(saved))
        shutil.copy2(str(STAGE / "sounds" / category / f"{name}.wav"), str(dest))


def read_wav(path: Path) -> list[float]:
    with wave.open(str(path), "rb") as handle:
        count = handle.getnframes()
        pcm = struct.unpack("<%dh" % count, handle.readframes(count))
    return [x / 32768.0 for x in pcm]


def burst(source: list[float], interval: float, seconds: float, gain: float = 0.55) -> list[float]:
    """Overlap one-shot voices at a real weapon cadence, with identical gain for A/B."""
    total = round(RATE * seconds)
    out = [0.0] * total
    t = 0.0
    while t < seconds:
        start = round(t * RATE)
        for i, x in enumerate(source):
            if start + i >= total:
                break
            out[start + i] += x
        t += interval
    return [max(-1.0, min(1.0, x * gain)) for x in out]


def generate_review() -> list[Path]:
    paths = []
    for category, names in REVIEW_TARGETS.items():
        for name in names:
            output = REVIEW / category / f"{name}.wav"
            write_wav(output, synthesize(category, name), normalize=False)
            paths.append(output)

    demo = REVIEW / "_demo"
    current = SOUNDS / "shoot" / "shootCyclone.wav"
    proposed = REVIEW / "shoot" / "shootCyclone.wav"
    if current.is_file():
        # cyclone has reload = 10f at 60 fps, i.e. one shot every 1/6 s
        old_burst = burst(read_wav(current), 1.0 / 6.0, 1.5)
        new_burst = burst(read_wav(proposed), 1.0 / 6.0, 1.5)
        comparisons = {
            "rafega_cyclone_atual.wav": old_burst,
            "rafega_cyclone_novo.wav": new_burst,
            "comparacao_AB.wav": old_burst + [0.0] * round(RATE * 0.35) + new_burst,
        }
        previous = demo / "rafega_sem_reforco.wav"
        if previous.is_file():
            comparisons["comparacao_realista.wav"] = read_wav(previous) + [0.0] * round(RATE * 0.30) + new_burst
        for filename, data in comparisons.items():
            write_wav(demo / filename, data, normalize=False)
            paths.append(demo / filename)

    showcase: list[float] = []
    for p in paths[:21]:
        showcase.extend(read_wav(p))
        showcase.extend([0.0] * round(RATE * 0.12))
    write_wav(demo / "vitrine_21_sons.wav", showcase, normalize=False)
    paths.append(demo / "vitrine_21_sons.wav")
    return paths


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--install", action="store_true", help="install the 21 reviewed gunfire/impact WAVs into core/assets/sounds")
    parser.add_argument("--review", action="store_true", help="write the 21 gunfire/impact review samples and A/B demos without touching core/assets")
    args = parser.parse_args()
    if args.review:
        paths = generate_review()
        print(f"Generated {len(paths)} review WAVs at {REVIEW}")
        return
    outputs = generate()
    print(f"Generated {len(outputs)} original WAV assets at {STAGE / 'sounds'}")
    if args.install:
        install(outputs)
        print("Installed 21 WAV assets; previous WAVs backed up to build/mit-sound-stage/replaced-v3-wav/.")


if __name__ == "__main__":
    main()
