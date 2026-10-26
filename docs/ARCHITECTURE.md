# Architecture

## Modules
| Source set | Package | Contents | Depends on Minecraft? |
|---|---|---|---|
| `main` | `io.github.vortexoptimizer` | `VortexOptimizer` (constants, logger) | no |
| `main` | `io.github.vortexoptimizer.bench` | `FrameRecorder`, `FrameStats`, `BenchmarkSession`, `BenchmarkResult`, `JvmProbe` | no (Gson only) |
| `client` | `io.github.vortexoptimizer.client` | `VortexOptimizerClient` (entrypoint, `/vortex` commands) | yes |
| `client` | `...client.bench` | `BenchmarkController`, `EnvironmentCollector` | yes |
| `client` | `...client.mixin` | `MinecraftMixin` | yes |
| `client` | `...client.gui` | `VortexScreen` (key V) | yes |
| `client` | `...client.hud` | `FrameMeter`, `VortexHud` (Fabric `HudElementRegistry`) | yes |
| `client` | `...client.perf` | `LowEndPreset` | yes |
| `client` | `...client` | `VortexConfig` (`config/vortexoptimizer.json`) | yes |
| `main` | `...bench` | `GcEventLog` (per-GC timeline), `ReportIndex` (reads saved reports) | no |

Pure logic lives in `main` and is unit-tested with JUnit 5 without launching the game.

## Benchmark execution flow
```
/vortex bench start [seconds] [warmupFrames] [label]
  -> BenchmarkController.start(): capture EnvironmentCollector snapshot, create BenchmarkSession
every frame (render thread):
  Minecraft.runTick HEAD -> MinecraftMixin -> BenchmarkController.onFrameStart()
     -> session.onFrameBoundary(System.nanoTime())
        - first call: baseline; warm-up frames discarded
        - then: record (frameNanos, renderThreadAllocatedBytes) into preallocated arrays
        - heap sampled every 250 ms
        - auto-finish on duration or buffer overflow
finish:
  -> GC / CPU / heap deltas computed, second settings snapshot compared with the first
  -> summary to chat + log
  -> JSON + CSV written to <gameDir>/vortex-benchmarks/ on a ForkJoinPool thread
disconnect -> session cancelled (no report)
```
Idle cost per frame: one static field read and a null check.

## Threading
- All controller/session state: render thread only (commands execute on the client thread).
- File I/O: `CompletableFuture.supplyAsync` (common pool); completion message re-posted with
  `Minecraft.execute`.

## Integration points
| Point | Mechanism | Why |
|---|---|---|
| `Minecraft#runTick(boolean)` | `@Inject HEAD` | Per-frame boundary (D-003) |
| Client commands | Fabric API `ClientCommandRegistrationCallback` | Supported API, no mixin |
| Disconnect | Fabric API `ClientPlayConnectionEvents.DISCONNECT` | Supported API |
| Key V | Fabric `KeyBindingHelper` + `KeyMapping.Category.register` (1.21.9+ category API) | Rebindable in Controls |
| HUD | Fabric `HudElementRegistry.addLast` | Drawn after vanilla HUD, hidden with F1 / F3 |
