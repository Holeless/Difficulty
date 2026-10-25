# Camouflaged Creepers (NeoForge 1.21.1)

Creepers take on the texture of the block they are standing on. Works with vanilla and modded
blocks, resource packs (including HD ones) and biome tints (grass, leaves, water...).

* **Client-only** - install it on your client; the server does not need it.
* **Minecraft** 1.21.1 / **NeoForge** 21.1.x, Java 21.

Port/remake of *Camouflaged Creepers* 2.0.0 (Forge 1.16.5) by George Tsakiridis (georgetsak).

## Building

CI builds on every push (see `.github/workflows/main.yml`); download the jar from the run's
**Artifacts**, or push a tag like `v3.0.0` to get it attached to a GitHub Release.

Locally (JDK 21):

```
./gradlew build        # jar ends up in build/libs/
./gradlew runClient    # launch a dev client
```

## Customising

`src/main/resources/assets/camoucreepers/json/render_settings.json` controls which blocks are
ignored (the creeper imitates the block below instead), which blocks borrow another block's
texture, and which blocks keep their side texture instead of the `_top` one. Entries can be
block ids (`minecraft:rail`) or block tags (`#minecraft:rails`). Unknown ids are skipped, so
you can list blocks from mods that may not be installed. A resource pack can override this file.

## Changes from the 1.16.5 version

* Ported to NeoForge 1.21.1 (new rendering, registry and resource APIs, `neoforge.mods.toml`).
* Textures are cached in a hash map, failed lookups are cached too (before, they were re-read from
  disk every frame), generated textures are freed, and everything is rebuilt on resource reload
  (switching resource packs now works).
* Biome tints come from the game's own block colours, so any tinted block (including modded
  ones) works, instead of a hard-coded few. Tints are quantised to avoid creating a texture per
  block position.
* HD block textures use the whole texture (before only the top-left 16x16 was used) and the skin is
  generated at matching resolution. Animated textures use their first frame.
* Creepers keep their face on see-through blocks, and no longer become near-invisible on tall
  grass/flowers/glass/etc: sparse textures are skipped and the block below is used.
* `_front` textures (furnaces, droppers...) as well as `_side` ones fall back to `_top`; snow-covered
  grass looks like snow.
* Settings load after registries are ready and one bad id no longer discards the whole file; tags
  are supported; the list is updated for 1.21.1 blocks (new wood types, tuff/deepslate/mud walls...).
* No more `java.awt` use.
* Per-creeper lookups are memoised instead of recomputed each frame.
* Removed the unused `lgbtq.png` and the bundled `grass_top.png` (vanilla's grass top texture is used).

## License

The original mod is *All Rights Reserved* by its author; keep that in mind before redistributing,
and get the author's permission if you publish this.
