# Roadmap

## Done
- [x] Project scaffold for MC 1.21.11 / Loader 0.20.1 / Fabric API 0.141.6 / Loom 1.19.3 / Gradle 9.8.2 / Mojang mappings
- [x] Benchmark recorder (`/vortex bench`) with JSON+CSV reports, render-thread allocation, GC, CPU, environment capture
- [x] Per-GC-event timeline in reports (spike attribution)
- [x] In-game screen (V), FPS overlay, reversible FPS-boost preset (D-007)
- [x] 20 JUnit tests

## Next (in priority order)
1. **Baseline capture on target hardware** (Intel iGPU, 8 GB): vanilla vs Sodium, cold + 3 warm runs at
   8 and 12 chunks. Blocked on running the real game on that machine (needs a human at the keyboard or a
   scripted camera path).
2. **Scripted camera path** (`/vortex bench path ...`): deterministic flight across loaded chunks so runs
   are comparable without manual input; also exercises chunk rebuild scheduling.
3. **Chunk-load timing**: time from world join until all sections within render distance are built
   (needs a Sodium-aware hook — investigate Q-002 first).
4. **Allocation hotspot survey (Q-001)**: JFR `jdk.ObjectAllocationSample` on the render thread with Sodium,
   rank allocation sites, pick the top site that Sodium/ImmediatelyFast do not already address.
5. **Sodium surface inspection (Q-002, Q-004)**: list Sodium 0.8.14 mixin targets to map what it replaces.
6. **Vulkan feasibility note (Q-005)**: research only; no code.

## Risks
- Sodium 0.8.x internals change between betas; any Sodium-touching mixin must be guarded by version checks.
- Gradle build is run with JDK 25 (`options.release = 21`); no standalone JDK 21 on the dev machine yet,
  so the dev client also runs on 25. Release testing must use Java 21 (the version the vanilla launcher ships).
- No Git on the dev machine: changes are not versioned yet.
