# Biosphere Worlds - Issac Edition

## Solar System - Issac Edition

Select **Solar System - Issac Edition** for independent solar generation.
Both configuration files live under **config/biosphereworlds/**:

- `BiosphereWorlds.toml` controls the original Biospheres generator.
- `SolarSystems.toml` controls Solar, with familiar general settings plus six Solar controls.

Their values are independent. A shared definition in SphereConfig supplies the same
labels, defaults and detailed descriptions without duplicating the option logic.
Old root-level files are copied on first start, without deleting the originals or
overwriting destinations. The latest unified settings take precedence over an older
Solar file when initializing the new Solar config.
Worldgen settings are saved with each world's generator. **Create a new Solar world to see
the revised orbits and central surface.** Existing Solar snapshots retain their
original layout (versions 1–6); new worlds use version 7. Editing the TOML does not
relocate saved planets or silently replace a saved world's settings.
Generate Structures must be enabled.

Use `/biospheres locate planets` as a player in a Solar world; operator permission
level 2 or higher is required. It lists the current system's orbital planets, nearest first,
with stable planet numbers, climate, center block coordinates and rounded 3D
distance to each center. The sun and moons are excluded. Between systems, outside
their horizontal radius, it reports that you are outside a solar system. It uses
the world's saved layout without loading or generating chunks, including in
existing Solar worlds. Click the green center coordinates to teleport directly
into the sky above the planet's center, 16 blocks above the world's build ceiling
(Y=336 in the standard Solar Overworld). There is no ground or route search.
The player's game mode is preserved. Links retain their original dimension
and planet; they reject use in another dimension or after losing OP permission.
The teleport height differs from the displayed center Y. Classic Biospheres
and vanilla dimensions reject the command.

Solar worlds also show a violet End-themed guide star toward the current system's
center, slightly above the horizon. It pulses gently, fades over the last 64 blocks
of the horizontal system radius, and disappears outside it. Opaque blocks hide it;
glass remains transparent. This visual also applies to existing Solar worlds and
receives the current center/radius from the server, without changing worldgen.
For navigation without this aid, set `SolarOrbits.solar_guide_enabled = false`
in `config/biosphereworlds/SolarSystems.toml`. The default is `true`. This runtime
toggle applies to existing worlds: disabled on the server, it skips guide geometry
queries and sends no guide packets; disabled on your client, it discards received
guide data and skips orientation calculations and drawing. A server may still send
packets to a client that hides the star locally. After a server-side disable, an
already received star expires within 40 game ticks (about two seconds at 20 TPS).
Smaller planet guide stars point toward the other orbital planets in your current
system: soft orange for warm climates, green for temperate, and light blue for cold.
They use a fixed two-day calendar: each planet appears during either the first or
second half of the dark night (ticks 13000–18000 or 18000–23000), on alternating
days, with 200-tick fades at each end. The calendar follows world day time, including
sleep and time commands. No signal points at the planet you are inside.
Set `SolarOrbits.solar_planet_guides_enabled = false` in the same `SolarSystems.toml`
to hide these independently of the central guide. It defaults to `true` and applies
to existing Solar worlds. Disabled on the server, no planet-guide geometry is
queried or packets sent; received data expires within 40 ticks. Disabled locally,
planet-guide calculations and rendering stop immediately. These signals reuse the
central guide's system-envelope fade and terrain occlusion, at a slightly higher
sky elevation. Their optional network channel leaves the central guide unchanged.
Initial spawn selects the first temperate planet in system (0,0) and uses its
natural surface height. Solar and classic Biospheres share the same login/respawn
correction: if the block at the player's feet is a shell material, search downward
in that column, then nearby columns, for full solid non-shell support and two air
blocks. The player is placed directly above that support on the server thread.
If no destination exists, the position stays unchanged. No platform is built,
blocks are not cleared and radii are not enlarged; radius 1 has no terrain interior.

Solar resolves its mineral catalog once per world opening from the saved settings
and current registries/tags. It logs candidates, exclusions and ignored entries;
ignored entries also produce a warning. With moons enabled, an empty catalog or one
with no positive selection weight is rejected before generation. Disabled moons
skip catalog resolution and validation. `/reload` does not replace the bound Solar
catalog, and reopening does not apply TOML edits to an existing world's snapshot.
Changed mods/tags can change the resolved catalog after restarting; partially
generated moons may therefore contain different minerals across old/new chunks.

Each planet occupies a distinct orbit in layout-v6 and newer worlds, retaining its existing
seeded angular placement and configured Y. The system envelope grows automatically
to include every orbit, its moons and the configured gap between systems.
In layout 7, a moon candidate above the build ceiling is lowered along its original
radial distance, preserving its size, surface gap, seed and horizontal direction.
Its elevation may fall below the configured minimum to fit the full volume.
All collision and system-boundary checks still apply; impossible candidates are
omitted. A seeded count of zero stays zero. Layouts 1–6 and classic Biospheres
retain their previous placement; existing worlds are not migrated.
Planet distance is a **minimum gap between surfaces**, rather than
a distance to a randomly chosen neighboring planet. The system envelope is calculated
automatically, including room for satellites; it is no longer a user-facing option.

The center follows the selected temperature direction: **magma terrain and lava pools**
for `climate_direction = 0`, or **packed ice** for `climate_direction = 1`. Its profile uses Badlands or
Ice Spikes when those biomes exist in the wrapped source. The middle orbital band
always stays temperate. With very few planets, at least one orbital planet is temperate.
The central sphere keeps its required Stronghold size and Y=127 automatically.
With Solar's `bedrock_mode = "SHELL"`, the sun's three-block inner liner ends at
Y=63, rather than following its raised geometric center at Y=127. Orbital planet
liners keep their center-relative ceiling. This affects newly generated chunks;
previously saved walls are not removed automatically. Guardian behavior is unchanged.
Central surface changes occur before structure placement and only at Y>=56; the
complete Stronghold remains at Y<=48. The sun has no mineral moons in version 4. Planets retain their satellites.
With guardians enabled, twelve small obsidian pedestals carry cages distributed
around the entire sun, including its poles. Their 224-block activation range covers
the interior as well as the surface. Each cage attempts up to two guardians every
8–12 seconds, limited to eight marked guardians within 32 blocks of a player and
24 around a sun. Each cycle reduces its spawn budget to the remaining capacity.
Distant spawn candidates are positioned within combat range of the approaching
player (16 blocks for ghasts/blazes, 12 for phantoms), retaining obstruction checks.
Ghasts and blazes defend by day; phantoms may also spawn at night. Peaceful and
`doMobSpawning=false` pause these cages. Destroyed cages are not rebuilt.
Saved layouts 1–5 keep their geometry. Existing marked cages upgrade their combat
settings when enabled, without replacing blocks or recreating destroyed cages; use a
new Solar world for a consistent twelve-cage arrangement. Combat difficulty still
requires in-game acceptance with normal chunk simulation distances.

Solar v5 uses the original Biospheres noise-settings template for terrain, noise
caves, surface rules and the edge-ocean density/climate profile. Runtime adapters
supply Solar sphere centers and immutable world settings; the classic template and
RandomState are not mutated. Extra vanilla biome carvers are omitted, matching the
original preset. There is no post-generation water/air replacement for the ocean.
The sun retains its separate magma/ice profile and protected Stronghold depth.
Existing v1–v4 snapshots retain their old terrain logic: **create a new Solar world
for the shared terrain profile**. Previously generated chunks are not regenerated.

Solar exposes its underlying `minecraft:multi_noise` biome source so TerraBlender
can initialize Biomes O' Plenty regions and surface rules. A per-source Solar
hook applies the void/central biome overrides and changes only the sampler's
temperature before positional biome selection. Climate disabled keeps the original
sampler. Existing generated chunks are unchanged; verify BOP in newly generated
planet terrain, since the hot/cold central body deliberately uses a fixed biome.

### Solar-specific options — SolarOrbits

| Option | Default | Range | Behavior |
|---|---|---|---|
| `planet_count` | `8` | 1–16 | Exact number of orbital planets, excluding the central Stronghold sphere and mineral satellites. Each planet occupies a distinct orbit in new layout-v6 worlds. |
| `planet_gap` | `400` | 0–4000 blocks / bloques | Minimum empty gap between sphere surfaces, in blocks, never center-to-center distance. Also separates the central sphere from the inner orbit. Orbital geometry can produce larger gaps. |
| `system_gap` | `1500` | 0–16000 blocks / bloques | Minimum empty gap between the outer envelopes of neighboring systems, in blocks. System size is calculated from planet sizes and distances automatically. |
| `climate_direction` | `0` | 0–1 (integer slider) | 0: magma/lava center, warm inner planets, temperate middle, cold exterior. 1: packed-ice center, cold inner planets, temperate middle, warm exterior. With very few planets, at least one stays temperate. |
| `guardians_enabled` | `true` | boolean | Generate twelve ghast, phantom and blaze cages covering the sun, with bounded spawn rates and population. Other mobs are unaffected. Saved with each new Solar world. |
| `solar_guide_enabled` | `true` | boolean | Runtime guide-star toggle, also for existing worlds. False on the server stops guide queries and packets for everyone; false locally discards guide data and stops orientation calculations/rendering for that client. A previously received guide expires within 40 ticks after server-side disable. |
| `solar_planet_guides_enabled` | `true` | boolean | Independent runtime toggle for climate-colored orbital planet stars. Each is visible for half a night every two world days, with smooth fades. Server false skips planet-guide queries/packets; local false skips planet-guide calculations/rendering. Received data expires within 40 ticks. Applies to existing Solar worlds. |

### Familiar general options — Settings

These options reuse the Biospheres definitions with independent values in SolarSystems.toml.
The table describes their Solar behavior; existing Solar worlds retain saved values.
Automatic bridges are **always disabled**. Classic grid spacing and hemispheres do
not apply. Solar uses its own `close_open_top` to cap planets cut by world height.
General minimum/maximum pairs are validated together.
Solar uses the positive radii from Settings without an additional planet radius cap.
Orbital centers use the configured Y; world height clips oversized planets. Satellite sizes of 100% are accepted as input, but candidates
as large as their parent are omitted.

| Option | Default | Range | Behavior |
|---|---|---|---|
| `min_radius` | `128` | integer 1..46340 | Smallest orbital sphere radius, in blocks. Must not exceed radius. Set both radii equal for fixed sizes. |
| `radius` | `128` | integer 1..46340 | Largest orbital sphere radius, in blocks. Technical upper limit, not a recommended gameplay size. The central Stronghold sphere keeps its required safe size automatically. |
| `center_y` | `62` | −64–319 | Exact orbital center Y. Oversized spheres are clipped at world height. The Stronghold center remains Y=127. |
| `sphere_block` | `minecraft:glass` | block IDs / ids de bloques | Block ids used for the sphere shells; each sphere selects one deterministically. Empty, missing or unusable choices fall back to glass. Automatic bridges are disabled in Solar. |
| `close_open_top` | `false` | boolean | Close planets cut by the build limit with a flat shell cap at Y=319. Disabled leaves the opening. Does not shrink the configured radius. |
| `bedrock_mode` | `FLOOR` | FLOOR / NONE / SHELL | FLOOR preserves vanilla world-floor bedrock only where a sphere reaches it. NONE replaces it with deepslate. SHELL adds a three-block bedrock lining inside the lower shell. |
| `edge_ocean_enabled` | `false` | boolean / booleano | Lowers and floods the interior band next to orbital shells. The dangerous central sphere keeps its magma or ice profile. |
| `edge_ocean_width` | `20` | 0–100 % | Band width as a percentage of the sphere radius, using the original smooth 3D profile. Zero disables the band. Used only when edge_ocean_enabled is enabled. |
| `edge_ocean_depth` | `20` | 0–100 blocks / bloques | Ocean floor depth below sea level, in blocks. Sea level 63 and depth 20 give a floor at Y=43. Used only when the edge ocean is enabled. |
| `satellites_enabled` | `true` | boolean / booleano | Generate elevated mineral moons around planets, without bridges. Turning this off leaves planet positions and climates unchanged. |
| `satellites_max_count` | `3` | 0–64 | Upper limit per planet. Actual count may be lower when a moon cannot fit without collisions or height violations. Zero disables satellites. |
| `satellites_min_size_percent` | `8` | 1–100 % | Minimum moon radius as a percentage of its parent radius. Must not exceed the maximum. Every accepted moon remains smaller than its parent. |
| `satellites_max_size_percent` | `12` | 1–100 % | Maximum moon radius as a percentage of its parent radius. Larger values may leave less room for moons inside build height. |
| `satellites_min_gap_percent` | `25` | 0–1000 % | Minimum surface gap from the parent, as a percentage of its radius. The absolute minimum in satellites_min_gap_blocks also applies. |
| `satellites_max_gap_percent` | `75` | 0–1000 % | Maximum sampled gap from the parent as a percentage of its radius. Must be at least the minimum percentage; the absolute minimum still takes precedence. |
| `satellites_min_gap_blocks` | `16` | 0–1024 blocks / bloques | Absolute minimum gap between a satellite and any planet or moon, in blocks. The larger of this value and the sampled percentage gap is used. |
| `satellites_min_elevation_degrees` | `30` | 0–90° | Preferred minimum angle above the parent's horizontal plane. Must not exceed the maximum. Solar layout 7 may lower it to fit a complete moon below the build ceiling. |
| `satellites_max_elevation_degrees` | `55` | 0–90° | Maximum angle in degrees; 90 is directly above the parent. Solar layout 7 adapts candidates above the ceiling; candidates still outside build height are omitted. |
| `satellites_excluded_ores` | `classic exclusions / exclusiones clásicas` | IDs or #tags / ids o #tags | Ore block ids or #tags that must not be selected. Exclusions override inclusions. Defaults exclude ancient debris, Nether ores and deepslate ores. |
| `satellites_included_ores` | `[]` | IDs or #tags / ids o #tags | Extra ore ids or #tags, in addition to c:ores_in_ground/stone. Only usable solid blocks without block entities are accepted. |
| `satellites_mod_ore_weight` | `3` | 0–1000 | Selection weight for mod ores; vanilla ores retain their own weights. Zero excludes mod ores from selection. |
| `satellites_mod_ore_density_permille` | `20` | 0–1000 ‰ | Ore blocks per 1000 core blocks for mod ores: 20 means 2%. One mineral type is chosen per moon, behind stone and magma. |

Solar selects central strongholds from `#minecraft:eye_of_ender_located`. Registered
replacements take precedence over `minecraft:stronghold`; their original placement
sets are replaced with Solar's central placement, which also serves eyes and locate.
The replacement keeps its own pieces and processors. Its complete pieces are moved
below Y=48 and must fit inside the existing central sphere and build height; oversized
replacements fail with a diagnostic instead of being clipped or silently omitted.
Without a replacement, vanilla stronghold generation is unchanged.
Natural End portal/frame writes through WorldGenRegion and marked ProtoChunks are
accepted only within an authorized central Stronghold piece placement (the portal
room for vanilla, or the replacement's own pieces).
Other mods that bypass those paths are not certified. Nether and End retain vanilla
generation.

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

Config file path in development environment: `run/config/biosphereworlds/BiosphereWorlds.toml`

Typical path in normal client/server installations: `<game_dir>/config/biosphereworlds/BiosphereWorlds.toml`

**New classic Biospheres worlds save their generation recipe** in
`<world>/data/biosphereworlds_classic.dat` (schema 1). It includes geometry,
shells, bridges, bedrock, ocean settings, structure restrictions and the resolved
satellite mineral list. Later TOML edits or tag reloads do not change that world's
recipe. Create a new world to apply different generation settings. Diagnostic
logging remains a runtime setting.

**Existing classic worlds without this file keep their previous behavior:** they
continue reading the classic TOML and are not automatically migrated. Solar keeps
its own existing snapshot format. A corrupt/unsupported classic recipe or a
missing required block is reported as an error instead of silently using new
defaults. Preserve the recipe file together with the rest of a world backup.

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

- `radius`: Maximum sphere radius, integer `1..46340` (technical integer-square limit, not a recommended gameplay size).
- `min_radius`: Minimum sphere radius, integer `1..radius`. Set equal to `radius` for fixed-size spheres; set lower to enable per-sphere random sizing. An inverted pair is rejected before classic worldgen or creation of a new Solar snapshot.
- `spacing`: Positive integer distance between sphere center points on the grid.
- `center_y`: Y level of sphere centers.
- `sphere_block`: List of block IDs used for sphere shells. Each sphere randomly picks one type from the list, deterministically based on its grid position. For example `["minecraft:glass", "minecraft:stone", "minecraft:obsidian"]`.
- `only_upper_hemisphere`: Generate only upper hemispheres when enabled.
- `close_open_top`: Close spheres that pass the build limit with a flat cap at the highest placeable Y (319), using each sphere's shell block. Use it when `center_y + radius` is greater than 321, which is when the shell leaves an open hole at the top; below that value the shell already covers the whole disk and the option changes nothing. Default `false` keeps the natural opening.
- `bedrock_mode`: Bedrock at the world floor. `floor` (default) keeps the `bedrock_floor` surface rule at the bottom of the world (Y -64 to -60, noisy, only where there is stone). `none` leaves no bedrock at the bottom: the carver turns that bedrock into deepslate, so the world floor can be mined through (the Create Diesel Generators pumpjack needs bedrock, so it will not work with this value). `shell` is `floor` plus a 3-block bedrock lining on the inside of the glass shell in the lower half of each sphere (`y <= center_y`), which replaces any block except the shell itself and gives an accessible bedrock layer to build on. With `only_upper_hemisphere` enabled, `shell` behaves exactly like `floor` (there is no lower half to line). Only affects newly generated chunks.
- `generate_bridge`: Enable or disable bridges between adjacent spheres.
- `bridge_block`: Block used for bridges.
- `bridge_radius`: Bridge radius, integer `0..46340` (larger values make thicker bridges; zero preserves the empty bridge behavior).
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

For **existing classic worlds without a saved recipe**, the catalog is a `volatile` immutable snapshot invalidated when game tags update and resolved on the next enabled classic use, before biome/block generation. Changes to lists, weight or density apply on reopen or `/reload`. New classic worlds resolve the catalog at creation and persist its exact order, weights and densities; they do not rebuild it from later tags or TOML edits. Vanilla worlds and disabled satellites do not resolve it. An empty or all-zero-weight active catalog produces a configuration error; excluded ores are never silently restored by a vanilla fallback.

In classic worlds without a saved recipe, changing mods or lists between sessions can give **a partially generated satellite more than one mineral**. New classic recipes keep their resolved choices; removing a required mineral block produces an explicit error on reopening.

Satellite placement is deterministic and depends only on the cell coordinates and the options above: no world seed, chunk generation order or shared state. Each satellite is validated with its full volume: it must fit inside its main sphere's cell with the safety margin, stay between Y -64 and 319, and not overlap the main sphere, neighbouring main spheres or the other satellites of the same cell. If no valid position is found after a limited number of attempts, that satellite is skipped without an error.

Each satellite picks one mineral for its whole core, deterministically from its placement seed. The vanilla candidates and their densities (ore blocks per core block) are coal 20%, copper 16%, iron 12%, gold 4%, redstone 3%, lapis 2%, diamond 1% and emerald 0.5%; the rest of the core is stone. A satellite whose interior is too small for the three regions keeps only the glass shell and the stone body.

**Small cores have high variance.** A default-size satellite usually holds a few dozen to a few hundred ore blocks, but a small or unlucky one can end up with very little or even no mineral of its chosen type. That is intentional: the ore is a per-block hash, not a guaranteed payout.

Classic satellites that exceed Y319 are lowered while preserving size, placement seed, azimuth and radial distance to their parent. This can relax the minimum elevation and recovers moons around large spheres when sufficient horizontal space exists. Full-volume, cell and collision checks still apply; a narrow cell or an oversized moon can still prevent placement. Solar applies this adjustment only to layout7; layouts1–6 retain their saved behavior.

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
