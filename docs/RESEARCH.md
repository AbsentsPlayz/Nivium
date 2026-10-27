# Research notes

Each note: **source → finding → relevance → decision**. Only things actually fetched, read or
executed are recorded here. "Not yet verified" items live under *Open questions*.

## R-001 Toolchain for Minecraft 1.21.11 (2026-10-26)
- **Sources:** `meta.fabricmc.net/v2/versions/loader/1.21.11`, `meta.fabricmc.net/v2/versions/yarn/1.21.11`,
  `maven.fabricmc.net` metadata for `fabric-api` and `net.fabricmc.fabric-loom-remap`, and the
  official `FabricMC/fabric-example-mod` branch `1.21.11` (`build.gradle`, `gradle.properties`,
  `gradle-wrapper.properties`, `fabric.mod.json`).
- **Findings:**
  - Newest stable loader: `0.20.1`. Fabric API for 1.21.11: newest `0.141.6+1.21.11`.
  - The official template for 1.21.11 uses plugin id `net.fabricmc.fabric-loom-remap` (the remapping
    Loom variant for obfuscated versions), `loom.officialMojangMappings()`, Gradle `9.8.2`, Java release 21.
    Newest released `fabric-loom-remap` is `1.19.3` (template uses `1.19-SNAPSHOT`).
  - Yarn still exists for 1.21.11 (`1.21.11+build.6`), but the official template has moved to Mojang
    mappings, and 26.x is unobfuscated, so Mojang names are the forward-compatible choice.
- **Decision:** Loader 0.20.1, Fabric API 0.141.6+1.21.11, `fabric-loom-remap` **1.19.3 pinned** (not a
  SNAPSHOT, for reproducible builds), Gradle 9.8.2 wrapper, Mojang mappings. See D-001.

## R-002 Sodium for 1.21.11 (2026-10-26)
- **Sources:** Modrinth API `project/sodium/version?game_versions=["1.21.11"]&loaders=["fabric"]`;
  `github.com/CaffeineMC/sodium` README.
- **Findings:** Newest Sodium for 1.21.11: `0.8.15-beta.1` (2026-09-20); newest non-beta `0.8.14`
  (2026-08-28). Sodium is licensed **Polyform Shield 1.0.0** (not OSI open source — do not copy code
  from it). Requires OpenGL 4.5+; GL translation layers (GL4ES, ANGLE) unsupported. README gives no
  per-feature list.
- **Decision:** Test against `0.8.14` (stable) on the dev runtime only (`modLocalRuntime`). Vortex does not
  compile against Sodium. Never read Sodium source with the intent to reproduce it.

## R-003 ImmediatelyFast (2026-10-26)
- **Source:** `github.com/RaphiMC/ImmediatelyFast` README.
- **Finding:** Targets immediate-mode rendering (entities, block entities, particles, text, GUI/HUD, maps),
  batching draw calls. LGPL-3.0. Reported to complement Sodium (author benchmarks, not reproduced by us).
- **Relevance:** Entity/text/HUD draw-call batching is already a solved, maintained problem. Vortex should
  not duplicate it; any entity-rendering work must first be measured with ImmediatelyFast installed.

## R-004 EntityCulling (2026-10-26)
- **Source:** `github.com/tr7zw/EntityCulling` README.
- **Finding:** Asynchronous per-entity / block-entity line-of-sight path tracing on spare CPU threads; the
  main thread gathers data every few ticks. Sodium's own "Use Entity Culling" is chunk-section based only.
  Restrictive license (tr7zw Protective License).
- **Relevance:** Occlusion culling of entities is a real gap above Sodium but already covered by an existing
  mod. A Vortex implementation would only be justified if measurement shows EntityCulling's cost/benefit is
  poor on 2–4 core iGPU laptops (its async thread competes with chunk meshing threads).

## R-005 Minecraft 1.21.11 client API (verified with `javap` on the Mojang-mapped jar, 2026-10-26)
- `net.minecraft.client.Minecraft#runTick(boolean)` is `private void`; called once per frame.
- `Options` in 1.21.11 has **`graphicsPreset()`** (type `GraphicsPreset`) — the older `graphicsMode()` no
  longer exists. Also present: `improvedTransparency()`, `cutoutLeaves`, `chunkSectionFadeInTime()`,
  `weatherRadius`, `cloudRange`, `textureFiltering`, `inactivityFpsLimit`.
- `RenderSystem.tryGetDevice()` returns `com.mojang.blaze3d.systems.GpuDevice` with `getBackendName()`,
  `getVendor()`, `getRenderer()`, `getVersion()`. The blaze3d `GpuDevice` abstraction means vanilla's GL
  usage is behind a backend interface in this version (relevant to the Vulkan question, see Q-005).
- **Decision:** Benchmark environment capture uses only these verified members.

## R-006 Measurement methodology
- **Finding (design, from first principles):** average FPS must be `frames / total time`, not the mean of
  per-frame FPS (the latter over-weights fast frames; covered by `FrameStatsTest`). "1% low" has two
  common definitions (P99 frame time vs. average of the slowest 1 %); both are reported.
- `com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes()` gives per-thread allocation counters
  on HotSpot, letting us attribute allocation to the render thread per frame without an external profiler.
  This is the primary metric for allocation-reduction work on 8 GB machines, where GC pauses show up as
  frame spikes.

## R-007 Sodium 0.8.14 touches `Minecraft#runTick` (2026-10-26)
- **Source:** `javap -v` of `net.caffeinemc.mods.sodium.mixin.core.MinecraftMixin` in
  `sodium-mc1.21.11-0.8.14-fabric.jar` (constant pool only), resolved through Loom's
  `intermediary-v2.tiny` / `mappings.tiny`.
- **Finding:** Its annotations reference `method_1523` = `runTick(Z)V`, `method_64146` = profiler `get`,
  and `method_1521` = `reloadResourcePacks`. Sodium also has `core.FrustumMixin`, `core.WindowMixin`,
  immediate-mode `BufferBuilderMixin`/vertex consumer mixins and a `core.gui.LevelLoadTrackerMixin`.
- **Relevance:** Our frame hook shares a method with Sodium. A HEAD `@Inject` stacks with other
  injectors, so no conflict is expected; runtime check in COMPATIBILITY.md. Sodium already optimizes
  immediate-mode vertex writing (BufferBuilder) — do not duplicate.

## R-008 First in-game capture on target hardware (2026-10-26)
- **Source:** B-001 in BENCHMARKING.md (our own measurement, single smoke run).
- **Finding:** On Intel UHD / 4 cores with Sodium, frame *pacing* is the dominant problem: 26.6 % of
  wall time was spent in 25 spike frames, up to 1.44 s each, while the median frame was 11.3 ms.
  Stall frames did not show elevated render-thread allocation.
- **Relevance:** Points first at stalls (GC pauses, CPU contention with world generation / chunk
  meshing, sync waits) rather than at per-frame render cost or render-thread allocation volume.
- **Decision:** Add per-event GC timestamps to reports before touching anything (done: `GcEventLog`).
  Do not start allocation-reduction work until stalls are attributed.

## R-009 Render-thread profile, Sodium 0.8.14 + Nivium, 1.21.11 (2026-10-27)
- **Source:** our own JFR recording (`settings=profile`, 55 s, Intel UHD, RD 8), filtered to the Render thread
  (811 execution samples) plus allocation samples; analysis scripts in the session notes.
- **Where the render thread goes:** `LevelRenderer.renderLevel` 39% (FrameGraph execute 13%, Sodium terrain
  setup ~9%, **entity visibility selection `extractVisibleEntities` ~7%**), hand/held item ~10% (mostly
  `BufferSource.endBatch`, i.e. real draw submission), HUD/GUI ~7%, `ClientLevel.animateTick` ~4%.
- **Entity visibility detail:** `EntityRenderDispatcher.shouldRender` runs for *every* client entity every frame.
  Half its time is `getRenderer(entity)` (13 samples `RegularImmutableMap.get` + 11 self, which is the
  `invokedynamic typeSwitch`), and `getRenderer` is also called from `getPackedLightCoords` and `extractEntity`,
  so 3x per visible entity per frame. The other half is the distance and frustum test.
- **Rejected:** `doAnimateTick` reads the same position twice (`getBlockState`, then `getFluidState`). It's
  equivalent in vanilla, but fluidlogging mods hook `LevelChunk.getFluidState`, and the gain is ~1.5% at 20 Hz.
  Not worth the compatibility risk. Hand rendering cost is real GPU submission, not waste.
- **Allocation:** render thread ~12-16 MiB/s, no site above ~7% (top: environment-attribute iterators,
  `GlRenderPass` HashMaps). In singleplayer most GC pressure comes from integrated-server threads, so cutting
  render-thread allocation would not measurably change GC pauses. No allocation "optimization" was claimed.
- **Coverage check:** Sodium culls entities only per chunk section (`SodiumWorldRenderer.isEntityVisible` via the
  section graph); it does not test per-entity occlusion and does not touch `getRenderer`. ImmediatelyFast
  batches immediate-mode draws (HUD, text, entities) and EntityCulling does async per-entity raytracing; neither is
  in the supported stack by default, and Nivium disables its own culling when EntityCulling is present.

## Open questions (highest impact first)
- **Q-006** What causes the 0.2–1.4 s stalls in B-001? Test: re-run with the GC timeline on (1) the same
  fresh world and (2) a fully pre-generated area, at render distance 8 and 16.
- **Q-001** Where does render-thread allocation come from in Sodium + vanilla 1.21.11 at 8–12 chunks on an
  Intel iGPU? Measure with `/vortex bench` (alloc MiB/s) and a JFR allocation profile before writing any
  allocation-reduction mixin.
- **Q-002** What does Sodium 0.8.14 already do for particles, block entities, and entity culling? Inspect its
  mixin config (names/targets only) and options UI to avoid duplicate work.
- **Q-003** Is frame pacing on Intel iGPUs dominated by CPU (draw submission, chunk upload) or GPU fill rate at
  typical settings? Determines whether CPU-side work can help at all.
- **Q-004** *Partly answered (R-007).* Sodium does target `runTick`; remaining question is the exact
  injection kinds (does it wrap anything at HEAD that would shift our boundary?).
- **Q-005** Vulkan: 1.21.11's blaze3d `GpuDevice` abstraction — is it a viable backend seam, and what do the
  existing Vulkan efforts (e.g. VulkanMod) show about driver coverage on Intel iGPUs? Not yet researched.
