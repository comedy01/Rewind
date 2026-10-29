# Rewind

Scrub your singleplayer world back to any moment of the current session, like scrubbing through a video.

Fell in lava? A creeper blew up your house? Lost your pet? Press **R**, drag the timeline back to just before it
happened, and press **Rewind here**. Everything is exactly as it was.

## What goes back

- Every block that was placed, broken, blown up, burned or moved
- Chests, furnaces, hoppers and every other container's contents (and signs, banners, anything with block data)
- Mobs, animals, villagers and pets: where they were, their health, whether they were alive
- Dropped items, arrows, experience orbs
- You: position, health, hunger, XP, inventory, ender chest and potion effects
- Time of day and weather

## How it works in game

- Press **R** (rebindable under Controls → Rewind) to open the timeline. The game pauses.
- The timeline runs from when you joined the world to right now. Drag it and watch the world move: blocks rebuild,
  mobs go back where they stood, your hearts and hotbar change.
- Drag back and forth as much as you like. Nothing is lost while you look.
- **Rewind here** keeps that moment and continues playing from it. **Cancel** or **Esc** returns to the present as if
  nothing happened.
- Markers show the big moments: your deaths (red), named mobs and pets dying (pink), explosions (orange) and going
  to another dimension (purple). Hover one to see what happened, click it to jump to just before it.
- A coloured band under the timeline shows when you were in another dimension (red for the Nether, pale yellow for
  the End, green for the Overworld). Dragging there still shows the dimension you are in now; **Rewind here** takes
  you to where you were.
- The death screen gets a **Rewind** button, which opens the timeline at just before you died.
- Keys on the timeline: ← → step, Shift + ← → five seconds, Home/End for the start and the present, the mouse wheel
  steps too, Enter rewinds.

## Limits

- Current session only. Leaving the world clears the history.
- Singleplayer only (not while the world is opened to LAN).
- Recent history is kept in fine detail (a moment every quarter second for the last minute); older history is kept a
  little coarser (every second up to ten minutes back, every five seconds beyond). Block changes are always exact.
- History has a memory budget (by default a quarter of Minecraft's memory, at most 768 MB; see Settings). A very
  long session drops its oldest part first.

## Settings

Open them from the mod list: the **Config** button on NeoForge, or [Mod Menu](https://modrinth.com/mod/modmenu) on
Fabric. They are saved in `config/rewind.properties`.

- **Open timeline**: the key that opens the timeline (also under Controls).
- **History limit**: how much memory the history may use, from Auto to 4 GB (limited by Minecraft's memory).
- **Marker jump**: how far before a marker, or before your death, the timeline opens (1 to 10 seconds).
- **Rewind on death screen**: show or hide the death screen's Rewind button.

## Compared to Rewinder

Rewinder plays time backwards while it is switched on. Rewind is about choosing an exact moment: scrub back and
forth, preview it, and only commit when you're happy with it.

## Versions

- Fabric (needs Fabric API): 1.20 to 1.20.6, 1.21 to 1.21.11, 26.1 to 26.1.2, 26.2 and 26.3
- NeoForge: 1.21 to 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3
- Forge: 1.20.1

Client-side: it runs inside your singleplayer world.
