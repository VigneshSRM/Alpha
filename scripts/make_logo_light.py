"""Create a dark-background variant of the Alpha Group logo.

The supplied artwork is designed for white paper: the "ALPHA GROUP OF" wordmark
and the crown-to-swash "A" are deep purple (#2E0146), which disappears on a dark
hero. This script recolours only those purple pixels to brand lavender while
leaving the gold crown, the ember "INDIA" lettering and the wolf untouched.

    python scripts/make_logo_light.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "static" / "img" / "alpha-logo.png"
DST = ROOT / "app" / "static" / "img" / "alpha-logo-light.png"

LAVENDER = (241, 238, 255)


def is_purple(r: int, g: int, b: int) -> bool:
    """Deep purple of the wordmark: blue > red > green, low overall luminance."""
    lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return b > r > g and (b - g) > 22 and lum < 120


def main() -> None:
    image = Image.open(SRC).convert("RGBA")
    pixels = image.load()
    width, height = image.size

    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            if is_purple(r, g, b):
                # Keep a hint of the original shading so edges/texture survive.
                shade = min(1.0, (0.2126 * r + 0.7152 * g + 0.0722 * b) / 70.0)
                factor = 0.88 + 0.12 * shade
                pixels[x, y] = (
                    min(255, int(LAVENDER[0] * factor)),
                    min(255, int(LAVENDER[1] * factor)),
                    min(255, int(LAVENDER[2] * factor)),
                    a,
                )

    image.save(DST, optimize=True)
    print(f"wrote {DST.relative_to(ROOT)} ({DST.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
