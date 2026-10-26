# Design decisions

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
