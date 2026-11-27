# Chunk Atlas

A map of your Minecraft worlds that can also change them.

Chunk Atlas draws your worlds from above, in game or straight from the world list. Pick chunks on the map, then
delete, copy, paste, blend or generate them, without leaving the game.

## The map

- **Every dimension**, drawn from the saved files, in a Detailed or a Vanilla map style, with biome colours, shading and
  a night view.
- **Cave view**: cave floors, or a slice at any height.
- **Seed map**: the land the seed will make beyond what you've explored, with biomes, structures, trees and snow. Off
  until you turn it on.
- **Live while you play**: chunks appear as the game loads and generates them.
- **Layers**: loaded chunks and what keeps them loaded, mobs, spawnable blocks, slime chunks, protected chunks and your
  footsteps.
- **Minimap** with waypoints, a mob radar, cave view and the seed map.

## Pick chunks

- Click, drag a box, paint with a brush, or take whole regions. The Hand tool moves the map.
- **Find chunks by rule**: time spent, last changed, block entities, entities, biome, structure, distance, status and
  more, mixed with *all* or *any*.
- Colour the map by any of those values.
- Protect chunks so no edit can touch them.

## Change them

- **Delete, keep only, or remove entities.** Trim a world down to the parts you care about.
- **Copy and paste** between worlds, with a picture of the paste on the map before you place it.
- **Blend** pasted chunks into the land around them. **Smart** moves a paste up or down to meet the land, and
  structures move with it.
- **Change fields** such as inhabited time, status and force blending, or edit a chunk's data directly.
- **Chunk tasks**: pre-generate land in nine shapes, several tasks at once, with pause, resume, progress and time left.
- **World borders** in any of those shapes, keeping players and teleports inside.
- **Export** chunks to a new world.
- **Undo** any edit made with a backup. Turn on *Keep backup* when you confirm; it's off by default, like MCA
  Selector. Settings lists your backups and their sizes.

## Servers

Put **Chunk Atlas Server** on a server and operators can edit its world from their own map: delete, copy, paste,
blend and generate chunks, either when the server restarts or live in chunks nobody is near.

## Safe by design

Chunk Atlas won't touch a world another game has open. It writes changes so that a crash part-way through can be put
right the next time you open the map, and it keeps everything it saves inside your Minecraft folder.

## Requirements

- Minecraft 26.3
- Fabric Loader and Fabric API
- Mod Menu (optional) opens the settings from the mod list
- Chunk Atlas Server on the server, to edit a server's world

Press **K** in game, or use the **Map** button in the pause menu or the world list.

## Building

```
./gradlew build
```

Needs JDK 25. The jars are in `build/libs/`. See [PORTING.md](PORTING.md) for how the build is laid out for each
Minecraft version.

## License

MIT

---

Made with love,
A7
