# Building for more Minecraft versions

The build is split so that one set of sources can be built for several Minecraft versions, and later for other
loaders.

## Layout

```
core/                 the engine: reads, draws and edits saved worlds. Plain Java 21, no game or loader types.
  src/main/java         dev.chunkatlas.engine (region files, NBT, chunk reading, tiles, rules, copy/paste, ...)
  src/main/resources    colours.txt, legacy-ids.txt (block colours, pre-1.13 numeric ids)
  src/test/java         the engine's tests
src/                  the map mod's shared sources, used by every version folder
server/src/           the server add-on's shared sources
versions/<mc>/        one folder per Minecraft version
  gradle.properties     minecraft_version, loader_version, fabric_api_version, loom_version, java_version
  build.gradle          the map mod for that version: loader plugin, dependencies, runs
  src/main/java         code only this version needs: its copy of the adapters (dev.chunkatlas.port)
  server/build.gradle   the server add-on for that version (uses ../gradle.properties)
gradle.properties     mod_version (each jar adds +<mc>), group, dev_minecraft (the version the root run tasks start)
```

All jars land in `build/libs/`: `chunk-atlas-<mod_version>+<mc>.jar` and `chunk-atlas-server-<mod_version>+<mc>.jar`.
Each mod jar carries core's classes and resources inside it, so players install one jar as before.

## Tasks

- `./gradlew build`: core, then every version folder, with all tests.
- `./gradlew :runClient` / `:runAtlasClient` / `:runServer`: the dev client, the AtlasDev client and the dev server of
  the version named by `dev_minecraft`. The full names are `:versions:26.3:runClient`,
  `:versions:26.3:runAtlasClient` and `:versions:26.3:server:runServer`.
- `./gradlew :core:test`: the engine's tests alone.
- The dev games always run in `run/` and `run-server/` at the repo root, whatever the version.

## Rules

- **core stays pure.** No Minecraft, Fabric, Forge or library types, only the JDK, compiled with `--release 21`, so
  any build on Java 21 or newer can use it. It reads every chunk layout the game has used: the pre-1.13 one with
  numeric block ids (translated to names at the edge by `BlockColours.legacyName` and `RuleIds.legacyBiome`), 1.13
  to 1.17 with a `Level` tag, and the current one. Keep those paths and their tests (`LegacyChunkTest` and others).
- **Game-touching code goes through small adapter classes**, so a version folder can swap the adapter rather than
  fork a screen. Adapter signatures use plain Java and core types: blocks and biomes by their modern string id
  (`minecraft:stone`), never game classes, Fabric types or anything that only exists after 1.13. A version whose game
  uses numeric ids translates them inside its adapter.
- When one version needs different code, move only that piece behind an adapter and put each version's copy in
  `versions/<mc>/src/main/java`. Do not copy whole folders.

## Adapters

Shared code calls the game through small classes in package `dev.chunkatlas.port`. Each version folder has its own
copy in `versions/<mc>/src/main/java/dev/chunkatlas/port/` (the server add-on's own ones in
`versions/<mc>/server/src/main/java/dev/chunkatlas/port/`). Ids cross them as strings (`minecraft:overworld`); pure
string work on ids (namespace, path, validity) is `engine/IdText` in core.

| Adapter | What it covers |
|---|---|
| `Game` (client only) | the open screen (`screen`, `open`, `busy`), spawn, day time, night, the camera's eye and view matrix, the picked world in the world list, leaving a level, `mac()`, background workers, and dev-tour calls (GUI resize, screenshot, screen pixel samples, create-world screen, creative world settings, `unthrottled` frames for measuring) |
| `Ids` | `ResourceKey` to id string (`of`, `namespace`, `path`) and back (`key`, `dimension`, `biome`) |
| `Reg` | blocks and items by id (`blockId`, `block`, `blockName`, `blockIds`, `itemId`, `itemReady`) and a world registry's ids (`ids`) |
| `Pos`, `Heights` | chunk position x/z and packing; a level's lowest and highest (inclusive) block y |
| `Version` | the running game's data version, name and id |
| `Net` | the message types (`type("edit")`) and their registration (`c2s`, `s2c`) |
| `Tickets` | chunks in memory and their ticket levels, the tickets that load chunks and their kind, force-loading, a loading ticket of our own for generating, whether a chunk is busy (loaded, loading or unloading), and running the chunk map's unloading while the game is paused |
| `ChunkIo` | a chunk's document, entities and points of interest read and written through the game's own storage while it runs, flushing the disk writer, and whether a saved chunk is fully generated |
| `Perms` (server add-on) | whether a command source or player is an admin |
| `Gfx` (client) | one frame's drawing: fills, text, outlines, pictures (`blit` of a `Tex` or a texture id), `quads` (shapes with a texture corner each, from a repeating unsmoothed picture; on 26.3 a GUI element of its own, needing two widened fields), GUI and block sprites, items, player faces, game widgets, clipping, moves/scales/turns, `layer()`, tooltips, the GUI size |
| `Tex` (client) | the mod's own pictures: `create`, `set` (ARGB), `putAbgr` (bulk, the game's memory layout), `upload` (all, or `upload(x, y, w, h)` for part), `save` (PNG), `close` |
| `Players` | what shaped borders do to a player on the server: moving them (`teleport`, keeping their facing), a sound by id, particles by id seen by one player |
| `AtlasScreen`, `AtlasButton`, `AtlasSlider` (client) | base classes for the mod's screens and widgets, with plain hooks: `draw`, `drawBackground`, `onClick`, `onRelease`, `onDrag`, `onScroll`, `onKey`, `onKeyRelease`, `onChar`, `mods()`; `drawContents`/`drawFrame` on buttons |
| `Keys`, `Input` (client) | keys held, modifier sets, key names, Enter/Esc, the keypad's + and − (`PAD_PLUS`, `PAD_MINUS`), key mapping registration; input handed to a game widget (edit box, button) |
| `Seeds` (client) | the seed map's world generation: whether the version has one (`available`), and opening a `SeedSampler` for a running game's dimension (`open`), a typed seed (`forSeed`) or the Create New World settings (`preview`); why a dimension can't be followed (`unsupported`) |
| `BlockLooks` (client) | the texture on top of a block's model, its tint index and whether it's see-through (`top`), which biome colour a tint asks for or the fixed colour it gives (`tint`, worked out on a small stand-in world class, never a reflection proxy), a resource-pack picture as ARGB (`picture`), map colours, biome colours by `Look.TINT_*` kind (1.21.1 has no dry foliage: foliage), the game's own biomes without a world (`vanilla`) |
| `MobLook` (client) | an entity's type id (`id`) and type by id (`type`), the texture of a mob's variant (`variant`), the cold cow, which mobs are marked neutral; `fix` turns an entry of the shared face table (written for 26.3's models, keyed by entity id) into this version's file names and layout |
| `Skin` (client) | a player's skin by id, by entity, or your own, for `Gfx.face` |
| `Hooks` (client) | HUD elements drawn under chat or the crosshair (`hud`), level changes, client commands (built on plain Brigadier for any source type), chat and game messages, widgets added to the game's screens, the icon button with a GUI sprite, chunks loading on the server |
| `Texts` (client) | hover text and click commands on a chat style, action-bar lines, search-box hints |

Drawing rules for shared code: colours are always ARGB; texture and sprite ids are strings (`minecraft:widget/button`,
`chunkatlas:icon/ui/copy`); key codes are named through `InputConstants.KEY_*` (their numbers differ between versions);
text drawn in a layer sits above that layer's fills, and `Gfx.layer()` starts a new layer on top; tooltips are drawn
last. Each version's `Gfx` keeps those rules (the 1.21.1 copy does it with depth: each layer and the text in it sit a
little higher, and tooltips are kept until the frame ends).

The server add-on compiles the mod's non-client adapters (`Ids`, `Reg`, `Pos`, `Heights`, `Net`, `Version`, `Tickets`,
`ChunkIo`, `Players`) along
with the shared `engine/` and `net/` files; the list is in `versions/<mc>/server/build.gradle`. The 1.21.1 copies are
a first, unchecked version (they compile; nothing has run on 1.21.1 yet). Shared code must stay on Java 21 syntax:
the 1.21.1 build compiles it at `--release 21`.

## Seed map

The shared seed map (`SeedCache`, `SeedMapLayer`, `SeedPanel`, `SeedPreviewScreen`, the minimap's `SeedTiles`, paste
Smart's ground guess) talks only to `seed/SeedSampler`: biomes as the game's numbers and ids, terrain pictures
(`Terrain`), quick ground heights (`groundAt`), chunks made in memory with trees and snow (`makeChunk`, `Tree`),
structure and feature starts with their checks (`Found`), slime chunks, and a few dev-tour checks. Kind names and
biome surface blocks are shared (`seed/SeedKinds`). Each version supplies the generator behind it in its own
`src/main/java/dev/chunkatlas/seed/`: 26.3 has `SeedWorld` and `SeedChunks` (the game's 26.x density functions,
surface rules and features, with `SeedLevel` as the stand-in level placement rules see). A version without one has a
`Seeds` whose `available()` is false: the map's seed button is greyed ("Not available in this Minecraft version"),
the minimap's seed map option is hidden, Create New World has no preview button, and paste Smart uses saved land only.
1.21.1 is such a version for now.

## Access wideners

Private parts of the game (the chunk map's chunks in memory, the tickets, the entity and point-of-interest storage,
the world list's previous screen and picked world, the create-world button, the seed map generator's noise and
surface steps and a foliage placer's radius) are opened by an access widener, never
looked up by name at run time: on an obfuscated game (1.21.1 and older) names looked up by reflection don't exist in a
player's game. Each version folder has its own `src/main/resources/chunkatlas.accesswidener` in that game's names
(26.3: `classTweaker v1 official`; 1.21.1: `accessWidener v2 named`, which Loom turns into intermediary names in the
jar). `gradle.properties` names it (`access_widener`), the build points Loom at it, and both shared `fabric.mod.json`
files take it as `"accessWidener": "${access_widener}"`. The server add-on packs and uses the mod's file. A version
that needs another private part adds a line to its own file; shared code may then use the part directly when its name
is the same in every version, else through an adapter.

## What is still tied to Fabric or to 26.3

Behind adapters now: the open screen, ids and registry lookups, chunk positions and heights, message registration,
permissions, the game version, the client calls listed under `Game`, chunk tickets and storage on the server, and all
drawing, input, key bindings and the mod's own textures (no shared file names the game's draw context, input events,
images or texture ids), block colours and tints from models (`GameColours`), mob faces, textures and player skins
(`MobMarks`, `MapMarkers`, the minimap), HUD registration, client events and commands (`Minimap`, `LiveChunks`, the map
buttons in `ChunkAtlas` and `InGame`), and chat components. Still direct (the next stage): the seed map's world
generation, which is the 26.3 `SeedWorld` behind `SeedSampler`; 1.21.1 has none yet (`Seeds.available()` is
false there).

On 1.21.1, Fabric API has no ordered HUD elements, so `Hooks.hud` draws after the whole HUD at a depth just under the
element it should sit under (the game's HUD layers are 200 apart with a depth test). The minimap stays under chat, but
where they overlap the minimap is hidden behind chat's dark background instead of showing through it.


The shared `src/` is Fabric- and 26.3-specific today. Most files touch the game directly; these also use Fabric API
or the Fabric loader: `ChunkAtlas`, `InGame`, `LiveMap`, `Footsteps`, `ServerLink`, `Generating`, `net/AtlasNet`,
`minimap/Minimap`, `minimap/Config`, `minimap/ChunkAtlasLink`, `minimap/waypoint/Waypoints`, and every file in
`server/src`. The game-bound engine parts stay in the shared sources (`engine/ChunkGeneration` drives the server's
chunk generator, `engine/WorldLock` takes the game's world lock) and are compiled into both the mod and the add-on.
The next phase puts the GUI, the minimap and these files behind adapters.

`fabric.mod.json` takes `minecraft_version` and `java_version` from the version folder.

## Adding a version

1. Copy `versions/26.3/` to `versions/<mc>/` (the build finds every folder with a `build.gradle`).
2. Set its `gradle.properties`. For an obfuscated game (1.21.1 and older) use the `fabric-loom` plugin with
   `mappings loom.officialMojangMappings()` and `modImplementation` for the loader and Fabric API.
3. Build it, then move what fails to compile behind an adapter, with this version's copy in its own `src/main/java`.
4. A different loader (Forge-based, for example) is a version folder with that loader's Gradle plugin and its own
   entry and glue classes in its `src/main/java`; it still uses `core` and as much of the shared sources as the
   adapters allow. Its resources (for example `mcmod.info` instead of `fabric.mod.json`) go in its own
   `src/main/resources`.
