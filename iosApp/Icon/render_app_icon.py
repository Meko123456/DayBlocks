#!/usr/bin/env python3
"""Renders the iOS app icons from the Android launcher foreground, so Kubi is drawn once.

The vector drawable's path data is already SVG, so each <path> is carried across as it is and the
colours translated from #AARRGGBB. The result is cropped to the middle of the 108dp adaptive-icon
canvas, laid on the app's own background colour and rasterised by headless Chrome at 1024x1024 --
the one size an iOS asset catalog needs. The alpha channel is dropped on the way out: an app icon
with one is rejected at upload, even when every pixel is opaque.

    python3 iosApp/Icon/render_app_icon.py

Needs Google Chrome and Pillow. Run it again whenever ic_launcher_foreground.xml changes.
"""

import pathlib
import subprocess
import tempfile
import xml.etree.ElementTree as ET

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
FOREGROUND = ROOT / "androidApp/src/main/res/drawable/ic_launcher_foreground.xml"
ICONSET = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ANDROID = "{http://schemas.android.com/apk/res/android}"

# Kubi's 60dp square sits at 24..84 in the 108dp canvas. An iOS icon shows the whole square, with
# no launcher mask cutting into it, so the crop keeps a margin rather than the 66dp safe zone.
VIEW_BOX = "12 12 84 84"
SIZE = 1024

# The designsystem's Paper and Ink: the backgrounds of the app itself in light and dark.
APPEARANCES = {
    "AppIcon.png": "#FAF7F2",
    "AppIcon-Dark.png": "#1B1D22",
}


def paint(value: str) -> tuple[str, str]:
    """#AARRGGBB (or #RRGGBB) as an SVG colour and opacity."""
    hex_digits = value.lstrip("#")
    if len(hex_digits) == 6:
        return f"#{hex_digits}", "1"
    alpha, rgb = int(hex_digits[:2], 16), hex_digits[2:]
    return (f"#{rgb}", f"{alpha / 255:.3f}") if alpha else ("none", "0")


def to_svg(background: str) -> str:
    paths = []
    for node in ET.parse(FOREGROUND).getroot().iter("path"):
        attrs = {"d": node.get(f"{ANDROID}pathData")}
        fill, fill_opacity = paint(node.get(f"{ANDROID}fillColor", "#00000000"))
        attrs.update({"fill": fill, "fill-opacity": fill_opacity})
        if stroke := node.get(f"{ANDROID}strokeColor"):
            attrs["stroke"], attrs["stroke-opacity"] = paint(stroke)
            attrs["stroke-width"] = node.get(f"{ANDROID}strokeWidth", "1")
            attrs["stroke-linecap"] = node.get(f"{ANDROID}strokeLineCap", "butt")
        paths.append("<path " + " ".join(f'{k}="{v}"' for k, v in attrs.items()) + "/>")
    x, y, w, h = (float(n) for n in VIEW_BOX.split())
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{VIEW_BOX}" width="{SIZE}" height="{SIZE}">'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{background}"/>'
        + "".join(paths)
        + "</svg>"
    )


def render(svg: str, out: pathlib.Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        page = pathlib.Path(tmp, "icon.html")
        page.write_text(f'<html><body style="margin:0">{svg}</body></html>')
        shot = pathlib.Path(tmp, "icon.png")
        subprocess.run(
            [CHROME, "--headless", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
             f"--window-size={SIZE},{SIZE}", f"--screenshot={shot}", page.as_uri()],
            check=True, capture_output=True,
        )
        Image.open(shot).convert("RGB").save(out, optimize=True)


def main() -> None:
    ICONSET.mkdir(parents=True, exist_ok=True)
    for name, background in APPEARANCES.items():
        render(to_svg(background), ICONSET / name)
        print(ICONSET.relative_to(ROOT) / name)


if __name__ == "__main__":
    main()
