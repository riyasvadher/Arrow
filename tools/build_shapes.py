"""
Builds app/src/main/assets/shapes.txt : 100 silhouettes (one per level) rendered from Noto Color Emoji.
Needs: Pillow, scipy, numpy and the NotoColorEmoji font. Run:  python3 tools/build_shapes.py
Format per shape:   @Name minN W H   followed by H rows of W chars ('#' = cell inside the shape).
"""
import os, subprocess, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage as ndi

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "shapes.txt")
LONG = 48

SHAPES = """❤ Heart|⭐ Star|🍎 Apple|🌙 Moon|💎 Diamond|💧 Drop|🎈 Balloon|🏠 House|☁ Cloud|🍄 Mushroom|
🐎 Horse|🐟 Fish|🐢 Turtle|🐈 Cat|🦆 Duck|🐖 Pig|🐧 Penguin|🌲 Pine tree|🔔 Bell|👑 Crown|
🚀 Rocket|🦋 Butterfly|🐇 Rabbit|🐋 Whale|🦉 Owl|🐑 Sheep|🐘 Elephant|🦄 Unicorn|⚓ Anchor|✈ Airplane|
🐬 Dolphin|🐓 Rooster|🐄 Cow|🐫 Camel|🦕 Dinosaur|🦀 Crab|⛵ Sailboat|☂ Umbrella|🐿 Squirrel|🐦 Bird|
🚁 Helicopter|🚗 Car|🏰 Castle|🎸 Guitar|🌴 Palm tree|🐙 Octopus|🐞 Ladybug|🦢 Swan|🚜 Tractor|🦈 Shark|
🍐 Pear|🔥 Flame|🐸 Frog|🎩 Top hat|🧲 Magnet|🌳 Tree|🍍 Pineapple|⛄ Snowman|🌻 Sunflower|⚡ Lightning|
🍉 Watermelon|🍌 Banana|🥕 Carrot|🍒 Cherries|🌵 Cactus|🎺 Trumpet|✂ Scissors|🦜 Parrot|🦩 Flamingo|🐀 Rat|
🦔 Hedgehog|🦇 Bat|🐜 Ant|🦂 Scorpion|🕷 Spider|🐠 Tropical fish|🦑 Squid|🐉 Dragon|🦅 Eagle|🐊 Crocodile|
🦒 Giraffe|🐴 Horse head|🐕 Dog|🐌 Snail|🐝 Bee|🐳 Spouting whale|🐡 Pufferfish|🍓 Strawberry|👟 Sneaker|🔨 Hammer|
⛺ Tent|🛸 UFO|🔭 Telescope|⌛ Hourglass|🗝 Old key|📯 Post horn|🎻 Violin|🔑 Key|🐻 Bear|🦊 Fox"""

entries = [s.strip() for s in SHAPES.replace("\n", "").split("|") if s.strip()]
assert len(entries) == 100, len(entries)

path = subprocess.check_output("fc-list | grep -i NotoColorEmoji | head -1 | cut -d: -f1", shell=True).decode().strip()
font = ImageFont.truetype(path, 109)

def render(ch):
    im = Image.new("RGBA", (180, 180), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((10, 10), ch, font=font, embedded_color=True)
    a = im.split()[3]
    a = a.crop(a.getbbox())
    s = LONG / max(a.size)
    a = a.resize((max(1, round(a.width * s)), max(1, round(a.height * s))), Image.LANCZOS)
    m = np.array(a) > 110
    lab, n = ndi.label(m)                                   # keep the biggest piece only
    if n > 1:
        sizes = ndi.sum(m, lab, range(1, n + 1)); m = lab == (1 + int(np.argmax(sizes)))
    inv = ~m                                                # fill small holes
    lab, n = ndi.label(inv)
    for i in range(1, n + 1):
        region = lab == i
        touches = region[0].any() or region[-1].any() or region[:, 0].any() or region[:, -1].any()
        if not touches and region.sum() < 0.015 * m.sum():
            m = m | region
    ys, xs = np.where(m)
    return m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

lines = []; sheet_items = []
for e in entries:
    ch, name = e.split(" ", 1)
    m = render(ch)
    dt = ndi.distance_transform_edt(m)
    min_n = int(min(36, max(16, math.ceil(84 / dt[m].mean()))))
    h, w = m.shape
    lines.append(f"@{name} {min_n} {w} {h}")
    lines += ["".join("#" if v else "." for v in row) for row in m]
    sheet_items.append((name, m, min_n))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w").write("\n".join(lines) + "\n")
print("wrote", OUT, os.path.getsize(OUT), "bytes,", len(entries), "shapes")

if os.environ.get("SHEET"):
    cols = 10; cell = 100
    rows = math.ceil(len(sheet_items) / cols)
    sh = Image.new("RGB", (cols * cell, rows * (cell + 14)), "white"); d = ImageDraw.Draw(sh)
    for i, (name, m, mn) in enumerate(sheet_items):
        x, y = (i % cols) * cell, (i // cols) * (cell + 14)
        im = Image.fromarray((~m * 255).astype("uint8")).convert("L").resize((m.shape[1] * 2, m.shape[0] * 2), Image.NEAREST)
        sh.paste(im.convert("RGB"), (x + 2, y + 2))
        d.text((x + 2, y + cell), f"{i+1} {name[:11]} n{mn}", fill=(150, 0, 0))
    sh.save("/tmp/shapes_sheet.png")
