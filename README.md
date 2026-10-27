# Nivium

**Advanced Minecraft Server Optimization** for Minecraft Java **1.21.11** (Fabric).

Nivium runs on the server. Players do not need to install it. It watches every server tick at
negligible cost, explains lag spikes, and steps the live simulation distance down under *sustained*
overload, then puts your value back when load drops.

## What it does
- **Continuous tick monitor:** TPS and MSPT (mean / p95 / max over 5 s and 60 s), slow-tick
  count, heap and GC, loaded chunks. Rolling windows only: no world scans, no entity enumeration on a timer.
- **Spike attribution:** each slow tick is labeled as a GC pause, a world save, or tick work.
- **Adaptive simulation distance** (on by default, dedicated servers only): down 1 step after mean MSPT
  >= 45 ms for 30 s, back up after <= 30 ms for 120 s, a 60 s cooldown, never below 6 or above your
  server.properties value. Changes are live only and logged, and a restart always restores your values.
  Adaptive view distance is available but off by default.
- **Recommendations** (never applied automatically): missing Lithium, a small heap, low system RAM,
  high distances in server.properties.
- **Compatibility:** if ServerCore is installed, adaptive distances stay off (it has its own).

## Commands (operators, permission level 2)
`/nivium status` · `/nivium spikes` · `/nivium recommendations` · `/nivium adaptive [on|off]` · `/nivium reload`

## Config
`config/nivium.json`, created on first start. Every setting applies live with `/nivium reload`; nothing
needs a restart. Before Nivium writes the file it copies the previous version to `nivium.json.bak`, and
it never rewrites a file it cannot parse.

## Optional client extras
When installed on a client: press **K** for the Nivium screen: live server stats in singleplayer, buttons
for Recommendations / Adaptive ON / Adaptive OFF / Reload (run directly in singleplayer; sent as the real
`/nivium` command on a remote server, so operator permission still applies), an FPS overlay, and a
reversible client video preset. These are client-side conveniences. Installing
Nivium on a server does not raise anyone's FPS.

## Build
JDK 21+ (`JAVA_HOME`), then `gradlew build` → `build/libs/nivium-0.2.0.jar`.

## License
[LGPL-3.0-only](LICENSE) (GPL-3.0 text in [COPYING](COPYING)).

Source: https://github.com/AbsentsPlayz/nivium · Issues: https://github.com/AbsentsPlayz/nivium/issues

Nivium grew out of the earlier Vortex Optimizer client mod; that history is kept in this repository's commits.
