# Compatibility

## Tested
| Date | Setup | Result |
|---|---|---|
| 2026-10-27 | **Nivium 0.2.0** `gradlew build` | Compiles; 24/24 JUnit tests pass |
| 2026-10-27 | **Nivium 0.2.0 dedicated dev server** (`runServer`, Fabric API 0.141.6, JDK 25, 0 players), 3 boots | Logs `Monitoring server ticks (dedicated server)`; config created on boot 1 and loaded unchanged on boots 2-3; `/nivium status/spikes/recommendations/adaptive/reload` all work from the console; `adaptive off/on` saved the config and wrote `nivium.json.bak` with the previous values; clean stop (exit 0). Two bugs found and fixed: the 60 s stats were never computed (`Long.MIN_VALUE` overflow) and view distance showed "not evaluated" while adaptation was off. **Not tested:** adaptive step-down under real load (needs players), Nivium on a real client/launcher, Java 21. |
| 2026-10-26 | `gradlew build` (JDK 25.0.1, `release 21`), MC 1.21.11, Loader 0.20.1, Fabric API 0.141.6 | Compiles; 13/13 JUnit tests pass |
| 2026-10-26 | `gradlew runClient`: Fabric API 0.141.6 + **Sodium 0.8.14+mc1.21.11** + Vortex 0.1.0, JDK 25.0.1, Windows 11, **Intel UHD Graphics** (driver 30.0.100.9864) | Boots to resource loading / title screen. Log shows `Vortex Optimizer client initialized`; no ERROR lines, no mixin apply failures. `MinecraftMixin` coexists with Sodium's `core.MinecraftMixin` on `runTick` (R-007). Ran ~6 min on the title screen, then shut down cleanly (`Stopping!`, exit code 0); no errors logged. |

**Not yet tested:** `/vortex bench` inside a world (needs in-game input), vanilla-without-Sodium
runtime, Java 21 runtime, multiplayer, Iris, ImmediatelyFast, other optimization mods.

## Known limitations
- Requires Fabric API (client commands, connection events).
- GPU utilization is not measured (no portable OpenGL API).
- Benchmark runs are cancelled on disconnect/world change by design.
- Running on JDK 25 prints `[LWJGL] Unsupported JNI version detected` (LWJGL 3.3.3 predates JDK 25). It did
  not crash in testing, but release testing should use Java 21.
- On this Intel UHD machine Minecraft reports `OpenGL Version: 3.3.0` (the requested core context), while
  Sodium's README states official support requires OpenGL 4.5+. Sodium still loaded; worth noting when
  interpreting iGPU results.

- 2026-10-26: one dev-client session closed normally (`Stopping!`, all chunks saved). After that the JVM process
  exited with `0xCFFFFFFF`, so Gradle reported `runClient FAILED`. No Java exception and no crash report were
  produced, and it happened after the game had finished shutting down. Most likely the LWJGL 3.3.3 / JDK 25
  native incompatibility above; an earlier session exited 0. Not seen on Java 21, which has not been tested yet.

## Conflicts
None known.
