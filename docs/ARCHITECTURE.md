# Architecture (Nivium 0.2.0, player version)

Client-only (`environment: "client"`). Nothing runs on servers; works on any server.

## Modules
| Source set | Package | Contents | Minecraft types? |
|---|---|---|---|
| `main` | `io.github.nivium` | `Nivium` (constants, logger) | no |
| `main` | `...stats` | `GcEventLog`, `StutterLog`, `JvmProbe`, `UptimeClock` | no |
| `main` | `...lag` | `ServerTpsEstimator`, `LagVerdict` | no |
| `main` | `...advice` | `ClientAdvice` | no |
| `client` | `io.github.nivium.client` | `NiviumClient` (entrypoint), `NiviumClientConfig` | yes |
| `client` | `...client.lag` | `LagMonitor` (glue: FPS + TPS + ping + freezes) | yes |
| `client` | `...client.hud` | `FrameMeter` (per-frame timing, freeze detection), `NiviumHud` (overlay) | yes |
| `client` | `...client.gui` | `NiviumScreen` (K) | yes |
| `client` | `...client.perf` | `LowEndPreset` (FPS boost) | yes |
| `client` | `...client.mixin` | `MinecraftMixin` (`runTick` HEAD), `ClientPacketListenerMixin` (`handleSetTime` TAIL) | yes |

All decision logic sits in `main` with no Minecraft types and is unit-tested.

## Data flow (render thread)
```
Minecraft.runTick HEAD   -> FrameMeter: frame time, 0.5 s FPS window, frame > 100 ms while playing -> StutterLog
handleSetTime TAIL       -> ServerTpsEstimator.onTimeUpdate(nanoTime, gameTime)   (every 20 server ticks)
JMX GC notifications     -> GcEventLog (synchronized ring, JMX thread)
JOIN / DISCONNECT        -> estimator reset
K screen / overlay       -> LagMonitor: LagVerdict(FPS, freezes, GC freezes, TPS, silence, ping), ClientAdvice
```
Per-frame cost: one `nanoTime()` and a few comparisons. The TAIL injection on `handleSetTime` runs only on
the render thread, because the network-thread call exits early to reschedule itself.
