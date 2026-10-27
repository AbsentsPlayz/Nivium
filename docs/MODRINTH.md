# Modrinth listing text (Nivium 0.2.0, player version)

**Summary** (plain text field):

> Smoother Minecraft on any server: one-click FPS boost, FPS/TPS/ping overlay, and a lag check that tells you if lag is your PC, the server or your connection.

**Description** (body, Markdown):

---

## Nivium: smoother Minecraft, on any server

Lagging on a server and not sure why? Nivium tells you whether it's **your PC**, **the server**, or **your
connection**, and gives your FPS a boost with one click.

**Install it on your own PC. It works on any server**, and the server doesn't need it.

### 🚀 Engine optimizations
- **Entity occlusion culling:** mobs, items and other entities completely hidden behind solid blocks are skipped
  entirely: not processed, not drawn. Great in villages, farms, caves and bases.
  In our test with 180 mobs behind a wall: **+41% average FPS** and far fewer memory-cleanup pauses.
  Safe: players, named and glowing mobs, item frames and holograms are never hidden.
  Works with Sodium, and steps aside automatically if you use EntityCulling.

### 🔍 Lag check (press **K**)
- **Your PC:** FPS, frame time and freezes
- **Server:** live TPS, estimated from the server's own time updates (works on any server)
- **Connection:** your ping
- A clear verdict: *"Your PC is the bottleneck"*, *"Server is lagging (12 TPS)"*, *"High ping"*, or *"Everything looks smooth"*

### 🧊 Freeze detection
Every freeze over 100 ms is recorded and labeled. If it was Java's **memory cleanup (GC)**, Nivium tells you,
and how to fix it (usually by freeing up RAM).

### ⚡ FPS boost
One click lowers the settings that cost the most FPS:
- Render distance ≤ 8, simulation distance ≤ 6
- Clouds off, fewer particles, no entity shadows
- Biome blend off, entity distance 75%

**Restore my video settings** puts back your exact settings, even after a restart.

### 📊 Overlay
FPS, frame time and your worst frame, plus server TPS and ping, in the corner of the screen.

### 💡 Recommendations
Tips for your setup: free RAM, launcher memory (-Xmx), render distance, Sodium. Advice only; nothing is changed automatically.

### ℹ️ Good to know
- Nivium makes **your** game smoother. It can't make someone else's server faster or lower your ping, but it
  shows you exactly which one is the problem.
- For the biggest FPS gain, use it with **Sodium**.
- Very lightweight: one timer read per frame.

### 📋 Requirements
Minecraft **1.21.11** · Fabric Loader **0.17.3+** · **Fabric API**

### 📜 License
LGPL-3.0-only · Source code: https://github.com/AbsentsPlayz/nivium

---

**Upload settings:** Loader *Fabric* · Game version *1.21.11* · Environment **Client: required, Server:
unsupported** · Channel **Beta** · Dependencies: *Fabric API, required*; *Sodium, optional* · License
*LGPL-3.0-only* · Categories: *Optimization, Utility*.
