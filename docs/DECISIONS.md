# Design decisions

## D-010 Engine optimizations chosen from profiling (2026-10-27)
The owner asked for real optimizations "like other mods", not presets or dashboards. Choices come from R-009:
1. **Entity renderer cache** (`EntityRenderDispatcherMixin` + `EntityTypeMixin`). The renderer of a non-avatar
   entity depends only on its type, so it is cached on the `EntityType` (about 150 objects, a few KB) and
   invalidated by a generation counter bumped at `onResourceManagerReload` HEAD and TAIL. Players and mannequins
   keep the vanilla path because their renderer follows the skin model, which can change. `@Redirect` on the
   three call sites instead of a cancellable `@Inject` avoids a callback allocation per call.
2. **Entity occlusion culling** (`EntityOcclusionCuller`, `VoxelRay`). Per-entity ray tests through the block grid
   on the render thread (no worker thread, no world snapshot, no unsafe concurrency). Only `isSolidRender` blocks
   occlude. 12 sample points on the enlarged AABB, any clear ray means visible. Results are cached (hidden rechecked
   after 6 frames, visible after 10, or immediately when camera or entity moved 0.25 blocks). Exempt: avatars,
   glowing/highlighted, named, leashed, camera vehicle, item frames/paintings/knots, display entities, ender
   dragon, boxes > 8 blocks, anything within 3 blocks, spectator mode. Off when EntityCulling is installed.
3. **Benchmark harness** (`BenchHarness`, `runBenchClient`, scenes none/occluded/visible): dev-only, activated by a
   system property; makes every number in BENCHMARKING.md reproducible.
- **Rejected:** `animateTick` fluid lookup shortcut (compatibility risk vs 1.5% at 20 Hz); hand-rendering and
  GlRenderPass changes (real GPU work or deep blaze3d rewrites with Sodium/IF overlap); render-thread allocation
  work (no dominant site; GC pressure is elsewhere).
- Existing features (lag check, freeze labels, recommendations, overlay, FPS preset) are unchanged.

## D-009 Nivium becomes a player (client-side) mod (2026-10-27, owner's decision)
The owner plays on other people's servers as a member and wants Nivium to help *them*, not the server.
Server-side code cannot affect a server the player does not control, so it was removed rather than kept as an
unused bonus (owner chose "Player mod" over "keep both").
- **Removed:** tick monitor, adaptive simulation/view distance, server recommendations, `/nivium` commands,
  `config/nivium.json`, `TimingStats` and `RollingTimes` (no remaining users). They are preserved in git history (commit e69544d).
- **Added:** lag check (`LagVerdict`) that combines this PC's FPS and freezes, the server's TPS estimated from
  `ClientboundSetTimePacket` (sent every 20 server ticks; game-time delta / real-time delta over 10 s, works on
  any vanilla-protocol server) and ping (`PlayerInfo.getLatency`). Freeze detection (`StutterLog`, frames > 100 ms
  while playing) with GC attribution (`GcEventLog`), which addresses the GC/low-RAM freezes measured in B-002.
  Client recommendations (`ClientAdvice`) covering RAM, -Xmx, render distance and Sodium.
- **Kept:** FPS boost preset (reversible), FPS overlay (now with a TPS/ping line), K screen, client config migration.
- **Honesty:** the FPS gain comes from the preset's vanilla settings plus Sodium. The lag check measures; it
  does not by itself make anything faster.

## D-008 Project becomes Nivium, a server-side optimizer (2026-10-27, owner's direction)
- **Renamed** Vortex Optimizer → Nivium: mod id `nivium`, package `io.github.nivium`, version 0.2.0. Still
  Fabric, 1.21.11, Java 21; no platform switch.
- **Removed the 20/60/120 s workflow:** `BenchmarkSession`, `BenchmarkResult`, `FrameRecorder`,
  `ReportIndex`, `BenchmarkController`, `EnvironmentCollector`, `/vortex bench`, the benchmark buttons and
  the REC HUD. It was a client frame-time benchmark, not a world scanner. No world-scanning or
  scan-based preset code ever existed, so nothing like that had to be removed.
- **Kept and reused:** `FrameStats` → `TimingStats` (spike rule now a parameter; used for MSPT),
  `GcEventLog` (always-on ring, spike attribution), `JvmProbe`. The client extras (V screen, FPS overlay,
  `LowEndPreset`) stay as an optional client module because they work and don't conflict with
  server operation.
- **The real optimization is adaptive simulation distance.** Chunks within simulation distance tick entities,
  block entities, random and scheduled ticks, so it directly bounds tick work. Changing it live through
  `PlayerList.setSimulationDistance` is supported vanilla behavior and is reversible. Safety comes from a
  10 s mean (not spikes), a 30 s sustain, a 120 s recovery, a 45/30 ms hysteresis band, a 60 s cooldown, a
  floor of 6, a ceiling at the admin's value, adopting outside changes instead of fighting them, restore
  on stop, and staying off on integrated servers (the host client overwrites the value) and with ServerCore.
  **Not yet measured under real load**: the dev server had no players, and Nivium deliberately does not
  step down with 0 players.
- **Not done:** no entity removal, no world edits, no async world access, no networking changes. Duplicating
  Lithium-style tick optimizations was out of scope; Nivium recommends Lithium instead.

## D-001 Toolchain: Mojang mappings, pinned Loom 1.19.3 (2026-10-26)
Follow the official 1.21.11 template (R-001) but pin `fabric-loom-remap` to the released `1.19.3`
instead of `1.19-SNAPSHOT`, so builds are reproducible. Mojang mappings match Fabric's current
docs and the unobfuscated 26.x line, minimising porting cost later.

## D-002 First feature is measurement, not an optimization (2026-10-26)
The directive requires every optimization to be justified by profiling. There is no in-game way to
capture frame-time distributions, render-thread allocation and GC together in a reproducible file, so
nothing could be justified or rejected yet. The benchmark recorder is therefore the first feature; it
is the instrument every later change is judged with.

## D-003 Frame boundary at `Minecraft#runTick` HEAD (2026-10-26)
Alternatives: Fabric HUD render callback (skipped when HUD is hidden / some screens), world render
events (skipped on menus, and their API changed across 1.21.x). `runTick` runs once per frame in all
states. A HEAD `@Inject` is additive and cannot break other mods' injections. Frame time = distance
between consecutive HEADs, so it includes swap/vsync/limiter waits — i.e. what the player sees.

## D-004 Allocation-free recording (2026-10-26)
Two preallocated `long[]` of 2^18 entries (~4 MiB). No boxing or collections per frame, so the
recorder does not create the GC pressure it measures. Overflow stops the run and is flagged.
Report writing happens on a background thread so a 250k-row CSV write never lands inside a frame.

## D-005 Client-only mod, no compile dependency on Sodium (2026-10-26)
`environment: client`. Sodium is a `suggests`, and only on the dev runtime. Any future
Sodium-specific integration must be behind a runtime presence check.

## D-007 In-game screen on V, FPS overlay, reversible low-end preset (2026-10-26)
The owner asked for a GUI on V and for "more FPS".
- **Screen** (`VortexScreen`): one-click 20/60/120 s benchmarks (the screen closes so gameplay is measured,
  not a paused menu), Stop, last 5 reports, open-folder button, overlay toggle, preset toggle.
- **Overlay** (`FrameMeter` + `VortexHud`): off by default. When off, its per-frame cost is one static boolean
  read. When on, one `nanoTime()` per frame plus two short strings drawn by the HUD.
- **FPS boost preset** (`LowEndPreset`): only vanilla settings that remove real work (render/simulation
  distance caps, clouds off, fewer particles, no entity shadows, biome blend 0, entity distance 75 %).
  It trades visuals for FPS openly and is fully reversible: the old values go to `config/vortexoptimizer.json`
  before anything changes. Mipmaps, VSync, the FPS limit and window size are left alone (a mipmap change
  reloads every resource; the rest change what is measured, not the work done).
- **Not done:** no hidden "optimization" mixins. Vortex's own overhead was already ~zero, so making the mod
  "lighter" gains no FPS. Real engine-level gains still require attributing the B-001 stalls first (Q-006).
  The preset's effect should be measured with the screen's benchmark buttons, before and after.

## D-006 License: LGPL-3.0-only (2026-10-26, owner's decision)
Chosen by the project owner over MIT. Other mods and modpacks can depend on or bundle Vortex freely, while
changes to Vortex itself must stay open (ImmediatelyFast uses the same license). `LICENSE` holds the LGPL-3.0
text and `COPYING` the GPL-3.0 text it builds on, both downloaded from gnu.org. Both are packaged into the
mod jar. `fabric.mod.json` declares the SPDX id `LGPL-3.0-only`.
Consequence: code from more restrictively licensed mods (Sodium: Polyform Shield; EntityCulling: tr7zw
Protective License) can never be copied in. Independent implementations only.
