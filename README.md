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
generate_bridge = true
bridge_block = "minecraft:oak_planks"
bridge_radius = 1
restrict_structures_to_spheres = true
structure_edge_margin = 16
debug_biome_lookups = false
edge_ocean_enabled = false
edge_ocean_width = 20
edge_ocean_depth = 20
```

- `radius`: Maximum sphere radius.
- `min_radius`: Minimum sphere radius. Set equal to `radius` for fixed-size spheres; set lower to enable per-sphere random sizing.
- `spacing`: Distance between sphere center points on the grid.
- `center_y`: Y level of sphere centers.
- `sphere_block`: List of block IDs used for sphere shells. Each sphere randomly picks one type from the list, deterministically based on its grid position. For example `["minecraft:glass", "minecraft:stone", "minecraft:obsidian"]`.
- `only_upper_hemisphere`: Generate only upper hemispheres when enabled.
- `close_open_top`: Close spheres extending above Y=320 with a one-block flat cap at Y=319, using each sphere's shell material and effective shell radius (including the upper-hemisphere radius adjustment). Disabled by default; affects newly generated chunks. The cap replaces blocks across its disk, including terrain in upper-hemisphere mode. Closing the top removes the natural exit for aircraft; opaque shell materials reduce interior light.
- `generate_bridge`: Enable or disable bridges between adjacent spheres.
- `bridge_block`: Block used for bridges.
- `bridge_radius`: Bridge radius (larger values make thicker bridges).
- `restrict_structures_to_spheres`: Keep structure starts inside biospheres when enabled.
- `structure_edge_margin`: Extra inward margin, in blocks, for structure start positions.
- `debug_biome_lookups`: Enable temporary DEBUG logging for outside-sphere biome routes.
- `edge_ocean_enabled`: Flood the band next to the sphere shell with ocean: the terrain is clamped down to a flat floor so water reaches the glass.
- `edge_ocean_width`: Width of the ocean band, as a percentage of the sphere radius (range `0..100`).
- `edge_ocean_depth`: Depth of the ocean floor below sea level (63), in blocks (range `0..100`).

### Tuning Notes

- Overlap behavior depends on `spacing` versus `2 * radius`.
- If `spacing > 2 * radius`, spheres have gaps between them.
- If `spacing = 2 * radius`, spheres are tangent.
- If `spacing < 2 * radius`, spheres overlap.

| Setting | Default | Effect |
|---|---|---|
| `close_open_top` | `false` | Add a flat shell-material cap at Y=319 to spheres extending above Y=320. |

Limitation: when `2 * radius > spacing`, the existing cell enumeration clips both the shell and the cap by cells; overlapping spheres are not guaranteed to form complete shells or caps. This option preserves that behavior. An empty shell block list (air), or a non-solid shell material, cannot provide a solid enclosure.

The existing heightmap treatment is unchanged: shell materials above `center_y` are ignored by the biosphere heightmap mixin. At `center_y >= 319`, this exclusion does not apply to the cap. Lighting, high bridges touching the cap, and chunk seams must be checked in game, including after saving and reloading.

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
