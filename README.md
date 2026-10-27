# Nivium

**Smoother Minecraft for players, on any server.** Fabric, Minecraft Java **1.21.11**, client-side.

You install Nivium on your own PC. It works on any server (Hypixel, SMPs, friends' servers) without the
server having it.

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
JDK 21+ (`JAVA_HOME`), then `gradlew build` → `build/libs/nivium-0.2.0.jar`.

## License
[LGPL-3.0-only](LICENSE) (GPL-3.0 text in [COPYING](COPYING)).

Source: https://github.com/AbsentsPlayz/nivium · Issues: https://github.com/AbsentsPlayz/nivium/issues

Nivium grew out of the earlier Vortex Optimizer client mod; that history is kept in this repository's commits.
