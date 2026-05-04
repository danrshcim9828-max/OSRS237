# OSRS Private Server — RSProt 237 Framework (JDK 21 / Gradle 8.7+)

This is the **active RSProt 237 server framework** in this repository.
It is structured for cross-platform builds and operation on Windows/Linux/macOS using **JDK 21** and **Gradle 8.7+**.

## Key Targets

- Revision: **237**
- Protocol alignment source: `rsprox/OPCODE_ALIGNMENT.md`
- Runtime: **Java 21**
- Build system: **Gradle Kotlin DSL**
- Network stack: **Netty**

## Project Layout

- `src/main/kotlin/com/osrs/server` — server framework code.
- `src/main/resources/config.yaml` — runtime server settings.
- `rsprox/OPCODE_ALIGNMENT.md` — opcode cross-reference notes for rev 237.
- `scripts/build-windows.ps1` — Windows build entrypoint.
- `scripts/build-linux.sh` — Linux/macOS build entrypoint.

## Windows Build (PowerShell)

```powershell
cd osrs-server-advanced
./scripts/build-windows.ps1
```

Optional task:

```powershell
./scripts/build-windows.ps1 -Task test
```

## Linux/macOS Build

```bash
cd osrs-server-advanced
./scripts/build-linux.sh
```

Optional task:

```bash
./scripts/build-linux.sh test
```

## Direct Gradle Commands

```bash
cd osrs-server-advanced
gradle clean build
gradle run
```

## JDK 21 Notes

This project enforces Java toolchain 21 in `build.gradle.kts`.
If your system default Java is not 21, configure `JAVA_HOME` to a JDK 21 install.

## RSProt 237 Alignment Workflow

1. Use opcode expectations documented in `rsprox/OPCODE_ALIGNMENT.md`.
2. Keep `network/codec/OpcodeTable.kt` synchronized to the rev 237 protocol source.
3. Verify login and game packet flow through RSProx before feature expansion.
