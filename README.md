# Aegis-LUMI

High-performance, zero-overhead Bedrock Anticheat Bridge for **Lumi** (Cloudburst Nukkit 2.0).

---

## 🏗 Architecture

```
+-------------------------------------------------------------+
|                     Lumi Minecraft Server                   |
|                                                             |
|  [Player Input / Packets]   [World Collisions / Sampling]   |
|              \                           /                  |
|               v                         v                   |
|      +-----------------------------------------+            |
|      |        Aegis-LUMI Bridge Plugin         |            |
|      +-----------------------------------------+            |
|                           | (Local TCP / UDS Socket)        |
+---------------------------|---------------------------------+
                            v
+-------------------------------------------------------------+
|                  Aegis Sidecar / Core Daemon                |
|                                                             |
|  - Physics Simulation & Predictive Rollback                 |
|  - Movement (Fly, Speed, NoClip, FastLadder, Jesus)         |
|  - Combat (Reach, Hitbox, AutoClicker, Angle, Aim)          |
|  - BadPackets / Protocol Heuristics                         |
|  - Machine Learning & Statistical Anomaly Models            |
+-------------------------------------------------------------+
```

### Key Highlights
- **Zero Server Lag**: CPU-heavy physics simulations and raycasts run out-of-process in the Aegis daemon.
- **Budget Protection**: Configurable nanosecond tick budget guards both packet observation and collision sampling.
- **Asynchronous Protocol**: Non-blocking framing with sequence tracking, automatic reconnection, and batch flushing.
- **Rich Event API**: Both internal high-speed lambda `AegisEventBus` and standard Lumi `Server.getEventBus()` dispatching.

---

## 📦 Project Structure

- `bridge/`: Nukkit / Lumi plugin source code (Java 21).
  - `aegis.lumi.AegisLumiPlugin`: Main lifecycle and tick dispatch.
  - `aegis.lumi.config`: Configuration models.
  - `aegis.lumi.network`: Framing, buffer pool, stateful connection.
  - `aegis.lumi.telemetry`: Packet observer, world collision sampler, tick budget.
  - `aegis.lumi.event`: `AegisDetectionEvent`, `AegisFlagEvent`, `AegisPunishEvent`.
  - `aegis.lumi.service`: Alert broadcaster, punishment executor, verdict routing.
  - `aegis.lumi.command`: `/aegis` administration command.

---

## ⚙ Configuration (`config.yml`)

```yaml
# Connection to Aegis core sidecar
host: "127.0.0.1"
port: 19140
secret: "CHANGE_ME_IN_PRODUCTION"
reconnect-delay-ms: 2000
timeout-ms: 5000

# Telemetry & Sampling
sample-interval-ticks: 5
max-samples-per-tick: 8
input-budget-nanos: 1500000
sample-budget-nanos: 1500000

# Actions & Punishments
punishments-enabled: true
ban-duration-seconds: 0 # 0 = permanent ban
log-alerts: true
alert-cooldown-ms: 1000
alerts-enabled-by-default: true
```

---

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `aegis.command` | `op` | Allows using `/aegis` commands |
| `aegis.alerts` | `op` | Receives real-time detection notifications |
| `aegis.bypass` | `false` | Bypasses all anticheat detections and checks |

---

## 💬 Commands

- `/aegis status` - Displays bridge status, sidecar connection health, tracked player sessions, and server TPS.
- `/aegis alerts [on|off]` - Toggles real-time cheat notifications for staff.
- `/aegis reload` - Reloads configuration from `config.yml`.

---

## 🛠 Compilation & Deployment

To compile the plugin jar:
```bash
./gradlew jar
```
The output jar is located in `bridge/build/libs/Aegis-LUMI.jar` and automatically deployed to `survival/plugins/Aegis-LUMI.jar` if the server directory exists.
