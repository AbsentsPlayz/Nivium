# Modrinth listing text

**Summary** (short description field):

> FPS tools for low-end PCs: one-click benchmarks, a live FPS overlay and a reversible FPS-boost preset. Built to run alongside Sodium.

**Description** (body):

---

## Vortex Optimizer

Vortex Optimizer is a lightweight Fabric mod for players on **weak PCs and laptops** (integrated graphics,
8 GB RAM). It shows how your game is really running, and helps you trade a little visual quality for
smoother gameplay without losing your settings.

It is built to sit **alongside Sodium**, not replace it. Use them together.

### Features

**Vortex menu (press `V`)**
Everything in one screen. The key can be changed in *Options → Controls*.

**One-click benchmarks**
- *Quick 20s*, *Normal 60s* and *Full 120s* runs, with a short warm-up first so loading doesn't skew results
- Measures average FPS, **1% lows**, stutters (frames that take much longer than normal) and memory use
- Tells you how many stutters happened during Java garbage collection
- A small **● REC** counter shows the run's progress
- Your last 5 runs appear in the menu, color-coded, so you can compare settings
- Full reports (JSON plus per-frame CSV) are saved to `.minecraft/vortex-benchmarks/`

**FPS overlay**
A small corner readout of FPS, average frame time and your *worst* frame. Off by default.

**FPS boost preset**
One click lowers the settings that cost the most on weak hardware:
- Render distance capped at 8, simulation distance capped at 6
- Clouds off, particles decreased, entity shadows off
- Biome blend off, entity distance 75%

Your own settings are saved first. **Restore my settings** puts every value back exactly, even after a
restart.

### Lightweight by design
When the overlay and benchmark are off, Vortex does almost nothing each frame. No background threads,
no gameplay changes, and it is safe on servers (client-side only).

### Honest note
Vortex does **not** secretly change the game engine to boost FPS. The FPS boost comes from the vanilla
settings listed above, and you can see exactly what changes. Deeper engine optimizations are in
development and will only ship once benchmarks prove they help.

### Requirements
- Minecraft **1.21.11**
- Fabric Loader **0.17.3+**
- **Fabric API** (required)
- **Sodium** (recommended)

### Commands
Same features as the menu, for those who prefer chat:
`/vortex bench start [seconds] [warmupFrames] [label]` · `/vortex bench status` · `/vortex bench stop`

### License
LGPL-3.0-only

---

**Upload settings:** Project type *Mod* · Loader *Fabric* · Game version *1.21.11* · Environment
*Client: required, Server: unsupported* · Release channel **Alpha** · Dependencies: *Fabric API, required*;
*Sodium, optional* · License *LGPL-3.0-only* · Categories: *Optimization, Utility*.
