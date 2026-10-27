# Roadmap

## Done
- [x] Vortex Optimizer 0.1.0: client benchmark tool, FPS overlay, client video preset (history: D-001 to D-007)
- [x] **Nivium 0.2.0** (D-008): server-side continuous tick monitor, spike attribution (GC / save / tick work),
      adaptive simulation distance with hysteresis, recommendations, `/nivium` commands, config with backup
- [x] 24 JUnit tests (statistics, ring buffer, spike attribution, adaptive controller rules, config safety)
- [x] Dedicated dev server boot verified: init, config create/load/backup, all commands, clean stop

## Next (priority order)
1. **Measure adaptive simulation distance under real load** on a server with players (farms, mobs). Log MSPT
   before and after each step. Keep the feature only if the data shows a reduction.
2. **Per-dimension tick cost** (`START/END_WORLD_TICK`): show which dimension is expensive, still with no world traversal.
3. **Autosave spike mitigation**: research only. Check what vanilla 1.21.11 and Lithium/C2ME already do before
   touching saving.
4. Rename the GitHub repository to `nivium` and update `fabric.mod.json` contact URLs.
5. Java 21 runtime test (dev runs used JDK 25).

## Risks
- Adaptive distance changes gameplay near the edge (farms beyond the reduced radius pause). That's why it is
  slow, bounded, logged and reversible, with a single config switch to turn it off.
- Integrated servers overwrite distances from the host's options every tick, so adaptation is off there by default.
