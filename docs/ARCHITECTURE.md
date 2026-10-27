# Architecture (Nivium 0.2.0)

## Modules
| Source set | Package | Contents | Minecraft types? |
|---|---|---|---|
| `main` | `io.github.nivium` | `Nivium` (ModInitializer: wires events, owns state) | yes |
| `main` | `...stats` | `TimingStats`, `GcEventLog`, `JvmProbe` | no |
| `main` | `...monitor` | `RollingTimes`, `SpikeLog` (pure); `TickMonitor`, `PerformanceSnapshot` | partly |
| `main` | `...optimize` | `AdaptiveController` (pure decision logic); `AdaptiveDistances`, `Recommendations` | partly |
| `main` | `...config` | `NiviumConfig` (load / upgrade with backup / validate) | no |
| `main` | `...command` | `NiviumCommands` (`/nivium ...`) | yes |
| `main` | `...compat` | `ModCompat` (Lithium, ServerCore, FerriteCore, C2ME detection) | no |
| `client` | `io.github.nivium.client...` | `NiviumClient`, `NiviumScreen` (K), `NiviumHud`, `FrameMeter`, `LowEndPreset`, `NiviumClientConfig`, `MinecraftMixin` | yes |

`fabric.mod.json`: `environment: "*"`. The main entrypoint runs on dedicated and integrated servers; the
client entrypoint and its mixin load only on clients. Players connecting to a Nivium server need nothing.

## Server execution flow (all on the server thread)
```
SERVER_STARTING  -> GC listener on, new TickMonitor + AdaptiveDistances
START_SERVER_TICK-> TickMonitor.onTickStart: nanoTime, interval -> ring (TPS)
BEFORE_SAVE      -> mark this tick as saving (spike attribution)
END_SERVER_TICK  -> duration -> ring; > threshold => SpikeLog
   every 20 ticks:
     publish PerformanceSnapshot (5 s stats each second; 60 s stats every 5 s, every 15 s when overloaded)
     AdaptiveDistances.evaluate(mean MSPT over 10 s)   (skipped during /tick sprint or freeze)
     rate-limited spike summary log; optional status log
SERVER_STOPPING  -> restore every reduced distance, GC listener off
```
Per-tick cost: two `nanoTime()` calls, two array writes, one comparison.
Per-second cost: a 200-element mean, a 100-element sort, a loop over dimensions reading a counter.

## Threading
- Every Minecraft object is read on the server thread only.
- `GcEventLog` is filled by a JMX notification thread (synchronized ring).
- `PerformanceSnapshot` is immutable and published through a volatile field, which is how the singleplayer
  client screen reads it from the render thread.

## Integration points
| Point | Mechanism |
|---|---|
| Tick timing | Fabric `ServerTickEvents.START/END_SERVER_TICK` (no mixin) |
| Save attribution | Fabric `ServerLifecycleEvents.BEFORE_SAVE` |
| Start/stop | Fabric `ServerLifecycleEvents.SERVER_STARTING/STOPPING` |
| Distances | vanilla `PlayerList.get/setSimulationDistance`, `get/setViewDistance` (live, not persisted) |
| Commands | Fabric `CommandRegistrationCallback`, `Commands.hasPermission(LEVEL_GAMEMASTERS)` (1.21.11 API) |
| Client key K / HUD / frame hook | Fabric `KeyBindingHelper`, `HudElementRegistry`; `Minecraft#runTick` HEAD inject |
