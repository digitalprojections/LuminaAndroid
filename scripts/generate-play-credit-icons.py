from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math


SIZE = 1024
SCALE = 3
WORK = SIZE * SCALE
OUT_DIR = Path(__file__).resolve().parents[2]


PACKS = [
    {
        "filename": "small_pack_200.png",
        "background": ((25, 53, 88), (42, 92, 124)),
        "accent": (243, 176, 76),
        "coin": (244, 184, 75),
        "coin_dark": (166, 102, 40),
        "gem": (84, 211, 194),
        "layout": "small",
    },
    {
        "filename": "medium_625.png",
        "background": ((36, 41, 72), (73, 92, 148)),
        "accent": (130, 202, 255),
        "coin": (231, 203, 118),
        "coin_dark": (151, 116, 50),
        "gem": (111, 226, 255),
        "layout": "medium",
    },
    {
        "filename": "large.png",
        "background": ((31, 64, 58), (91, 111, 63)),
        "accent": (255, 214, 96),
        "coin": (255, 205, 78),
        "coin_dark": (156, 105, 35),
        "gem": (141, 244, 192),
        "layout": "large",
    },
]


def lerp(a, b, t):
    return int(a + (b - a) * t)


def draw_background(image, start, end, accent):
    pixels = image.load()
    cx, cy = WORK * 0.54, WORK * 0.42
    max_dist = math.hypot(max(cx, WORK - cx), max(cy, WORK - cy))
    for y in range(WORK):
        row_t = y / (WORK - 1)
        for x in range(WORK):
            dist_t = math.hypot(x - cx, y - cy) / max_dist
            t = min(1.0, row_t * 0.58 + dist_t * 0.42)
            base = tuple(lerp(start[i], end[i], t) for i in range(3))
            glow = max(0.0, 1.0 - (math.hypot(x - cx, y - cy) / (WORK * 0.48)))
            pixels[x, y] = tuple(lerp(base[i], accent[i], glow * 0.18) for i in range(3)) + (255,)

    draw = ImageDraw.Draw(image, "RGBA")
    for ring in range(5):
        margin = int((116 + ring * 76) * SCALE)
        alpha = 22 - ring * 3
        draw.rounded_rectangle(
            (margin, margin, WORK - margin, WORK - margin),
            radius=int(130 * SCALE),
            outline=(*accent, alpha),
            width=int(2 * SCALE),
        )


def draw_soft_shadow(layer, bbox, radius, alpha=115):
    shadow = Image.new("RGBA", (WORK, WORK), (0, 0, 0, 0))
    draw = ImageDraw.Draw(shadow, "RGBA")
    draw.ellipse(bbox, fill=(0, 0, 0, alpha))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius))
    layer.alpha_composite(shadow)


def coin(draw, cx, cy, rx, ry, color, dark, tilt=0):
    bbox = (cx - rx, cy - ry, cx + rx, cy + ry)
    draw.ellipse(bbox, fill=dark)
    inset = int(10 * SCALE)
    top = (bbox[0] + inset, bbox[1] + inset, bbox[2] - inset, bbox[3] - inset)
    draw.ellipse(top, fill=color)
    draw.arc(top, 210 + tilt, 334 + tilt, fill=(255, 242, 174, 185), width=int(7 * SCALE))
    inner = (cx - rx * 0.44, cy - ry * 0.44, cx + rx * 0.44, cy + ry * 0.44)
    draw.ellipse(inner, outline=(255, 245, 189, 120), width=int(6 * SCALE))


def gem(draw, cx, cy, size, color, accent):
    top = (cx, cy - size)
    right = (cx + size * 0.86, cy - size * 0.2)
    bottom = (cx + size * 0.52, cy + size * 0.88)
    left_bottom = (cx - size * 0.52, cy + size * 0.88)
    left = (cx - size * 0.86, cy - size * 0.2)
    points = [top, right, bottom, left_bottom, left]
    draw.polygon(points, fill=color)
    draw.line([top, (cx, cy + size * 0.74)], fill=(255, 255, 255, 122), width=int(5 * SCALE))
    draw.line([left, right], fill=(255, 255, 255, 86), width=int(5 * SCALE))
    draw.line(points + [top], fill=accent, width=int(6 * SCALE), joint="curve")


def star(draw, cx, cy, outer, inner, color):
    points = []
    for i in range(10):
        radius = outer if i % 2 == 0 else inner
        angle = -math.pi / 2 + i * math.pi / 5
        points.append((cx + math.cos(angle) * radius, cy + math.sin(angle) * radius))
    draw.polygon(points, fill=color)


def draw_art(pack):
    image = Image.new("RGBA", (WORK, WORK), (0, 0, 0, 0))
    draw_background(image, pack["background"][0], pack["background"][1], pack["accent"])
    art = Image.new("RGBA", (WORK, WORK), (0, 0, 0, 0))
    draw = ImageDraw.Draw(art, "RGBA")

    draw_soft_shadow(art, (244 * SCALE, 648 * SCALE, 780 * SCALE, 826 * SCALE), 42 * SCALE)

    coin_color = pack["coin"]
    coin_dark = pack["coin_dark"]
    gem_color = pack["gem"]
    accent = pack["accent"]

    if pack["layout"] == "small":
        coin(draw, 512 * SCALE, 572 * SCALE, 148 * SCALE, 96 * SCALE, coin_color, coin_dark)
        coin(draw, 468 * SCALE, 514 * SCALE, 132 * SCALE, 88 * SCALE, coin_color, coin_dark)
        coin(draw, 548 * SCALE, 462 * SCALE, 118 * SCALE, 78 * SCALE, coin_color, coin_dark)
        gem(draw, 508 * SCALE, 381 * SCALE, 92 * SCALE, gem_color, accent)
        star(draw, 353 * SCALE, 335 * SCALE, 31 * SCALE, 13 * SCALE, (255, 240, 178, 225))
        star(draw, 682 * SCALE, 326 * SCALE, 22 * SCALE, 9 * SCALE, (255, 240, 178, 190))
    elif pack["layout"] == "medium":
        for offset in [112, 68, 24, -20]:
            coin(draw, 500 * SCALE, (616 + offset) * SCALE, 156 * SCALE, 86 * SCALE, coin_color, coin_dark)
        coin(draw, 602 * SCALE, 538 * SCALE, 122 * SCALE, 78 * SCALE, coin_color, coin_dark)
        gem(draw, 436 * SCALE, 382 * SCALE, 108 * SCALE, gem_color, accent)
        gem(draw, 594 * SCALE, 346 * SCALE, 76 * SCALE, (150, 237, 255), accent)
        star(draw, 690 * SCALE, 474 * SCALE, 26 * SCALE, 11 * SCALE, (255, 240, 178, 210))
    else:
        for x, y, rx in [(403, 653, 140), (512, 617, 166), (621, 652, 140), (462, 552, 128), (565, 522, 136)]:
            coin(draw, x * SCALE, y * SCALE, rx * SCALE, 82 * SCALE, coin_color, coin_dark)
        gem(draw, 512 * SCALE, 364 * SCALE, 132 * SCALE, gem_color, accent)
        gem(draw, 386 * SCALE, 438 * SCALE, 72 * SCALE, (171, 249, 213), accent)
        gem(draw, 642 * SCALE, 446 * SCALE, 72 * SCALE, (173, 247, 211), accent)
        star(draw, 512 * SCALE, 238 * SCALE, 34 * SCALE, 14 * SCALE, (255, 242, 175, 235))
        star(draw, 720 * SCALE, 384 * SCALE, 24 * SCALE, 10 * SCALE, (255, 242, 175, 190))

    art = art.filter(ImageFilter.UnsharpMask(radius=1.2 * SCALE, percent=110, threshold=3))
    image.alpha_composite(art)
    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for pack in PACKS:
        image = draw_art(pack)
        path = OUT_DIR / pack["filename"]
        image.save(path, format="PNG", dpi=(72, 72), optimize=True)
        print(path)


if __name__ == "__main__":
    main()
