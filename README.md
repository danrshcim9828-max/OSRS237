# OSRS237 Repository

Primary framework implementation is in:

- `osrs-server-advanced/`

This variant is configured around RSProt revision 237 guidance and includes cross-platform build scripts for Windows and Linux/macOS with JDK 21 + Gradle 8.7+.

## Quick Start

```bash
cd osrs-server-advanced
gradle clean build
gradle run
```

For Windows PowerShell, use:

```powershell
cd osrs-server-advanced
./scripts/build-windows.ps1
```
