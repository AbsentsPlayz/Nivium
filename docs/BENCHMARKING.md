# Benchmarking

## Tool
`/vortex bench start [seconds=60] [warmupFrames=300] [label]`, `/vortex bench status`, `/vortex bench stop`.
Output: `<gameDir>/vortex-benchmarks/<UTC stamp>-<label>.json` (summary + environment) and `.csv`
(per-frame `frame_ms`, `render_thread_alloc_bytes`).

Reported: average FPS (frames / time), 1 % low (avg of slowest 1 % **and** 1000/P99), 0.1 % low,
p50/p95/p99/p99.9/max frame time, std-dev, spike count (frames > max(2×median, 8 ms)), process CPU
utilization, render-thread allocation (bytes/frame, MiB/s), GC count/time per collector, heap
start/end/peak, all video settings, window size, GPU vendor/renderer/driver string, JVM args, mod list.
Not measured: GPU utilization (no portable API in OpenGL core) — use Task Manager or PresentMon
alongside and record it manually. Lows are flagged unreliable below 10 000 frames.

## Procedure (A/B comparison)
Configurations, each a separate game instance with identical options.txt:
1. Vanilla (Fabric API only, Vortex installed for the recorder — the recorder's idle cost is one null check)
2. Sodium 0.8.14
3. Sodium + Vortex (all features off)
4. Sodium + Vortex, one experimental feature on at a time

Controls:
- Same world copy (keep a pristine zip; restore before each cold run), same seed, same spawn point.
- Fixed resolution and window mode, render distance, simulation distance, framerate limit = unlimited,
  vsync off, same resource packs, same JVM args (`-Xmx` especially).
- Close background apps; laptop on AC power, same power plan.
- Camera: stand still at a fixed position/rotation (teleport with `/tp @s x y z yaw pitch`) using
  `/gamerule doDaylightCycle false`, `/time set 6000`, `/weather clear`. A scripted flight path is a
  future addition (ROADMAP).
- **Cold** run: first run after launching the game and loading the world (warm-up 0, label `cold`).
  **Warm** runs: 3+ further runs with default 300-frame warm-up.
- Report mean ± std-dev of average FPS and 1 % low across warm runs; never a single run.

## Recorded results

### B-001 Smoke test — `run/vortex-benchmarks/20261026-143422-test.json` (2026-10-26)
**Purpose:** prove the tool works end-to-end. **Not a valid baseline**: freshly generated world,
terrain still generating, a single 20 s run, settings not the controlled set.

Setup: Sodium 0.8.14 + Fabric API + Vortex 0.1.0, JDK 25.0.1, Intel UHD Graphics (driver
30.0.100.9864), 4 logical cores, heap max 1.94 GiB (default, no `-Xmx`), 1920×974 windowed,
render distance 16, simulation distance 12, FPS limit 260 (= unlimited), vsync off, graphics FANCY.

| Metric | Value |
|---|---|
| Frames / time | 1288 in 20.0 s |
| Average FPS | 64.4 |
| Median frame | 11.33 ms (≈ 88 FPS) |
| P95 / P99 frame | 17.2 / 33.6 ms |
| 1 % low (avg) | 2.6 FPS — *unreliable, < 10 000 frames* |
| Max frame | 1444 ms |
| Spikes (> 22.7 ms) | 25 frames, **5.3 s of the 20 s run (26.6 %)** |
| Process CPU | 36 % of 4 cores |
| Render-thread allocation | 29.3 MiB/s, median 495 KiB/frame, P99 1.5 MiB/frame |
| GC | 4 young (40 ms total), 2 "G1 Concurrent GC" (763 ms total), 0 full |

**Analysis (from the CSV):**
- The run's average is dominated by 9 stalls of 240–1444 ms (t = 3.8–13.4 s). Between stalls the game ran at
  ~88 FPS median. The stutter, not the average, is the problem.
- The stall frames had *normal* render-thread allocation (490–630 KiB). The render thread was not allocating
  heavily when it stalled. Allocation rose only in the 1–3 frames *after* each stall (1.5–3 MiB, catch-up work).
- The integrated server logged `Can't keep up!` (2–7 s behind) repeatedly before and after the window: world
  generation at render distance 16 was saturating this 4-core CPU.
- Candidate causes, not yet distinguished: (a) G1 pauses — 763 ms of "G1 Concurrent GC" time is in the same
  range as the stalls, but that total alone can't show when it happened, or whether it was pause time at all;
  (b) render thread waiting on chunk work / the server thread for CPU; (c) driver/GPU sync or OS paging.
- **Instrument change made because of this run:** reports now include a per-event GC timeline placed on the
  frame timeline (`gcEvents`, `spikeFramesWithGcEvent`). The next run will show directly whether the stalls
  line up with GC pauses.

### B-002 First run with GC timeline — `run/vortex-benchmarks/20261026-151132-normal-rd16.json` (2026-10-26)
Same machine and mods as B-001, same (still young) world. Started from the V menu ("Normal 60s"),
render distance 16, simulation distance 12. Integrated server logged `Can't keep up!` 8.7–35 s behind
just before and during the run.

| Metric | Value |
|---|---|
| Frames / time | 2635 in 60.0 s |
| Average FPS | 43.9 (median frame 11.96 ms ≈ 84 FPS) |
| P99 / max frame | 101 ms / **8183 ms** |
| Spikes | 77, of which **2 contain a GC event** |
| Process CPU | 24 % of 4 cores |
| Render-thread allocation | 7.9 MiB/s |
| GC | 6 young (2394 ms total), 2 concurrent, 0 full |

GC timeline (per event):
- **8.18 s freeze** at ~t=12 s contains three young GCs: 5 ms, **1062 ms**, 7 ms. So about 7.1 s of the
  freeze was *not* GC.
- **1.43 s freeze** at ~t=20 s contains a **1183 ms young GC**. This one is mostly GC.
- The 755 ms "G1 Concurrent GC" event fell in a normal 13 ms frame. **This confirms concurrent-cycle time is
  not a pause**, so B-001's 763 ms concurrent total was not stall time.

Interpretation:
1. Most of the stall time is not GC. The 75 spikes without a GC event, and 7 s of the 8 s freeze, have
   another cause.
2. A young GC normally takes tens of ms; 1.0–1.2 s pauses are pathological. Combined with **low CPU use (24 %)
   while the server ran up to 35 s behind**, this points at threads *waiting* rather than computing:
   most likely OS memory paging (8 GB RAM, ~2 GB game heap, plus a Gradle daemon in this dev setup) or disk
   I/O. CPU saturation alone would show higher CPU use. **Hypothesis, not yet verified.**
3. Next instrument: record system physical memory (total/free, sampled) and process resident memory in reports,
   to confirm or rule out paging.

The FPS-boost preset was applied right after this run (log 21:12:48), but no "after" run was recorded.
