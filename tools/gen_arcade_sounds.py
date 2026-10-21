#!/usr/bin/env python3
"""Synthesise every NeroArcade sound as an original, from scratch, with the Python standard library only.

No sampled or downloaded audio: every file is additive/subtractive synthesis (sine, square, saw,
triangle, filtered noise) rendered mono at 22 050 Hz, written as WAV, then encoded to OGG Vorbis with
ffmpeg when it is on PATH. Deterministic: every voice seeds its RNG from the sound key.

Also writes assets/neroarcade/sounds.json from the same table so the keys and files never drift.

usage: python3 tools/gen_arcade_sounds.py [--wav-only] [--check]
  (default)   render WAVs to build/sounds-wav/, encode to assets/neroarcade/sounds/**.ogg, write sounds.json
  --wav-only  skip ffmpeg (the owner converts build/sounds-wav/** to .ogg by hand)
  --check     verify every .ogg sounds.json references exists; exit 1 otherwise
"""
import json
import math
import os
import random
import shutil
import struct
import subprocess
import sys
import wave

sys.dont_write_bytecode = True

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO_ROOT, "common", "src", "main", "resources", "assets", "neroarcade")
SOUNDS = os.path.join(ASSETS, "sounds")
WAV_DIR = os.path.join(REPO_ROOT, "build", "sounds-wav")
SR = 22050
THEMES = ("neon_void", "hangar", "cindara_dusk", "terminal_green")


# ---------------------------------------------------------------------------------------------------
# DSP helpers on plain float lists in [-1, 1]
# ---------------------------------------------------------------------------------------------------
def silence(seconds):
    return [0.0] * int(SR * seconds)


def osc(kind, freq, seconds, amp=1.0, phase=0.0, freq_end=None):
    n = int(SR * seconds)
    out = []
    ph = phase
    for i in range(n):
        f = freq if freq_end is None else freq + (freq_end - freq) * i / max(1, n - 1)
        ph += f / SR
        t = ph % 1.0
        if kind == "sine":
            v = math.sin(2 * math.pi * t)
        elif kind == "square":
            v = 1.0 if t < 0.5 else -1.0
        elif kind == "saw":
            v = 2.0 * t - 1.0
        elif kind == "tri":
            v = 4.0 * abs(t - 0.5) - 1.0
        else:
            raise ValueError(kind)
        out.append(v * amp)
    return out


def noise(seconds, amp=1.0, rng=None):
    rng = rng or random.Random(0)
    return [rng.uniform(-1, 1) * amp for _ in range(int(SR * seconds))]


def env(sig, attack=0.005, decay=None, sustain=1.0, release=0.05):
    """Simple ADSR-ish shaping in place; decay=None means hold at sustain after attack."""
    n = len(sig)
    a = int(attack * SR)
    r = int(release * SR)
    d = int(decay * SR) if decay else 0
    for i in range(n):
        g = 1.0
        if i < a:
            g = i / max(1, a)
        elif d and i < a + d:
            g = 1.0 - (1.0 - sustain) * (i - a) / max(1, d)
        else:
            g = sustain
        if i >= n - r:
            g *= (n - i) / max(1, r)
        sig[i] *= g
    return sig


def expdecay(sig, tau):
    for i in range(len(sig)):
        sig[i] *= math.exp(-i / (tau * SR))
    return sig


def lowpass(sig, cutoff):
    rc = 1.0 / (2 * math.pi * cutoff)
    dt = 1.0 / SR
    a = dt / (rc + dt)
    out = []
    y = 0.0
    for x in sig:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(sig, cutoff):
    lp = lowpass(sig, cutoff)
    return [x - y for x, y in zip(sig, lp)]


def mix(*parts):
    n = max(len(p) for p in parts)
    out = [0.0] * n
    for p in parts:
        for i, v in enumerate(p):
            out[i] += v
    return out


def at(base, sig, seconds):
    """Overlay sig onto base starting at `seconds` (extends base if needed)."""
    off = int(seconds * SR)
    need = off + len(sig)
    if len(base) < need:
        base.extend([0.0] * (need - len(base)))
    for i, v in enumerate(sig):
        base[off + i] += v
    return base


def gain(sig, g):
    return [v * g for v in sig]


def normalize(sig, peak=0.8):
    m = max(1e-9, max(abs(v) for v in sig))
    return [v / m * peak for v in sig]


def resample(sig, ratio):
    """ratio > 1 = higher pitch / shorter."""
    n = int(len(sig) / ratio)
    out = []
    for i in range(n):
        x = i * ratio
        j = int(x)
        fr = x - j
        a = sig[j] if j < len(sig) else 0.0
        b = sig[j + 1] if j + 1 < len(sig) else 0.0
        out.append(a + (b - a) * fr)
    return out


def tremolo(sig, rate, depth):
    return [v * (1.0 - depth + depth * 0.5 * (1 + math.sin(2 * math.pi * rate * i / SR))) for i, v in enumerate(sig)]


def loop_crossfade(sig, seconds=0.25):
    """Make the tail fade into the head so the file loops cleanly."""
    n = int(seconds * SR)
    L = len(sig)
    for i in range(n):
        g = i / n
        sig[L - n + i] = sig[L - n + i] * (1 - g) + sig[i] * g
    return sig


# ---------------------------------------------------------------------------------------------------
# Voices (each returns a float list)
# ---------------------------------------------------------------------------------------------------
NOTE = {"C5": 523.25, "E5": 659.25, "G5": 783.99, "C6": 1046.5, "A4": 440.0, "E4": 329.63, "A3": 220.0,
        "C4": 261.63, "G4": 392.0, "D5": 587.33, "F5": 698.46}


def v_insert():
    rng = random.Random("cabinet.insert")
    tk = expdecay(lowpass(noise(0.03, 0.9, rng), 3000), 0.008)
    thunk = mix(expdecay(osc("sine", 120, 0.12, 0.9), 0.03),
                expdecay(lowpass(noise(0.03, 0.6, rng), 1200), 0.006))
    out = silence(0.3)
    at(out, tk, 0.0)
    at(out, thunk, 0.15)
    return normalize(out[:int(0.3 * SR)], 0.7)


def v_start():
    out = silence(0.4)
    for i, n in enumerate(("C5", "E5", "G5")):
        at(out, env(osc("square", NOTE[n], 0.13, 0.35), 0.003, release=0.03), i * 0.12)
    return normalize(out[:int(0.4 * SR)], 0.6)


def v_deny():
    out = silence(0.2)
    for i in range(2):
        at(out, env(osc("square", 110, 0.06, 0.5), 0.002, release=0.01), i * 0.1)
    return normalize(lowpass(out, 1500), 0.65)


def v_win():
    rng = random.Random("cabinet.win")
    out = silence(1.2)
    for i, n in enumerate(("C5", "E5", "G5", "C6")):
        at(out, env(osc("square", NOTE[n], 0.22, 0.3), 0.004, release=0.06), i * 0.18)
    at(out, env(osc("tri", NOTE["C6"], 0.5, 0.25), 0.02, release=0.3), 0.72)
    at(out, env(lowpass(noise(1.0, 0.25, rng), 2500), 0.5, release=0.4), 0.1)
    return normalize(out[:int(1.2 * SR)], 0.7)


def v_lose():
    out = silence(0.6)
    at(out, env(osc("saw", 330, 0.3, 0.4, freq_end=300), 0.005, release=0.05), 0.0)
    at(out, env(osc("saw", 220, 0.3, 0.4, freq_end=180), 0.005, release=0.12), 0.28)
    return normalize(lowpass(out, 900)[:int(0.6 * SR)], 0.65)


def v_pickup():
    return normalize(env(osc("sine", 880, 0.04, 0.8), 0.002, release=0.015), 0.6)


def v_hit():
    rng = random.Random("game.hit")
    out = mix(expdecay(lowpass(noise(0.08, 0.8, rng), 1800), 0.02),
              expdecay(osc("sine", 220, 0.08, 0.7, freq_end=110), 0.03))
    return normalize(out, 0.7)


def v_tick():
    rng = random.Random("game.tick")
    return normalize(expdecay(highpass(noise(0.015, 0.8, rng), 2500), 0.003), 0.5)


def v_bench_save():
    return resample(v_insert(), 1.2)


def v_dup_spin():
    rng = random.Random("duplicator.spin")
    base = mix(osc("tri", 70, 2.0, 0.5), osc("tri", 70.7, 2.0, 0.3), lowpass(noise(2.0, 0.25, rng), 600))
    base = tremolo(base, 6.0, 0.15)
    return normalize(loop_crossfade(base), 0.5)


def v_publish():
    return list(reversed(v_start()))


# ---- theme music: sting (1 s), ambient loop (8 s), outro (1 s) ----

def pad_chord(freqs, seconds, amp, cutoff):
    parts = [osc("saw", f, seconds, amp) for f in freqs] + [osc("saw", f * 1.004, seconds, amp * 0.7) for f in freqs]
    return lowpass(mix(*parts), cutoff)


def neon_void(part):
    if part == "start":
        out = pad_chord([NOTE["A3"], NOTE["E4"], NOTE["C5"]], 1.0, 0.18, 1200)
        for i, n in enumerate(("A4", "E5", "C6")):
            at(out, env(osc("square", NOTE[n], 0.12, 0.2), 0.003, release=0.03), 0.25 + i * 0.1)
        return normalize(env(out, 0.02, release=0.3)[:SR], 0.6)
    if part == "loop":
        out = pad_chord([NOTE["A3"], NOTE["E4"], NOTE["C5"]], 8.0, 0.14, 900)
        out = tremolo(out, 0.25, 0.3)
        seq = ("A4", "C5", "E5", "A4", "E5", "G5", "C5", "E5")
        for step in range(32):
            n = seq[step % len(seq)]
            at(out, env(osc("square", NOTE[n] / (2 if step % 8 == 7 else 1), 0.11, 0.09), 0.003, release=0.04), step * 0.25)
        return normalize(loop_crossfade(out[:8 * SR], 0.4), 0.55)
    out = pad_chord([NOTE["A3"], NOTE["E4"]], 1.0, 0.18, 800)
    at(out, env(osc("square", NOTE["A4"], 0.3, 0.15, freq_end=NOTE["A3"]), 0.01, release=0.2), 0.3)
    return normalize(env(out, 0.01, release=0.6)[:SR], 0.55)


def hangar(part):
    rng = random.Random("theme.hangar." + part)
    secs = 8.0 if part == "loop" else 1.0
    hum = mix(osc("sine", 50, secs, 0.25), osc("sine", 100, secs, 0.12), lowpass(noise(secs, 0.5, rng), 250))
    hum = tremolo(hum, 0.5, 0.2)
    out = hum
    if part == "loop":
        for _ in range(5):
            t = rng.uniform(0.2, 7.2)
            f = rng.choice((620.0, 880.0, 1250.0))
            clank = mix(expdecay(osc("sine", f, 0.4, 0.25), 0.08), expdecay(osc("sine", f * 2.76, 0.3, 0.12), 0.05),
                        expdecay(highpass(noise(0.05, 0.3, rng), 1500), 0.01))
            at(out, lowpass(clank, 3000), t)
        return normalize(loop_crossfade(out[:8 * SR], 0.5), 0.55)
    if part == "start":
        at(out, expdecay(osc("sine", 880, 0.6, 0.3), 0.12), 0.05)
        at(out, expdecay(osc("sine", 2430, 0.4, 0.12), 0.06), 0.05)
        return normalize(env(out, 0.01, release=0.3)[:SR], 0.55)
    at(out, expdecay(osc("sine", 620, 0.7, 0.3), 0.15), 0.1)
    return normalize(env(out, 0.01, release=0.5)[:SR], 0.55)


def cindara_dusk(part):
    rng = random.Random("theme.cindara_dusk." + part)
    secs = 8.0 if part == "loop" else 1.0
    wind = lowpass(noise(secs, 0.6, rng), 400)
    wind = [v * (0.5 + 0.5 * math.sin(2 * math.pi * 0.18 * i / SR + 1.0)) for i, v in enumerate(wind)]
    out = wind
    pops = int(secs * 9)
    for _ in range(pops):
        t = rng.uniform(0.0, secs - 0.05)
        pop = expdecay(lowpass(noise(0.03, rng.uniform(0.2, 0.6), rng), rng.uniform(900, 2400)), 0.004)
        at(out, pop, t)
    if part == "loop":
        drone = lowpass(mix(osc("saw", 110, secs, 0.08), osc("saw", 110.6, secs, 0.08)), 300)
        out = mix(out, drone)
        return normalize(loop_crossfade(out[:8 * SR], 0.5), 0.5)
    if part == "start":
        at(out, env(osc("tri", NOTE["E4"], 0.5, 0.3), 0.05, release=0.3), 0.1)
        at(out, env(osc("tri", NOTE["A4"], 0.5, 0.25), 0.05, release=0.35), 0.4)
        return normalize(env(out, 0.02, release=0.3)[:SR], 0.55)
    at(out, env(osc("tri", NOTE["A4"], 0.6, 0.3, freq_end=NOTE["E4"]), 0.05, release=0.4), 0.2)
    return normalize(env(out, 0.02, release=0.5)[:SR], 0.5)


def terminal_green(part):
    rng = random.Random("theme.terminal_green." + part)
    secs = 8.0 if part == "loop" else 1.0
    out = silence(secs)
    t = 0.0
    while t < secs - 0.03:
        d = rng.choice((0.02, 0.03, 0.05, 0.08))
        f = rng.choice((1200.0, 2200.0, 1650.0, 980.0))
        at(out, env(osc("square", f, d, 0.25), 0.002, release=0.005), t)
        t += d + rng.choice((0.0, 0.01, 0.04))
    out = lowpass(out, 3500)
    carrier = osc("sine", 60, secs, 0.08)
    out = mix(out, carrier)
    if part == "loop":
        return normalize(loop_crossfade(out[:8 * SR], 0.2), 0.45)
    if part == "start":
        at(out, env(osc("square", 2200, 0.25, 0.25, freq_end=2600), 0.003, release=0.05), 0.0)
        return normalize(env(out, 0.005, release=0.2)[:SR], 0.5)
    at(out, env(osc("square", 1200, 0.3, 0.25, freq_end=300), 0.003, release=0.1), 0.6)
    return normalize(env(out, 0.005, release=0.3)[:SR], 0.5)


THEME_VOICES = {"neon_void": neon_void, "hangar": hangar, "cindara_dusk": cindara_dusk, "terminal_green": terminal_green}

# key -> (voice, subtitle key suffix, extra sounds.json entry fields)
TABLE = [
    ("cabinet.insert", v_insert, {}),
    ("cabinet.start", v_start, {}),
    ("cabinet.deny", v_deny, {}),
    ("cabinet.win", v_win, {}),
    ("cabinet.lose", v_lose, {}),
    ("game.pickup", v_pickup, {}),
    ("game.hit", v_hit, {}),
    ("game.tick", v_tick, {}),
    ("bench.save", v_bench_save, {}),
    ("duplicator.spin", v_dup_spin, {}),
    ("exchange.publish", v_publish, {}),
]
for _t in THEMES:
    for _p in ("start", "loop", "end"):
        vol = 0.4 if _p == "loop" else 1.0
        if _t == "terminal_green":
            vol = 0.2 if _p == "loop" else 0.6
        TABLE.append(("theme.%s.%s" % (_t, _p), (lambda t=_t, p=_p: THEME_VOICES[t](p)), {"volume": vol}))


def key_path(key):
    return key.replace(".", "/")


def write_wav(sig, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with wave.open(dest, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for v in sig:
            v = max(-1.0, min(1.0, v))
            frames += struct.pack("<h", int(v * 32767))
        w.writeframes(bytes(frames))


def sounds_json():
    obj = {}
    for key, _voice, extra in TABLE:
        entry = {"name": "neroarcade:" + key_path(key)}
        entry.update(extra)
        obj[key] = {"subtitle": "subtitles.neroarcade." + key, "sounds": [entry]}
    return obj


def check():
    with open(os.path.join(ASSETS, "sounds.json"), encoding="utf-8") as f:
        sj = json.load(f)
    problems = 0
    for key, entry in sj.items():
        for s in entry["sounds"]:
            if s.get("type") == "event":
                continue
            rel = s["name"].split(":", 1)[1] + ".ogg"
            if not os.path.exists(os.path.join(SOUNDS, rel)):
                print("  missing: sounds/" + rel + "  (" + key + ")")
                problems += 1
    print("gen_arcade_sounds --check: %d key(s), %d problem(s)" % (len(sj), problems))
    return 1 if problems else 0


def main(argv):
    if "--check" in argv:
        return check()
    ffmpeg = None if "--wav-only" in argv else shutil.which("ffmpeg")
    for key, voice, _extra in TABLE:
        sig = voice()
        wav = os.path.join(WAV_DIR, key_path(key) + ".wav")
        write_wav(sig, wav)
        if ffmpeg:
            ogg = os.path.join(SOUNDS, key_path(key) + ".ogg")
            os.makedirs(os.path.dirname(ogg), exist_ok=True)
            subprocess.check_call([ffmpeg, "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-ar", str(SR),
                                   "-c:a", "libvorbis", "-q:a", "4", ogg])
        print("  %-28s %5.2f s" % (key, len(sig) / SR))
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sounds_json(), f, indent=2)
        f.write("\n")
    print("gen_arcade_sounds: %d sound(s), ogg=%s" % (len(TABLE), "yes" if ffmpeg else "NO (wav only)"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
