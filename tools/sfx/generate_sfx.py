"""Synthesizes the board sound effects (CC0 1.0).

Usage:
    python3 generate_sfx.py OUT_DIR
    for s in move capture select; do
        ffmpeg -y -fflags +bitexact -i OUT_DIR/sfx_$s.wav -map_metadata -1 \
            -c:a libvorbis -q:a 5 ../../app/src/main/res/raw/sfx_$s.ogg
    done
"""

import sys
import wave
from pathlib import Path

import numpy as np

SR = 44100


def modes(t, rng, specs):
    out = np.zeros_like(t)
    for freq, amp, tau in specs:
        out += amp * np.sin(2 * np.pi * freq * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / tau)
    return out


def band_noise(n, lo, hi, rng):
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / SR)
    spectrum[(freqs < lo) | (freqs > hi)] = 0
    x = np.fft.irfft(spectrum, n)
    return x / np.max(np.abs(x))


def hit(dur, specs, noise_band, noise_tau, noise_amp, rng):
    n = int(SR * dur)
    t = np.arange(n) / SR
    x = modes(t, rng, specs)
    x += noise_amp * band_noise(n, *noise_band, rng) * np.exp(-t / noise_tau)
    return x * np.minimum(1.0, t / 0.0004)


def board_knock(dur, rng, scale=1.0):
    return hit(
        dur,
        [
            (125 * scale, 0.45, 0.018),
            (410 * scale, 1.00, 0.042),
            (1130 * scale, 0.55, 0.024),
            (2210 * scale, 0.30, 0.013),
            (3050 * scale, 0.35, 0.007),
            (4700 * scale, 0.15, 0.004),
        ],
        (1200, 8000), 0.0025, 0.55, rng,
    )


def piece_clack(dur, rng):
    return hit(
        dur,
        [
            (1650, 0.40, 0.016),
            (2450, 1.00, 0.012),
            (3900, 0.60, 0.008),
            (6100, 0.30, 0.004),
        ],
        (1800, 10000), 0.0018, 0.70, rng,
    )


def finish(x, rms_db, fade=0.012):
    n = int(SR * fade)
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n)
    x *= 10 ** (rms_db / 20) / np.sqrt(np.mean(x ** 2))
    peak = np.max(np.abs(x))
    return x if peak <= 0.94 else x * 0.94 / peak


def move(rng):
    return finish(board_knock(0.20, rng), -24.0)


def capture(rng):
    total = int(SR * 0.30)
    x = np.zeros(total)
    clack = piece_clack(0.10, rng) * 0.85
    x[: len(clack)] += clack
    start = int(SR * 0.030)
    knock = board_knock(0.30 - 0.030, rng, scale=0.94)
    x[start:start + len(knock)] += knock
    return finish(x, -25.0)


def select(rng):
    x = hit(
        0.10,
        [(1850, 0.60, 0.007), (3300, 1.00, 0.005), (5200, 0.35, 0.003)],
        (2500, 9000), 0.0012, 0.45, rng,
    )
    return finish(x, -27.0, fade=0.008)


def write_wav(path, x):
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for seed, (name, make) in enumerate([("move", move), ("capture", capture), ("select", select)]):
        write_wav(out / f"sfx_{name}.wav", make(np.random.default_rng(seed + 1)))


if __name__ == "__main__":
    main()
