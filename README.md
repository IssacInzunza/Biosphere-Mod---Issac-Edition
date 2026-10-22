# Biosphere Worlds - Issac Edition

Biosphere Worlds – Issac Edition is a fork of [NeoBiosphere](https://github.com/mlus-asuka/NeoBiosphere) by mlus, for NeoForge 1.21.1. Full credit to the original author for the concept and base code.

Biosphere Worlds generates the Overworld as floating glass biospheres surrounded by void, with biomes and structures restricted to each sphere. Select **Biospheres - Issac Edition** on the Create World screen to opt into the biosphere terrain. Vanilla Overworld presets remain unchanged.

## Highlights

- Sphere-based Overworld generation with a clean grid layout.
- Multi-biome support inside spheres.
- Highly configurable parameters: radius, spacing, center Y, sphere block, bridge options, and more.
- Per-sphere variety: each sphere gets a random size and block type from configurable lists, while remaining deterministic across world reloads.
- Optional upper-hemisphere mode for sky-island or challenge-style gameplay.
- Automatic sphere-to-sphere bridges for smoother exploration.
- Up to three elevated satellite spheres per main sphere, with glass shells, a stone body, a continuous magma layer and a mixed mineral core, that force players to build upward paths.
- Compatibility-oriented implementation via Biome Modifier and noise routing integration.
- Opt-in world preset that leaves vanilla worlds untouched.
- Biomes outside the spheres are `minecraft:the_void`, so `/locate biome` and biome compasses only find biomes inside spheres.
- Structures are restricted to the spheres, with a configurable edge margin.

## Requirements

- Minecraft: 1.21.1
- NeoForge: 21.1.216+
- Java: 21

## Installation

1. Install NeoForge for Minecraft 1.21.1.
2. Put the mod jar into your `mods` folder.
3. Launch the game and create a new world.

Using a fresh world is strongly recommended to avoid broken borders with previously generated chunks.

## Configuration

Config file path in development environment: `run/config/BiosphereWorlds.toml`

Typical path in normal client/server installations: `<game_dir>/config/BiosphereWorlds.toml`

### Main Settings

```toml
[Settings]
radius = 128
min_radius = 128
spacing = 500
center_y = 62
sphere_block = ["minecraft:glass"]
only_upper_hemisphere = false
close_open_top = false
bedrock_mode = "floor"
generate_bridge = true
bridge_block = "minecraft:oak_planks"
bridge_radius = 1
restrict_structures_to_spheres = true
structure_edge_margin = 16
debug_biome_lookups = false
edge_ocean_enabled = false
edge_ocean_width = 20
edge_ocean_depth = 20
satellites_enabled = true
satellites_max_count = 3
satellites_min_size_percent = 8
satellites_max_size_percent = 12
satellites_min_gap_percent = 25
satellites_max_gap_percent = 75
satellites_min_gap_blocks = 16
satellites_min_elevation_degrees = 30
satellites_max_elevation_degrees = 55
satellites_excluded_ores = [
  "minecraft:ancient_debris",
  "minecraft:nether_quartz_ore",
  "minecraft:nether_gold_ore",
  "#c:ores_in_ground/deepslate"
]
satellites_included_ores = []
satellites_mod_ore_weight = 3
satellites_mod_ore_density_permille = 20
```

- `radius`: Maximum sphere radius.
- `min_radius`: Minimum sphere radius. Set equal to `radius` for fixed-size spheres; set lower to enable per-sphere random sizing.
- `spacing`: Distance between sphere center points on the grid.
- `center_y`: Y level of sphere centers.
- `sphere_block`: List of block IDs used for sphere shells. Each sphere randomly picks one type from the list, deterministically based on its grid position. For example `["minecraft:glass", "minecraft:stone", "minecraft:obsidian"]`.
- `only_upper_hemisphere`: Generate only upper hemispheres when enabled.
- `close_open_top`: Close spheres that pass the build limit with a flat cap at the highest placeable Y (319), using each sphere's shell block. Use it when `center_y + radius` is greater than 321, which is when the shell leaves an open hole at the top; below that value the shell already covers the whole disk and the option changes nothing. Default `false` keeps the natural opening.
- `bedrock_mode`: Bedrock at the world floor. `floor` (default) keeps the `bedrock_floor` surface rule at the bottom of the world (Y -64 to -60, noisy, only where there is stone). `none` leaves no bedrock at the bottom: the carver turns that bedrock into deepslate, so the world floor can be mined through (the Create Diesel Generators pumpjack needs bedrock, so it will not work with this value). `shell` is `floor` plus a 3-block bedrock lining on the inside of the glass shell in the lower half of each sphere (`y <= center_y`), which replaces any block except the shell itself and gives an accessible bedrock layer to build on. With `only_upper_hemisphere` enabled, `shell` behaves exactly like `floor` (there is no lower half to line). Only affects newly generated chunks.
- `generate_bridge`: Enable or disable bridges between adjacent spheres.
- `bridge_block`: Block used for bridges.
- `bridge_radius`: Bridge radius (larger values make thicker bridges).
- `restrict_structures_to_spheres`: Keep structure starts inside biospheres when enabled.
- `structure_edge_margin`: Extra inward margin, in blocks, for structure start positions.
- `debug_biome_lookups`: Enable temporary DEBUG logging for outside-sphere biome routes.
- `edge_ocean_enabled`: Flood the band next to the sphere shell with ocean: the terrain is clamped down to a flat floor so water reaches the glass.
- `edge_ocean_width`: Width of the ocean band, as a percentage of the sphere radius (range `0..100`).
- `edge_ocean_depth`: Depth of the ocean floor below sea level (63), in blocks (range `0..100`).
- `satellites_enabled`: Generate small satellite spheres above each main sphere. Satellites are always complete spheres: a glass shell using the same block as their main sphere, a one-block stone body, a continuous `minecraft:magma_block` layer and a mixed stone/mineral core. They ignore `only_upper_hemisphere`, `close_open_top`, `bedrock_mode` and bridges.
- `satellites_max_count`: Maximum satellites per main sphere (`0..64`). `0` disables them even when `satellites_enabled` is `true`. Each main sphere deterministically gets between 0 and this value.
- `satellites_min_size_percent` / `satellites_max_size_percent`: Satellite radius as a percentage of the main sphere radius (`1..100`; defaults `8` and `12`). If the minimum is greater than the maximum, the maximum is used for both.
- `satellites_min_gap_percent` / `satellites_max_gap_percent`: Gap between the satellite and main sphere surfaces, as a percentage of the main radius (`0..1000`). The absolute minimum is `satellites_min_gap_blocks`.
- `satellites_min_gap_blocks`: Absolute minimum gap in blocks with the main sphere, and also the safety margin from the cell border and from other satellites (`0..1024`).
- `satellites_min_elevation_degrees` / `satellites_max_elevation_degrees`: Preferred elevation angle of the satellites above the main sphere center (`0..90`). A candidate above the build ceiling is lowered, possibly below the configured minimum, while preserving its radial distance, size, gap, seed and horizontal direction. Satellite centers stay at or above the main sphere center. Full-volume height, cell-boundary and collision checks still apply.
- `satellites_excluded_ores`: Block ids or `#tags` that must never be chosen for a satellite core. Defaults: `minecraft:ancient_debris`, `minecraft:nether_quartz_ore`, `minecraft:nether_gold_ore` and `#c:ores_in_ground/deepslate`. Invalid entries are ignored with a warning.
- `satellites_included_ores`: Block ids or `#tags` added to the candidates even when they are not in `#c:ores_in_ground/stone`, for mods that do not tag their ores. Empty by default. Invalid entries are ignored with a warning.
- `satellites_mod_ore_weight`: Selection weight for ores added by other mods (`0..1000`, default `3`). Vanilla ores keep their stage-2 weights.
- `satellites_mod_ore_density_permille`: Ore blocks per 1000 core blocks for mod ores (`0..1000`, default `20` = 2 %). Vanilla ores keep their stage-2 densities.

The vertical satellite placement fix also affects newly generated chunks in
existing worlds because Biospheres uses global configuration and has no saved
placement version. Saved chunks are not rebuilt: satellites spanning old and new
chunks can be incomplete. A new world avoids that boundary. Small cells or
collisions can still prevent placement; a seeded count of zero stays zero,
and disabled satellites remain a no-op.

Mod ores are discovered dynamically from the `#c:ores_in_ground/stone` block tag: that tag contains exactly the ores whose host ground is stone, so deepslate, netherrack, Nether, End and ancient debris variants stay out. The exclusion list is an extra safety net and the inclusion list covers mods that do not tag their ores. Candidates are ordered vanilla first (unchanged) and then mod ores sorted by block id; the exclusion list wins over tags and inclusion. Only blocks with a usable default state are accepted: no air, no fluids, no block entity, and a full solid cube (`canOcclude`).

The catalog is a `volatile` immutable snapshot rebuilt only when the game tags update. **Changes to these lists, the mod ore weight or density apply when the world is reopened or with `/reload`**, not immediately after editing the TOML. The rebuild writes one INFO line with the final candidates `(id, weight, density)` plus the excluded and ignored entries; use it to build your exclusion list. If the snapshot is not ready yet, the game uses the stage-2 vanilla list and logs a warning.

If you install or remove mods, or change these lists between sessions, **a satellite that was only partially generated can show more than one mineral**: chunks generated under the old candidate list keep their mineral, and chunks generated later use the new one.

Satellite placement is deterministic and depends only on the cell coordinates and the options above: no world seed, chunk generation order or shared state. Each satellite is validated with its full volume: it must fit inside its main sphere's cell with the safety margin, stay between Y -64 and 319, and not overlap the main sphere, neighbouring main spheres or the other satellites of the same cell. If no valid position is found after a limited number of attempts, that satellite is skipped without an error.

Each satellite picks one mineral for its whole core, deterministically from its placement seed. The vanilla candidates and their densities (ore blocks per core block) are coal 20%, copper 16%, iron 12%, gold 4%, redstone 3%, lapis 2%, diamond 1% and emerald 0.5%; the rest of the core is stone. A satellite whose interior is too small for the three regions keeps only the glass shell and the stone body.

**Small cores have high variance.** A default-size satellite usually holds a few dozen to a few hundred ore blocks, but a small or unlucky one can end up with very little or even no mineral of its chosen type. That is intentional: the ore is a per-block hash, not a guaranteed payout.

With `center_y = 62`, satellites only fit when the main radius is below roughly 200-300 blocks (at 55 degrees the build limit is the constraint; at 30 degrees the cell width is). Larger main spheres simply generate few or no satellites. The exact ceiling depends on the size, gap and elevation ranges.

### Tuning Notes

- Overlap behavior depends on `spacing` versus `2 * radius`.
- If `spacing > 2 * radius`, spheres have gaps between them.
- If `spacing = 2 * radius`, spheres are tangent.
- If `spacing < 2 * radius`, spheres overlap.

Suggested presets:

- Classic style: `radius=128, min_radius=128, spacing=500, center_y=62`
- Compact exploration: `radius=160, min_radius=160, spacing=320, center_y=70`
- Challenge mode: `only_upper_hemisphere=true, generate_bridge=true`
- Varied spheres: `radius=180, min_radius=80, sphere_block=["minecraft:glass", "minecraft:stone", "minecraft:obsidian"]`

## Compatibility

Biosphere Worlds is designed to support most terrain/ecology mod setups, but world generation is a high-conflict area. Please note:

- Usually compatible with:
  - Mods that mainly add mobs, vegetation, structures, or surface features.
  - Mods that append content through Biome Modifiers.

- Potential conflicts with:
  - Mods or datapacks that replace or modify the `biosphereworlds:biospheres` noise settings or world preset.
  - Mods that deeply alter Carver, Aquifer, or ChunkGenerator pipelines.

- Recommended approach:
  - Treat Biosphere Worlds as the primary world-shape mod.
  - Test in a fresh world before finalizing load order and datapack priority in modpacks.

## World Preset

Choose **Biospheres - Issac Edition** in the Create World screen to use the biosphere generator. The standard vanilla presets, including Overworld, Large Biomes, and Amplified, retain their normal terrain generation.

### TerraBlender and Biomes O' Plenty

The Biospheres preset uses a plain `minecraft:multi_noise` biome source, so TerraBlender can initialize its Overworld regions and TerraBlender-based biomes (for example Biomes O' Plenty) can generate inside the spheres. The old `biosphereworlds:biospheres` biome source remains registered so worlds created by previous versions still load.

**This fix only applies to new worlds.** A world created before this version stores the old biome source wrapper in its `level.dat`; TerraBlender still skips that wrapper, so the world will not generate TerraBlender or Biomes O' Plenty biomes. Create a new world to use the fix. Existing chunks are not regenerated either way.

## FAQ

### 1. Why does my old world look incomplete?

Changing world generation settings does not regenerate already created chunks. A new world is recommended.

### 2. Why are bridges not generating?

Check the following:

- `generate_bridge=true`
- `bridge_radius >= 1`
- `bridge_block` is a valid block ID

### 3. Why are spheres different sizes or using different blocks?

This is expected when `min_radius < radius` or when `sphere_block` contains multiple entries. Each sphere deterministically picks its own size and block type based on its grid coordinates, so reloading the world always produces the same sphere layout.

### 4. Why is the sphere shell block not applied?

Make sure each block ID in the list is valid, for example `minecraft:glass`. Invalid IDs can cause config load failure or fallback behavior. If using a single block type, the list must still be formatted as `["minecraft:glass"]`.

### 5. What should I do if another terrain mod conflicts?

First check whether both mods modify Overworld noise settings or Carver flow. In heavy conflicts, keep only one primary world-shape mod.

## Development and Build

Run in project root:

```bash
# Windows
.\gradlew.bat build

# Linux / macOS
./gradlew build
```

Run development client:

```bash
# Windows
.\gradlew.bat runClient

# Linux / macOS
./gradlew runClient
```

## License

GNU General Public License v3.0

## Feedback

Found a bug or have a suggestion? Open an issue:

- https://github.com/IssacInzunza/Biosphere-Mod---Issac-Edition/issues

## Credits

Original mod: [NeoBiosphere](https://github.com/mlus-asuka/NeoBiosphere) by mlus (GPL-3.0). This fork keeps the original license and copyright notices.
