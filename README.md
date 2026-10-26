# Vortex Optimizer

Measurement-driven client performance mod for Minecraft Java **1.21.11** (Fabric), aimed at low-end PCs
(Intel iGPU, 8 GB RAM) and designed to sit alongside Sodium rather than replace it.

Current state (0.1.0): the benchmark recorder — the instrument every future optimization is judged by.
No gameplay or rendering behaviour is changed yet.

Press **V** in game for the Vortex menu: one-click benchmarks, recent results, an FPS overlay and a
reversible "FPS boost" preset of vanilla settings. The same benchmark is available as commands:

```
/vortex bench start [seconds] [warmupFrames] [label]
/vortex bench status
/vortex bench stop
```
Reports go to `.minecraft/vortex-benchmarks/` (JSON summary + per-frame CSV). See `docs/BENCHMARKING.md`.

## Build
Requires a JDK 21+ (`JAVA_HOME`). `gradlew build` → `build/libs/vortex-optimizer-0.1.0.jar`.
`gradlew runClient` starts a dev client with Fabric API and Sodium 0.8.14.

## License
[LGPL-3.0-only](LICENSE). The LGPL is a set of additional permissions on top of the GNU GPL v3,
whose text is in [COPYING](COPYING). See `docs/DECISIONS.md` D-006.
