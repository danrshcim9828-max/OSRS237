# OSRS Private Server

This repository now contains a clean OSRS private server implementation based on the deep research guide.
The active project is located in `osrs-server/`.

## Build & run

```bash
cd osrs-server
./gradlew run
```

Use `-Dosrs.config.path=/path/to/config.yaml` or `OSRS_CONFIG_PATH=/path/to/config.yaml` to override the default config.

## Legacy backup

The previous implementation has been preserved in `osrs-server-advanced/` for reference.
