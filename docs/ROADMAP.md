# Roadmap

## Done
- [x] Vortex Optimizer 0.1.0: client benchmark tool, FPS overlay, client video preset (D-001 to D-007)
- [x] Nivium server edition (D-008), then superseded by the player edition (D-009); kept in git history
- [x] **Nivium 0.2.0 player edition:** lag check (PC / server TPS / ping), freeze detection with GC labels,
      recommendations, FPS boost, overlay with TPS and ping, K screen
- [x] 22 JUnit tests (TPS estimator, lag verdict, recommendations, freeze attribution, GC log)
- [x] Mixin targets verified in the remapped jar (`handleSetTime` = `method_11079`, `runTick` = `method_1523`)

## Next (priority order)
1. **In-game test on a real multiplayer server** by the owner: check the TPS estimate against a server's
   own TPS readout, check freeze labels, and check FPS boost before and after.
2. **Measure the FPS boost** with before/after numbers on the target laptop and put them in the Modrinth description.
3. **Real engine-level FPS work** only after profiling (see Q-001/Q-006). Candidates must not duplicate Sodium.
4. Java 21 runtime test (dev runs used JDK 25).
