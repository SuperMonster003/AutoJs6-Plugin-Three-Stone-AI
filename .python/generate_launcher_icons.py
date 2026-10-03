"""Generate separate transparent UI icons and adaptive/legacy launcher icons.

The retained source alpha is the artwork. Colors and output geometry are generated,
never inferred from antialiased source RGB. Run with --check to verify without writes.
Auto is the default launcher mode. Explicit light and best-effort automatic modes
have independent resources; transparent UI/README icons follow the application theme.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw
import icon_geometry as geometry

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = ROOT / ".python/icons/three-stone-original.png"
SIZE = 432
SCALE = 4
DAY_GLYPH = (0x27, 0x27, 0x27)
NIGHT_GLYPH = (0xD8, 0xD8, 0xD8)
NIGHT_BACKGROUND = (0x21, 0x21, 0x21, 255)
DAY_BACKGROUND = (0xFA, 0xFA, 0xFA, 255)


def source_alpha() -> Image.Image:
    alpha = Image.open(SOURCE).convert("RGBA").getchannel("A")
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("Icon source has no visible artwork")
    alpha = alpha.crop(bounds)
    return alpha


# Optical geometry v1; ratios are derived, not tuned independently by surface.
OPTICAL_X = 0.0
OPTICAL_Y = 0.0
OPTICAL_SCALE = 1.0
UI_GLYPH, ADAPTIVE_GLYPH = geometry.normalized_ratios(source_alpha(), OPTICAL_SCALE)


def render(alpha, ratio, color, background=None):
    return geometry.render(alpha, ratio, color, background,
                           adaptive=ratio == ADAPTIVE_GLYPH,
                           optical_x=OPTICAL_X, optical_y=OPTICAL_Y)


def generated_files() -> dict[Path, bytes]:
    alpha = source_alpha()
    images = {
        "mipmap/ic_launcher.png": render(alpha, UI_GLYPH, DAY_GLYPH),
        "mipmap-night/ic_launcher.png": render(alpha, UI_GLYPH, NIGHT_GLYPH),
        "mipmap/ic_launcher_system.png": render(alpha, UI_GLYPH, NIGHT_GLYPH, NIGHT_BACKGROUND),
        "mipmap/ic_launcher_system_foreground.png": render(alpha, ADAPTIVE_GLYPH, NIGHT_GLYPH),
        "mipmap/ic_launcher_system_light.png": render(alpha, UI_GLYPH, DAY_GLYPH, DAY_BACKGROUND),
        "mipmap/ic_launcher_system_light_foreground.png": render(alpha, ADAPTIVE_GLYPH, DAY_GLYPH),
        "mipmap/ic_launcher_monochrome.png": render(alpha, ADAPTIVE_GLYPH, (0, 0, 0)),
    }
    images["mipmap/ic_plugin_center.png"] = images["mipmap/ic_launcher.png"]
    images["mipmap-night/ic_plugin_center.png"] = images["mipmap-night/ic_launcher.png"]
    result = {}
    for name, image in images.items():
        result[RES / name] = geometry.encode_png(image)
    def adaptive(foreground: str, background: str) -> bytes:
        return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/{background}"/>
    <foreground android:drawable="@mipmap/{foreground}"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
'''.encode("utf-8")
    for name, background in (("ic_launcher_system", "ic_launcher_background"), ("ic_launcher_system_light", "ic_launcher_background_light")):
        result[RES / "mipmap-anydpi-v26" / f"{name}.xml"] = adaptive(f"{name}_foreground", background)
    for name, color in (("ic_launcher_background", "#212121"), ("ic_launcher_background_light", "#FAFAFA")):
        result[RES / "values" / f"{name}.xml"] = (
            '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            f'    <color name="{name}">{color}</color>\n</resources>\n'
        ).encode("utf-8")
    # A real resource keeps its ID in the parsed Manifest. A values resource alias
    # is eagerly resolved by PackageManager and freezes the install-time theme.
    # Each legacy bitmap wrapper has a matching API 26 adaptive configuration.
    for directory, target in (("mipmap", "ic_launcher_system"), ("mipmap-notnight", "ic_launcher_system_light")):
        result[RES / directory / "ic_launcher_system_auto.xml"] = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            f'<bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@mipmap/{target}"/>\n'
        ).encode("utf-8")
    result[RES / "mipmap-anydpi-v26/ic_launcher_system_auto.xml"] = adaptive("ic_launcher_system_foreground", "ic_launcher_background")
    result[RES / "mipmap-notnight-anydpi-v26/ic_launcher_system_auto.xml"] = adaptive("ic_launcher_system_light_foreground", "ic_launcher_background_light")
    result[RES / "raw/keep_plugin_center_icon.xml"] = geometry.KEEP_RESOURCE
    return result


def obsolete_files() -> list[Path]:
    # These exact former resources collided with the transparent UI resource or
    # duplicated launcher layers. Never remove arbitrary files/directories.
    candidates = [RES / "values-night/ic_launcher_background.xml",
                  RES / "values/ic_launcher_system_auto.xml", RES / "values-notnight/ic_launcher_system_auto.xml"]
    for directory in RES.glob("mipmap*"):
        for name in ("ic_launcher.xml", "ic_launcher_round.xml", "ic_launcher_round.png", "ic_launcher_foreground.png"):
            candidates.append(directory / name)
    candidates.append(RES / "mipmap-night/ic_launcher_monochrome.png")
    candidates.extend(RES / name for name in ("mipmap-notnight/ic_launcher_system.png", "mipmap-notnight/ic_launcher_system_foreground.png", "mipmap-notnight-anydpi-v26/ic_launcher_system.xml", "values-notnight/ic_launcher_background.xml", "mipmap-notnight-v29/ic_launcher_system_foreground.png", "values-notnight-v29/ic_launcher_background.xml"))
    return [path for path in candidates if path.is_file()]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check all generated resources without changing files")
    options = parser.parse_args()
    outputs = generated_files()
    stale = [path for path, expected in outputs.items() if not path.is_file() or path.read_bytes() != expected]
    obsolete = obsolete_files()
    if options.check:
        if stale or obsolete:
            raise SystemExit("Stale icon resources: " + ", ".join(str(p.relative_to(ROOT)) for p in stale + obsolete))
        print(f"Verified {len(outputs)} icon resources")
        return
    for path in obsolete:
        if not path.resolve().is_relative_to(RES.resolve()):
            raise ValueError("Icon output escaped resource directory")
        path.unlink()
    for path, data in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        print(f"Generated {path.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
