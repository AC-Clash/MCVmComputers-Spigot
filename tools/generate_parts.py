#!/usr/bin/env python3
"""
Converts the VM Computers mod's Blockbench item models into display-entity geometry.

The mod draws its components as custom item models: a list of axis-aligned cuboids, each face
UV-mapped into a custom texture. A resource pack could render those directly, but this plugin
targets unmodified clients with no pack, so each cuboid becomes one BlockDisplay entity instead.

Two things carry over cleanly and one does not:

  * Geometry is exact. A Blockbench element is a box with a size, a centre and an optional
    rotation about a pivot, which is precisely what a Display transformation expresses. Rotation
    is in fact freer here -- Blockbench allows one axis at a fixed set of angles, a Display takes
    an arbitrary quaternion.
  * Colour is approximate. A BlockDisplay shows one vanilla block, so each element gets the
    closest vanilla block to the texture it was UV-mapped to.
  * Per-face texturing and painted detail are lost. An element with six different faces collapses
    to one block, and detail drawn into a texture -- the key rows on the keyboard, the capacitors
    on the motherboard -- simply is not there. Models that carry their detail in geometry survive;
    models that are a single cuboid relying on their texture come out flat.

Colour is matched on the texture's *dominant* colour rather than its mean. The mean of a
high-contrast texture is a colour that appears nowhere in it: cpu.png is 48% black with gold pins,
and averages to an olive that matches neither. Bucketing to 32 levels per channel and taking the
largest bucket picks black, which is what the chip actually reads as.

Run from the repo root with the mod checkout alongside it:

    python3 tools/generate_parts.py ../MCVmComputers

Writes src/main/resources/parts.json, which is committed -- the mod is not a build dependency.

Model geometry is derived from MCVmComputers (GPLv3), Copyright (C) 2020 Louis Grunenwald.
"""

import json
import math
import os
import sys
import zipfile
from collections import defaultdict

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required: pip3 install Pillow")

# Vanilla blocks that render as a plain full cube with the same texture on every face. Anything
# with a distinct top/side, a non-cube model, or transparency is excluded -- a BlockDisplay shows
# the whole block model, so a furnace would bring its front face along with it.
COLOURS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
           'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']

CANDIDATES = (
    [(f'{c}_concrete', f'{c}_concrete') for c in COLOURS]
    + [(f'{c}_terracotta', f'{c}_terracotta') for c in COLOURS]
    + [(f'{c}_wool', f'{c}_wool') for c in COLOURS]
    + [
        ('coal_block', 'coal_block'),
        ('iron_block', 'iron_block'),
        ('gold_block', 'gold_block'),
        ('diamond_block', 'diamond_block'),
        ('emerald_block', 'emerald_block'),
        ('lapis_block', 'lapis_block'),
        ('redstone_block', 'redstone_block'),
        ('copper_block', 'copper_block'),
        ('exposed_copper', 'exposed_copper'),
        ('netherite_block', 'netherite_block'),
        ('obsidian', 'obsidian'),
        ('deepslate', 'deepslate'),
        ('polished_deepslate', 'polished_deepslate'),
        ('blackstone', 'blackstone'),
        ('polished_blackstone', 'polished_blackstone'),
        ('smooth_stone', 'smooth_stone'),
        ('stone', 'stone'),
        ('andesite', 'andesite'),
        ('polished_andesite', 'polished_andesite'),
        ('diorite', 'diorite'),
        ('polished_diorite', 'polished_diorite'),
        ('granite', 'granite'),
        ('tuff', 'tuff'),
        ('polished_tuff', 'polished_tuff'),
        ('calcite', 'calcite'),
        ('clay', 'clay'),
        ('terracotta', 'terracotta'),
        ('quartz_block_top', 'quartz_block'),
        ('purpur_block', 'purpur_block'),
        ('end_stone', 'end_stone'),
        ('dried_kelp_side', 'dried_kelp_block'),
        ('packed_mud', 'packed_mud'),
        ('moss_block', 'moss_block'),
    ]
)

# Where the nearest-colour match picks something technically close but materially wrong. This is
# the tuning knob: everything else is derived.
#
# Boards are forced to one green so a motherboard and a RAM stick read as the same material --
# consistency of material is worth more than a per-texture colour win. The GPU keeps its brighter
# lime because the mod draws it genuinely brighter, and real cards often are.
#
# Contacts are forced to gold for the same reason: yellow_concrete is a closer colour to the GPU's
# pins, but gold is what a contact is, and the two parts should agree.
OVERRIDES = {
    'motherboard/mvb': 'green_concrete',
    'ramstick/body': 'green_concrete',
    'gpu/body': 'lime_concrete',
    'gpu/pin_small': 'gold_block',
    'gpu/pin_big': 'gold_block',
    'ramstick/pins': 'gold_block',
    # An IO plate is stamped metal. white_concrete is a closer colour but reads as plastic.
    'gpu/io_plate': 'iron_block',
    # 48% pure black with gold pins around it; the mean is an olive that is nowhere in the image.
    'cpu/cpu': 'blackstone',

    # Black moulded plastic. Left alone, the matcher splits this between obsidian and coal_block
    # depending on a couple of RGB units, which would give the plain case and the windowed case
    # different materials and put a purple sheen on the keyboard. Every black shell is one block.
    'pc_case/btm_panel': 'black_concrete',
    'pc_case/top_panel': 'black_concrete',
    'pc_case/front_and_back_panel': 'black_concrete',
    'pc_case/l_and_r_panel': 'black_concrete',
    'pc_case_sidepanel/btm_panel': 'black_concrete',
    'pc_case_sidepanel/top_panel': 'black_concrete',
    'pc_case_sidepanel/front_and_back_panel': 'black_concrete',
    'pc_case_sidepanel/l_panel': 'black_concrete',
    'keyboard/keyboard': 'black_concrete',
    'flatscreen/screen_back': 'black_concrete',
    'flatscreen/screen_u': 'black_concrete',
    'flatscreen/screen_d': 'black_concrete',
    'flatscreen/screen_l': 'black_concrete',
    'flatscreen/screen_r': 'black_concrete',
    'flatscreen/screen_ports': 'black_concrete',
    'flatscreen/screen_stand1': 'black_concrete',
    'walltv/back': 'black_concrete',
    'walltv/top_side': 'black_concrete',
    'walltv/btm_side': 'black_concrete',
    'walltv/l_side': 'black_concrete',
    'walltv/r_side': 'black_concrete',
    'screen_placeholder': 'black_concrete',

    # Beige/white plastic. white_wool is the nearest colour but visibly fuzzy at this scale;
    # concrete is the flat moulded look these want.
    'crtscreen/screen': 'white_concrete',
    'crtscreen/screen_frame_top': 'white_concrete',
    'crtscreen/screen_frame_btm': 'white_concrete',
    'crtscreen/screen_frame_lft': 'white_concrete',
    'crtscreen/screen_frame_right': 'white_concrete',
    'crtscreen/screen_section_1': 'white_concrete',
    'crtscreen/screen_section_2': 'white_concrete',
    'crtscreen/screen_stand_1': 'white_concrete',
    'crtscreen/screen_stand_2': 'white_concrete',
    'mouse/mousetex': 'white_concrete',

    # Cardboard. copper_block is nearer in RGB but unmistakably metal.
    'package/texture': 'packed_mud',
}

# Elements whose texture is a window rather than a surface. A BlockDisplay can hold any block
# state, so glass works exactly as it does in the world.
GLASS = {
    'pc_case_sidepanel/r_panel': 'gray_stained_glass',
}


def dominant_colour(path):
    """Largest colour bucket, quantized to 32 levels per channel, over opaque pixels."""
    image = Image.open(path).convert('RGBA')
    width, height = image.size
    if height > width:  # animated strip; first frame only
        image = image.crop((0, 0, width, width))
    buckets = defaultdict(list)
    for pixel in image.getdata():
        if pixel[3] <= 128:
            continue
        buckets[tuple(c >> 5 for c in pixel[:3])].append(pixel[:3])
    if not buckets:
        return None
    biggest = max(buckets.values(), key=len)
    return tuple(sum(p[i] for p in biggest) / len(biggest) for i in range(3))


def load_vanilla(client_jar, workdir):
    """Extracts vanilla block textures and returns [(block_id, dominant_rgb)]."""
    texdir = os.path.join(workdir, 'assets/minecraft/textures/block')
    if not os.path.isdir(texdir):
        with zipfile.ZipFile(client_jar) as zf:
            for name in zf.namelist():
                if name.startswith('assets/minecraft/textures/block/') and name.endswith('.png'):
                    zf.extract(name, workdir)
    pool = []
    for texture, block in CANDIDATES:
        path = os.path.join(texdir, texture + '.png')
        if not os.path.exists(path):
            print(f'  ! missing vanilla texture {texture}, skipping')
            continue
        rgb = dominant_colour(path)
        if rgb:
            pool.append((block, rgb))
    return pool


def nearest(rgb, pool):
    block, colour = min(pool, key=lambda c: sum((c[1][i] - rgb[i]) ** 2 for i in range(3)))
    return block, math.sqrt(sum((colour[i] - rgb[i]) ** 2 for i in range(3)))


def texture_key(model, ref):
    """
    Resolves an element's texture reference (#3) to a path under the namespace's ``textures/``.

    Model identifiers look like ``mcvmcomputers:item/pc_case/btm_panel``: a namespace, then a path
    relative to the textures root. Most parts live under ``item/``, but not all -- the flat screen
    reaches for ``screen_placeholder`` at the root -- so the prefix cannot be assumed either way.

    Returns the full relative path; :func:`short` trims the common prefix for display.
    """
    seen = set()
    while ref.startswith('#'):
        ref = ref[1:]
        if ref in seen:  # a texture variable pointing at itself
            return None
        seen.add(ref)
        ref = model.get('textures', {}).get(ref)
        if ref is None:
            return None
    return ref.split(':', 1)[1] if ':' in ref else ref


def short(path):
    """Trims the ``item/`` prefix, so override keys and the report stay readable."""
    return path[len('item/'):] if path.startswith('item/') else path


def element_block(model, element, modtex, pool, report):
    """Picks the vanilla block for one element, from whichever texture covers most of its faces."""
    counts = defaultdict(int)
    for face in element.get('faces', {}).values():
        resolved = texture_key(model, face.get('texture', ''))
        if resolved:
            counts[resolved] += 1
    if not counts:
        raise LookupError('element has no resolvable face textures')
    resolved = max(counts, key=counts.get)
    key = short(resolved)

    if key in GLASS:
        report.append((key, GLASS[key], 0.0, 'glass'))
        return GLASS[key]
    if key in OVERRIDES:
        report.append((key, OVERRIDES[key], 0.0, 'override'))
        return OVERRIDES[key]

    path = os.path.join(modtex, resolved + '.png')
    if not os.path.exists(path):
        raise LookupError(f'texture not found: {path}')
    rgb = dominant_colour(path)
    if rgb is None:
        raise LookupError(f'texture is fully transparent: {path}')
    block, err = nearest(rgb, pool)
    report.append((key, block, err, 'auto'))
    return block


def convert(model, name, modtex, pool, report):
    """
    One Blockbench model -> a list of display pieces.

    Blockbench works in a 0..16 grid. The part's anchor is its bottom centre, (8, 0, 8), so a part
    spawned at a location sits on that spot rather than being buried in it. Everything below is
    converted to blocks (/16) and expressed relative to that anchor.
    """
    pieces = []
    for element in model.get('elements', []):
        lo, hi = element['from'], element['to']
        size = [(hi[i] - lo[i]) / 16 for i in range(3)]
        # Degenerate elements (zero thickness) would be invisible and can z-fight; give them the
        # thinnest dimension Minecraft renders cleanly.
        size = [max(s, 1 / 256) for s in size]
        centre = [((hi[i] + lo[i]) / 2 - (8, 0, 8)[i]) / 16 for i in range(3)]

        piece = {
            'block': element_block(model, element, modtex, pool, report),
            'size': [round(s, 6) for s in size],
            'centre': [round(c, 6) for c in centre],
        }

        rotation = element.get('rotation') or {}
        if rotation.get('angle'):
            origin = rotation.get('origin', [8, 8, 8])
            piece['rotation'] = {
                'angle': rotation['angle'],
                'axis': rotation.get('axis', 'y'),
                'pivot': [round((origin[i] - (8, 0, 8)[i]) / 16, 6) for i in range(3)],
            }
        pieces.append(piece)
    return pieces


def main():
    mod_root = sys.argv[1] if len(sys.argv) > 1 else '../MCVmComputers'
    models_dir = os.path.join(mod_root, 'src/main/resources/assets/mcvmcomputers/models/item')
    modtex = os.path.join(mod_root, 'src/main/resources/assets/mcvmcomputers/textures')
    if not os.path.isdir(models_dir):
        sys.exit(f'mod models not found at {models_dir}')

    client_jar = os.environ.get('MC_CLIENT_JAR') or os.path.expanduser(
        '~/Library/Application Support/minecraft/versions/26.2/26.2.jar')
    if not os.path.exists(client_jar):
        sys.exit(f'client jar not found at {client_jar}; set MC_CLIENT_JAR')

    workdir = os.environ.get('PARTS_WORKDIR', '/tmp/vmc-parts')
    os.makedirs(workdir, exist_ok=True)
    print('reading vanilla block textures...')
    pool = load_vanilla(client_jar, workdir)
    print(f'  {len(pool)} candidate blocks')

    report = []
    models = {}
    for filename in sorted(os.listdir(models_dir)):
        if not filename.endswith('.json'):
            continue
        name = filename[:-5]
        with open(os.path.join(models_dir, filename)) as handle:
            model = json.load(handle)
        try:
            pieces = convert(model, name, modtex, pool, report)
        except LookupError as exc:
            sys.exit(f'{filename}: {exc}')
        if pieces:
            models[name] = pieces

    out = {
        '_generated_by': 'tools/generate_parts.py -- do not edit by hand',
        '_source': 'MCVmComputers (GPLv3), Copyright (C) 2020 Louis Grunenwald',
        '_note': 'Blockbench cuboids converted to BlockDisplay transforms. '
                 'centre and size are in blocks, relative to the part bottom centre.',
        'models': models,
    }
    dest = 'src/main/resources/parts.json'
    with open(dest, 'w') as handle:
        json.dump(out, handle, indent=1, sort_keys=False)
        handle.write('\n')

    total = sum(len(p) for p in models.values())
    print(f'\nwrote {dest}: {len(models)} models, {total} display pieces')

    seen = {}
    for key, block, err, how in report:
        seen[key] = (block, err, how)
    print(f'\n{"mod texture":<38}{"vanilla block":<24}{"err":>6}  source')
    print('-' * 78)
    autoerrs = []
    for key in sorted(seen):
        block, err, how = seen[key]
        if how == 'auto':
            autoerrs.append(err)
        print(f'{key:<38}{block:<24}{err:>6.1f}  {how}')
    if autoerrs:
        autoerrs.sort()
        print(f'\nauto-matched colour error: median {autoerrs[len(autoerrs) // 2]:.1f}, '
              f'worst {autoerrs[-1]:.1f} (RGB distance, 0-441)')


if __name__ == '__main__':
    main()
