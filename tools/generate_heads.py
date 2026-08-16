#!/usr/bin/env python3
"""
Draws the component head skins.

A player head is the only vanilla item whose texture a server chooses, which makes it the only way
to get a graphics card that looks like a graphics card into a chest menu. This authors those
textures: one 64x64 Minecraft skin per component, of which only the head region is used.

    python3 tools/generate_heads.py

Writes tools/heads/*.png and tools/heads/preview.png.

The output is not usable on its own. A head carries a *URL* on Mojang's texture server rather than
the image itself, so each file has to be uploaded (mineskin.org) to get a texture value, and those
values go in ComponentType.HEAD_TEXTURES.

Drawing notes
-------------
Every face is 8x8, which is the whole budget. Three things make that read as an object rather than
a smudge:

  * Silhouette over detail. At this size a fan is a dark ring, not blades. Anything smaller than
    two pixels disappears into its neighbours.
  * Shading by face, applied here rather than drawn by hand, so the six faces of one part cannot
    drift apart. Top is lit, front is neutral, sides fall off, the back and underside are darkest.
    This is what stops a head looking like a sticker on a cube.
  * Consistent materials across parts. The same green is every circuit board, the same gold is
    every contact -- so a motherboard and a stick of RAM look like they came from the same machine.

Faces are laid out as the skin format wants them: top and bottom on the upper strip, then right,
front, left and back along the second.
"""

import os
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow is required: pip3 install Pillow")

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "heads")

# Head face origins in a 64x64 skin.
FACES = {
    "top":    (8, 0),
    "bottom": (16, 0),
    "right":  (0, 8),
    "front":  (8, 8),
    "left":   (16, 8),
    "back":   (24, 8),
}

# Per-face light levels. Hand-shading six faces per part would drift; deriving them keeps a part
# looking like one solid object.
SHADE = {"top": 1.12, "front": 1.0, "right": 0.86, "left": 0.86, "back": 0.74, "bottom": 0.62}

PALETTE = {
    " ": (0, 0, 0, 0),
    # blacks and greys -- cases, shrouds, keyboards
    "K": (13, 13, 13, 255),
    "k": (26, 26, 26, 255),
    "d": (45, 45, 45, 255),
    "g": (74, 74, 74, 255),
    "h": (108, 108, 108, 255),
    # metals
    "s": (154, 160, 166, 255),
    "S": (200, 205, 210, 255),
    "w": (232, 234, 237, 255),
    # circuit board green -- one green for every board in the catalogue
    "G": (34, 96, 42, 255),
    "e": (58, 138, 66, 255),
    # contacts
    "o": (198, 150, 44, 255),
    "O": (240, 198, 90, 255),
    # screens
    "b": (30, 56, 92, 255),
    "B": (74, 146, 214, 255),
    "c": (158, 214, 248, 255),
    # accents
    "L": (92, 255, 122, 255),   # power led
    "r": (192, 57, 43, 255),
    "p": (140, 90, 190, 255),
    "n": (176, 110, 60, 255),   # bronze
    "1": (0, 0, 0, 0),          # placeholder, replaced per part
}

BLANK = ["        "] * 8


def shade(colour, factor):
    if colour[3] == 0:
        return colour
    return tuple(min(255, int(c * factor)) for c in colour[:3]) + (255,)


def draw_face(image, face, art, overrides):
    ox, oy = FACES[face]
    factor = SHADE[face]
    palette = dict(PALETTE)
    palette.update(overrides)
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            colour = palette.get(ch, PALETTE[" "])
            image.putpixel((ox + x, oy + y), shade(colour, factor))


def build(name, faces, overrides=None):
    """faces: dict of face -> 8 strings. Missing faces fall back to 'side', then 'front'."""
    overrides = overrides or {}
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    side = faces.get("side") or faces.get("front") or BLANK
    for face in FACES:
        art = faces.get(face)
        if art is None:
            art = side if face in ("right", "left", "back") else faces.get("front", BLANK)
        if face == "bottom" and "bottom" not in faces:
            art = faces.get("top", side)
        draw_face(image, face, art, overrides)
    image.save(os.path.join(OUT, name + ".png"))
    return image


# ---------------------------------------------------------------------------
# the catalogue
# ---------------------------------------------------------------------------

def tower():
    """A tower case: drive slot, power light, vented front and top."""
    return {
        "front": [
            "kkkkkkkk",
            "kdSSSSdk",   # optical drive
            "kkkkkkkk",
            "kkkkkkkk",
            "kdddddLk",   # power led
            "kkkkkkkk",
            "kdkdkdkk",   # intake vents
            "kdkdkdkk",
        ],
        "top": [
            "kkkkkkkk",
            "kdkdkdkk",
            "kkkkkkkk",
            "kdkdkdkk",
            "kkkkkkkk",
            "kdkdkdkk",
            "kkkkkkkk",
            "kkkkkkkk",
        ],
        "side": [
            "kkkkkkkk",
            "kkkkkkkk",
            "kdddddkk",
            "kdddddkk",
            "kdddddkk",
            "kdddddkk",
            "kkkkkkkk",
            "kkkkkkkk",
        ],
    }


def side_panel():
    """The tinted window, as a pane in a frame."""
    return {
        "front": [
            "gggggggg",
            "gkkkkkkg",
            "gkbbbbkg",
            "gkbBBbkg",
            "gkbBBbkg",
            "gkbbbbkg",
            "gkkkkkkg",
            "gggggggg",
        ],
        "top": [
            "gggggggg",
            "gkkkkkkg",
            "gkkkkkkg",
            "gkkkkkkg",
            "gkkkkkkg",
            "gkkkkkkg",
            "gkkkkkkg",
            "gggggggg",
        ],
    }


def board(slots_accent):
    """A motherboard seen from above: socket, memory slots, expansion slot."""
    return {
        "top": [
            "GGGGGGGG",
            "GkkkkGGG",
            "GkddkG11",   # socket, then memory slots
            "GkddkG11",
            "GkkkkG11",
            "GGGGGG11",
            "GoooooGG",   # expansion slot
            "GGGGGGGG",
        ],
        "front": [
            "GGGGGGGG",
            "GGGGGGGG",
            "GkkGGkkG",
            "GkkGGkkG",
            "GGGGGGGG",
            "GGGGGGGG",
            "oooooooo",
            "GGGGGGGG",
        ],
        "side": [
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GkkkkkkG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
        ],
    }, {"1": slots_accent}


def processor(mark):
    """A chip: heat spreader on top, substrate at the edges, pins underneath."""
    return {
        "top": [
            "GGGGGGGG",
            "GSSSSSSG",
            "GSwwwwSG",
            "GSw11wSG",   # etched marking, tinted per tier
            "GSw11wSG",
            "GSwwwwSG",
            "GSSSSSSG",
            "GGGGGGGG",
        ],
        "front": [
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GSSSSSSG",
            "GGGGGGGG",
            "oooooooo",
            "oooooooo",
        ],
        "bottom": [
            "GGGGGGGG",
            "GoOoOoOG",
            "GOoOoOoG",
            "GoOoOoOG",
            "GOoOoOoG",
            "GoOoOoOG",
            "GOoOoOoG",
            "GGGGGGGG",
        ],
    }, {"1": mark}


def graphics():
    """A card: shroud and fan across the face, contacts along the bottom edge."""
    return {
        "front": [
            "GGGGGGGG",
            "GkkkkkkG",
            "GkhddhkG",
            "GkdssdkG",
            "GkdssdkG",
            "GkhddhkG",
            "GkkkkkkG",
            "oooooooo",
        ],
        "top": [
            "GGGGGGGG",
            "GkkkkkkG",
            "GkddddkG",
            "GkddddkG",
            "GkddddkG",
            "GkddddkG",
            "GkkkkkkG",
            "GGGGGGGG",
        ],
        "side": [
            "GGGGGGGG",
            "GkkkkkkG",
            "GkkkkkkG",
            "GkkkkkkG",
            "GkkkkkkG",
            "GkkkkkkG",
            "GkkkkkkG",
            "oooooooo",
        ],
    }


def drive():
    """A 3.5 inch drive: machined lid, label, screw points."""
    return {
        "top": [
            "sSSSSSSs",
            "SwwwwwwS",
            "SwkkkkwS",
            "SwkddkwS",
            "SwkddkwS",
            "SwkkkkwS",
            "SwwwwwwS",
            "sSSSSSSs",
        ],
        "front": [
            "ssssssss",
            "sSSSSSSs",
            "ssssssss",
            "ssssssss",
            "ssssssss",
            "ssssssss",
            "sSSSSSSs",
            "ssssssss",
        ],
        "side": [
            "sSSSSSSs",
            "ssssssss",
            "ssssssss",
            "sddddddss"[:8],
            "ssssssss",
            "ssssssss",
            "ssssssss",
            "sSSSSSSs",
        ],
        "bottom": [
            "GGGGGGGG",
            "GkkkkkkG",
            "GkGGGGkG",
            "GkGGGGkG",
            "GkGGGGkG",
            "GkGGGGkG",
            "GkkkkkkG",
            "GGGGGGGG",
        ],
    }


def memory(accent):
    """A stick: heat spreader stripe on top, chips down the face, contacts beneath."""
    return {
        "front": [
            "11111111",   # heat spreader, coloured per capacity
            "GGGGGGGG",
            "GkkGkkGG",
            "GkkGkkGG",
            "GGGGGGGG",
            "GkkGkkGG",
            "GGGGGGGG",
            "oooooooo",
        ],
        "top": [
            "11111111",
            "11111111",
            "11111111",
            "11111111",
            "11111111",
            "11111111",
            "11111111",
            "11111111",
        ],
        "side": [
            "11111111",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "oooooooo",
        ],
    }, {"1": accent}


def keys():
    """A keyboard: rows of caps on a dark deck."""
    return {
        "top": [
            "kkkkkkkk",
            "kSkSkSkk",
            "kkkkkkkk",
            "kSkSkSkk",
            "kkkkkkkk",
            "kSkSkSkk",
            "kkkkkkkk",
            "kkSSSSkk",   # space bar
        ],
        "front": [
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kddddddk",
            "kkkkkkkk",
            "kkkkkkkk",
        ],
        "side": [
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kdddddkk",
            "kkkkkkkk",
            "kkkkkkkk",
        ],
    }


def rodent():
    """A mouse: two buttons and a wheel, rounded corners."""
    return {
        "top": [
            "SwwwwwwS",
            "wwwddwww",
            "wwwkkwww",   # wheel
            "wwwkkwww",
            "wwwddwww",   # button split
            "wwwddwww",
            "wwwwwwww",
            "SwwwwwwS",
        ],
        "front": [
            "SwwwwwwS",
            "wwwwwwww",
            "wwwwwwww",
            "SSSSSSSS",
            "ssssssss",
            "ssssssss",
            "gggggggg",
            "dddddddd",
        ],
        "side": [
            "SwwwwwwS",
            "wwwwwwww",
            "wwwwwwww",
            "SSSSSSSS",
            "ssssssss",
            "ssssssss",
            "gggggggg",
            "dddddddd",
        ],
    }


def screen(bezel):
    """
    A monitor. Bezel thickness carries the tier, which is the only way size can show at this
    resolution -- a big screen and a small one are otherwise the same eight pixels.
    """
    art = []
    for y in range(8):
        row = ""
        for x in range(8):
            edge = min(x, y, 7 - x, 7 - y)
            if edge < bezel:
                row += "k"
            elif edge == bezel:
                row += "b"
            else:
                row += "B" if (x + y) % 5 else "c"
        art.append(row)
    return {
        "front": art,
        "top": [
            "dddddddd",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
            "kkkkkkkk",
        ],
        "side": [
            "kkkkkkkk",
            "kkkkkkkk",
            "kdddddkk",
            "kdddddkk",
            "kdddddkk",
            "kdddddkk",
            "kkkkkkkk",
            "kkkkkkkk",
        ],
        "back": [
            "kkkkkkkk",
            "kdddddkk",
            "kdkkkdkk",
            "kdkkkdkk",
            "kdddddkk",
            "kkkkkkkk",
            "kkhhhhkk",   # stand neck
            "khhhhhhk",
        ],
    }


CATALOGUE = [
    ("pc_case", tower(), {}),
    ("case_side_panel", side_panel(), {}),
]

_board_32, _ov = board((198, 150, 44, 255))
CATALOGUE.append(("motherboard", _board_32, _ov))
_board_64, _ov = board((80, 140, 200, 255))
CATALOGUE.append(("motherboard64", _board_64, _ov))

for _id, _mark in [("cpu2", (240, 198, 90, 255)),
                   ("cpu4", (200, 205, 210, 255)),
                   ("cpu6", (176, 110, 60, 255))]:
    _art, _ov = processor(_mark)
    CATALOGUE.append((_id, _art, _ov))

CATALOGUE.append(("gpu", graphics(), {}))
CATALOGUE.append(("harddrive", drive(), {}))

# Capacity reads off the heat spreader colour, cool and dull through to warm and bright.
for _id, _accent in [("ram64m", (90, 90, 90, 255)),
                     ("ram128m", (120, 120, 120, 255)),
                     ("ram256m", (70, 150, 90, 255)),
                     ("ram512m", (60, 160, 170, 255)),
                     ("ram1g", (60, 120, 200, 255)),
                     ("ram2g", (140, 90, 190, 255)),
                     ("ram4g", (240, 198, 90, 255))]:
    _art, _ov = memory(_accent)
    CATALOGUE.append((_id, _art, _ov))

CATALOGUE.append(("keyboard", keys(), {}))
CATALOGUE.append(("mouse", rodent(), {}))

# Bezel thins as the screen grows: three pixels of housing down to none, which is the only way a
# size difference can show when every tier gets the same eight pixels.
for _id, _bezel in [("monitor_small", 3), ("monitor_medium", 2),
                    ("monitor_large", 1), ("monitor_xlarge", 0)]:
    CATALOGUE.append((_id, screen(_bezel), {}))


# ---------------------------------------------------------------------------
# preview
# ---------------------------------------------------------------------------

def iso_head(skin, scale=10):
    """
    Draws one head as an isometric cube, so the faces can be judged together.

    Each source pixel becomes its own parallelogram rather than being resampled, which keeps the
    art crisp instead of blurring it into the projection.
    """
    w = 16 * scale
    h = 16 * scale + 8 * scale
    canvas = Image.new("RGBA", (w + 1, h + 1), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    apex = (w / 2, 0)
    u = (scale, scale / 2.0)      # towards the right face
    v = (-scale, scale / 2.0)     # towards the left face
    down = (0, scale)

    def quad(origin, a, b, i, j):
        p0 = (origin[0] + a[0] * i + b[0] * j, origin[1] + a[1] * i + b[1] * j)
        p1 = (p0[0] + a[0], p0[1] + a[1])
        p2 = (p1[0] + b[0], p1[1] + b[1])
        p3 = (p0[0] + b[0], p0[1] + b[1])
        return [p0, p1, p2, p3]

    def face(name, origin, a, b, flip_x=False):
        ox, oy = FACES[name]
        for j in range(8):
            for i in range(8):
                sx = (7 - i) if flip_x else i
                colour = skin.getpixel((ox + sx, oy + j))
                if colour[3] == 0:
                    continue
                draw.polygon(quad(origin, a, b, i, j), fill=colour)

    # top, then the two faces facing the viewer
    face("top", apex, u, v)
    left_origin = (apex[0] + v[0] * 8, apex[1] + v[1] * 8)
    face("front", left_origin, u, down)
    right_origin = (apex[0] + u[0] * 8, apex[1] + u[1] * 8)
    face("left", right_origin, v, down, flip_x=True)
    return canvas


def contact_sheet(images):
    cols = 6
    cell_w, cell_h = 180, 210
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (28, 30, 34, 255))
    draw = ImageDraw.Draw(sheet)
    for index, (name, skin) in enumerate(images):
        cube = iso_head(skin, scale=9)
        cx = (index % cols) * cell_w
        cy = (index // cols) * cell_h
        sheet.paste(cube, (cx + (cell_w - cube.width) // 2, cy + 20), cube)
        draw.text((cx + 8, cy + cell_h - 26), name, fill=(220, 224, 230, 255))
    return sheet


def transparent_faces(skin):
    """
    Face names containing a see-through pixel.

    A transparent pixel in a head's base layer is not a rounded corner, it is a hole through the
    model -- which is exactly what a silhouette drawn with blank space produces.
    """
    bad = []
    for face, (ox, oy) in FACES.items():
        for y in range(8):
            for x in range(8):
                if skin.getpixel((ox + x, oy + y))[3] == 0:
                    bad.append(face)
                    break
            else:
                continue
            break
    return bad


def main():
    os.makedirs(OUT, exist_ok=True)
    built = []
    for name, faces, overrides in CATALOGUE:
        for key, art in faces.items():
            for row in art:
                if len(row) != 8:
                    sys.exit(f"{name}/{key}: row is {len(row)} wide, must be 8: {row!r}")
        skin = build(name, faces, overrides)
        holes = transparent_faces(skin)
        if holes:
            sys.exit(f"{name}: transparent pixels in {', '.join(holes)} -- a cube face cannot have "
                     f"a hole in it, every one of the 64 pixels must be opaque")
        built.append((name, skin))
        print(f"  {name}.png")

    contact_sheet(built).save(os.path.join(OUT, "preview.png"))
    print(f"\n{len(built)} skins written to {OUT}")
    print("preview.png shows them as they will render.")


if __name__ == "__main__":
    main()
