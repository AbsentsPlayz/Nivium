# Nivium

**Smoother Minecraft for players, on any server.** Fabric, Minecraft Java **1.21.11**, client-side.

You install Nivium on your own PC. It works on any server (Hypixel, SMPs, friends' servers) without the
server having it.

## Engine optimizations
- **Entity occlusion culling:** mobs, items and other entities completely hidden behind solid blocks are not
  processed or drawn. Sodium only hides entities whose whole chunk section is hidden; Nivium also catches
  entities behind walls inside visible sections. Measured: **+41% average FPS** (+26% to +60%) and 3.7× fewer
  garbage collections with 180 mobs behind a wall; no measurable cost when nothing is hidden (docs/BENCHMARKING.md, B-003).
  Safe by design: players, named, glowing, leashed and nearby entities, item frames, holograms and spectator mode
  are never culled. Turns itself off if the EntityCulling mod is installed.

## Features
- **Lag check (press K):** answers "is the lag my PC, the server, or my connection?"
  - **Your PC:** FPS, frame time and freezes
  - **Server:** estimated TPS, measured from the server's own time updates, so it works on any server
  - **Connection:** your ping
- **Freeze detection:** every freeze over 100 ms is recorded and labeled as memory cleanup (Java GC) or something else.
- **FPS boost:** one click lowers the settings that cost the most FPS (render distance ≤ 8, clouds off,
  fewer particles, no entity shadows, biome blend off, entity distance 75%). **Restore** puts back your exact settings.
- **Overlay:** FPS and frame time, plus server TPS and ping, in the corner of the screen.
- **Recommendations:** RAM, launcher memory, render distance, Sodium. Advice only; nothing changes automatically.

## Honest limits
Nivium cannot make someone else's server faster or lower your ping. It makes **your** game smoother and
shows you where lag comes from. For the biggest FPS gain, use it together with **Sodium**.

## Build
JDK 21+ (`JAVA_HOME`), then `gradlew build` → `build/libs/nivium-0.3.1.jar`.

## License
[LGPL-3.0-only](LICENSE) (GPL-3.0 text in [COPYING](COPYING)).

Source: https://github.com/AbsentsPlayz/nivium · Issues: https://github.com/AbsentsPlayz/nivium/issues

Nivium grew out of the earlier Vortex Optimizer client mod; that history is kept in this repository's commits.
