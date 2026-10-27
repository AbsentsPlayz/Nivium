# Modrinth listing text (Nivium 0.2.0)

**Summary** (plain text field):

> Advanced Minecraft Server Optimization: live TPS/MSPT monitoring, lag-spike explanations and adaptive simulation distance. Server-side only.

**Description** (body, Markdown):

---

## Nivium: Advanced Minecraft Server Optimization

Nivium is a **server-side** Fabric mod that keeps an eye on every server tick, tells you *why* lag spikes
happen, and gently lowers the live simulation distance when your server is genuinely overloaded,
then puts it back when load drops. **Players don't need to install anything.**

### Features
- **Live performance monitor:** TPS, tick time (MSPT mean / p95 / max over 5 s and 60 s), slow ticks, memory,
  GC and loaded chunks. Uses rolling measurements only: no world scans, nothing that walks every entity.
- **Lag-spike explanations:** each slow tick is labeled *GC pause*, *world save* or *tick work*, so you know
  whether to look at RAM, saving, or what's happening in the world.
- **Adaptive simulation distance:** when the server stays overloaded (mean tick time ≥ 45 ms for 30 s), Nivium
  steps the simulation distance down by 1, at most once a minute, never below 6. When load stays low for
  2 minutes, it steps back up to your own value. Every change is logged. server.properties is never edited,
  and a restart always restores your settings.
- **Recommendations:** suggests things like installing Lithium, more heap (-Xmx), or lower distances. They're
  only suggestions and are never applied automatically.
- **Safe config:** `config/nivium.json`, applied live with `/nivium reload` (no restart). Your file is backed up
  before Nivium ever writes to it.

### Commands (operators)
`/nivium status` · `/nivium spikes` · `/nivium recommendations` · `/nivium adaptive on|off` · `/nivium reload`

### Optional client extras
Installed on a client, Nivium also adds a **K** screen (live server stats in singleplayer, plus buttons for
Recommendations, Adaptive ON/OFF and Reload), an FPS overlay and a
reversible video preset. These are conveniences: installing Nivium on a server does not raise players' FPS.

### Compatibility
- Works alongside **Lithium** (recommended) and doesn't duplicate its optimizations.
- If **ServerCore** is installed, Nivium's adaptive distances switch off automatically (ServerCore has its own).

### Requirements
Minecraft **1.21.11** · Fabric Loader **0.17.3+** · **Fabric API** (required on the server)

### License
LGPL-3.0-only · Source code: https://github.com/AbsentsPlayz/nivium

---

**Upload settings:** Loader *Fabric* · Game version *1.21.11* · Environment **Server: required, Client:
optional** · Channel **Beta** · Dependencies: *Fabric API, required*; *Lithium, optional* · License
*LGPL-3.0-only* · Categories: *Optimization, Utility*.
