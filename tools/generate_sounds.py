"""Generates the four WAV sound effects into app/src/main/res/raw (pure Python, no dependencies)."""
import math, random, struct, wave, os

SR = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)

def save(name, samples):
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 30000)) for s in samples))

def slide():                                   # airy rising whoosh
    random.seed(1); n = int(SR * 0.32); ph = 0; lp = 0; out = []
    for i in range(n):
        p = i / n; t = i / SR
        f = 380 * math.exp(p * math.log(1200 / 380)); ph += 2 * math.pi * f / SR
        lp += 0.12 * (random.uniform(-1, 1) - lp)
        env = min(1, t / 0.015) * (1 - p) ** 1.6
        out.append(env * (0.45 * math.sin(ph) + 0.12 * math.sin(2 * ph) + 0.9 * lp))
    return out

def collision():                               # dull thud + click
    random.seed(2); n = int(SR * 0.32); ph = 0; out = []
    for i in range(n):
        t = i / SR
        f = 160 * math.exp(-9 * t) + 48; ph += 2 * math.pi * f / SR
        out.append(math.exp(-8 * t) * 0.9 * math.sin(ph) + math.exp(-150 * t) * 0.5 * random.uniform(-1, 1))
    return out

def notes(seq, tail=0.05):                     # seq = [(freq, start_s, dur_s)]
    total = int(SR * (max(s + d for _, s, d in seq) + tail)); buf = [0.0] * total
    for f, s, d in seq:
        a = int(SR * s)
        for i in range(int(SR * d)):
            if a + i >= total: break
            t = i / SR
            env = min(1, t / 0.008) * math.exp(-5 * t / d)
            buf[a + i] += 0.45 * env * (math.sin(2 * math.pi * f * t) + 0.3 * math.sin(4 * math.pi * f * t))
    return buf

def game_over():
    return notes([(392, 0.00, 0.22), (330, 0.20, 0.22), (262, 0.40, 0.22), (196, 0.60, 0.50)])

def win():
    return notes([(523, 0.00, 0.16), (659, 0.12, 0.16), (784, 0.24, 0.16), (1047, 0.36, 0.55)])

save("sfx_slide.wav", slide()); save("sfx_collision.wav", collision())
save("sfx_game_over.wav", game_over()); save("sfx_win.wav", win())
print("sounds written to", os.path.abspath(OUT))
