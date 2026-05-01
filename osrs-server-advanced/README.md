# OSRS Private Server — RSProt 237 + RSProx

A minimal-but-correct OSRS private server network layer built for revision **237**,
using **RSProt** (by blurite) as the protocol library and supporting **RSProx**
for packet inspection/modification.

---

## Project Structure

```
osrs-server/
├── build.gradle.kts                     # Kotlin/Gradle build, RSProt dep
├── src/main/kotlin/com/osrs/server/
│   ├── ServerMain.kt                    # Entry point, opcode validation
│   ├── game/
│   │   ├── entity/Player.kt             # Player entity
│   │   └── world/World.kt               # World + 600ms game loop
│   ├── login/
│   │   └── LoginRequest.kt              # Login data class
│   └── network/
│       ├── codec/
│       │   ├── OpcodeTable.kt           # ★ ServerProt + ClientProt aligned to rev 237
│       │   ├── GamePacketCodec.kt       # Encoder / decoder (ISAAC-aware)
│       │   └── LoginDecoder.kt          # OSRS rev 237 login handshake
│       ├── handlers/
│       │   └── GameChannelHandler.kt    # Netty pipeline + login→game upgrade
│       └── session/
│           └── PlayerSession.kt         # Per-player packet write helpers + dispatch
└── rsprox/
    ├── rsprox.properties                # RSProx config (point at this server)
    └── OPCODE_ALIGNMENT.md             # Full opcode mapping + collision notes
```

---

## Prerequisites

- **Java 21+**
- **Gradle 8.7** (wrapper included)
- RSProt 237 on JitPack (fetched automatically by Gradle)

---

## Build & Run

```bash
# Clone and enter project
cd osrs-server

# Build fat jar
./gradlew jar

# Run directly
./gradlew run

# Or run the fat jar
java -jar build/libs/osrs-server-1.0.0.jar
```

Server starts on `0.0.0.0:43594` (standard OSRS port).

---

## RSProx Integration

RSProx (https://github.com/blurite/rsprox) acts as a proxy between the OSRS
client and your server, decoding and logging all packets using RSProt's codec.

### Setup

1. **Start this server** on port 43594
2. **Configure RSProx** using `rsprox/rsprox.properties`:
   ```
   server.host=127.0.0.1
   server.port=43594
   proxy.port=40000
   revision=237
   ```
3. **Start RSProx**:
   ```bash
   java -jar rsprox.jar --config rsprox/rsprox.properties
   ```
4. **Point the OSRS client** at `127.0.0.1:40000` (RSProx listen port)
5. **Set proxy mode** in `GameChannelInitializer`:
   ```kotlin
   proxyMode = true   // skips ISAAC decode; RSProx already decoded it
   ```

### What RSProx does
- Intercepts every packet between client and server
- Decodes packet names/fields using RSProt's definitions
- Logs to file + console
- Allows packet modification/injection via plugins

---

## Opcode Alignment

The file `src/main/kotlin/.../network/codec/OpcodeTable.kt` contains:
- `ServerProt` enum — all server→client opcodes for rev 237
- `ClientProt` enum — all client→server opcodes for rev 237

On startup, `validateOpcodeTables()` checks for duplicate opcode assignments
and logs any conflicts. **All conflicts must be resolved against RSProt's source**
before the server will correctly communicate with the client.

### Verifying against RSProt source

```bash
git clone https://github.com/blurite/rsprot
cd rsprot
# Find the rev237 tag
git tag | grep 237
git checkout <tag>

# View server opcodes
cat protocol/src/main/kotlin/net/rsprot/protocol/game/outgoing/GameServerProt.kt

# View client opcodes  
cat protocol/src/main/kotlin/net/rsprot/protocol/game/incoming/GameClientProt.kt
```

Compare each opcode number to `OpcodeTable.kt` and update any mismatches.

### Known potential conflicts (see `rsprox/OPCODE_ALIGNMENT.md`)
- `CLIENTSCRIPT` vs `REBUILD_REGION` (both opcode 3 in initial table)
- `OBJ_DEL` vs `NPC_INFO_LARGE_VIEWPORT` (both opcode 32)
- `CAM_SMOOTHRESET` vs `VARP_SMALL` (both opcode 62)

These may be rev-specific — verify against the rev237 RSProt tag.

---

## Extending the Server

| Task | File |
|------|------|
| Add a new packet handler | `PlayerSession.kt` → `handlePacket()` |
| Send a new packet type | Add to `ServerProt` + add write helper in `PlayerSession` |
| Add NPC spawning | `World.kt` + new `NpcEntity` class |
| Add pathfinding | `Player.walkTo()` in `Player.kt` |
| Add a cache/JS5 server | New `Js5ChannelInitializer` in `network/handlers/` |
| Add RSA key pair | Generate with `openssl`, pass to `LoginDecoder` |

---

## License

This project is a private development scaffold. RSProt is © blurite under its
own license. OSRS game content is © Jagex Ltd.
