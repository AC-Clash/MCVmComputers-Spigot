# Component head skins

> **These are not the heads the plugin currently uses.** The shipped catalogue points at
> community-made heads instead — see `HEAD_TEXTURES` in `ComponentType` for what is actually in
> game. This set was drawn first, and is kept as a self-authored alternative: every community head
> depends on someone else's upload staying on Mojang's texture server, and if one disappears these
> can be uploaded to replace it.

One 64×64 Minecraft skin per component. Only the head region of each is used — these are drawn to
serve as the player-head icons in the parts shop and the case bays.

Regenerate with:

```bash
python3 tools/generate_heads.py
```

`preview.png` renders every head as it will appear, so the art can be judged without uploading
anything.

## Getting them in game

These files are not usable on their own, and that is a property of player heads rather than
something missing here. **A head does not contain its texture.** The item carries a URL pointing at
Mojang's texture server, and the client fetches the image from there. So every skin has to be
hosted on that server before a head can reference it.

1. Upload each PNG to <https://mineskin.org> (or any service that registers a skin with Mojang).
2. Copy the texture **value** it returns — the long base64 string.
3. Paste it into `HEAD_TEXTURES` in `ComponentType`, keyed by component id:

   ```java
   HEAD_TEXTURES.put("gpu", "eyJ0ZXh0dXJlcyI6...");
   ```

A bare texture hash or a full `textures.minecraft.net` URL work equally well — head sites are
inconsistent about which they hand out, so all three forms are accepted.

Any component left out keeps its vanilla item icon, so the set can be finished a part at a time and
a half-filled table still works.

## What this costs

The artwork ends up on infrastructure this project does not control. Heads load asynchronously, and
a client that cannot reach Mojang's texture server shows a plain head instead. That is the standing
trade against a resource pack, which would be self-hosted but has to be downloaded by every player
who joins.

## Drawing notes

Every face is 8×8. Three things make that read as an object rather than a smudge:

- **Silhouette over detail.** A fan is a dark ring, not blades. Anything under two pixels dissolves
  into its neighbours.
- **Shading is derived, not drawn.** `SHADE` lights the top, leaves the front neutral and falls off
  towards the back and underside, so the six faces of one part cannot drift apart.
- **Materials are shared.** The same green is every circuit board and the same gold every contact,
  so a motherboard and a stick of RAM look like they came out of the same machine.

Tiers have to be legible at a glance, and each family solves that differently: memory by heat
spreader colour, processors by the tint of the etched marking, monitors by bezel thickness — which
is the only way a size difference can show when every tier gets the same eight pixels.

**A face cannot have rounded corners.** A transparent pixel in the base layer is a hole through the
model, not a silhouette. The generator refuses to write a skin with one.
