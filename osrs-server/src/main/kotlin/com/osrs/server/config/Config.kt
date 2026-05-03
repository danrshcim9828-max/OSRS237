package com.osrs.server.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val server: ServerConfig = ServerConfig(),
    val rsprox: RsProxConfig = RsProxConfig(),
    val js5: Js5Config = Js5Config(),
    val game: GameConfig = GameConfig(),
    val logging: LoggingConfig = LoggingConfig()
)

@Serializable
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 43594,
    val revision: Int = 237,
    val name: String = "OSRS Private Server"
)

@Serializable
data class RsProxConfig(
    val enabled: Boolean = false,
    val proxy_port: Int = 40000,
    val target_host: String = "127.0.0.1",
    val target_port: Int = 43594
)

@Serializable
data class Js5Config(
    val cache_path: String = "./cache",
    val port: Int = 43595
)

@Serializable
data class GameConfig(
    val tick_rate_ms: Long = 600,
    val max_players: Int = 2046,
    val starting_x: Int = 3222,
    val starting_y: Int = 3218,
    val starting_plane: Int = 0
)

@Serializable
data class LoggingConfig(
    val level: String = "DEBUG"
)

object AppConfigLoader {
    private const val CONFIG_PATH_PROPERTY = "osrs.config.path"
    private const val CONFIG_PATH_ENV = "OSRS_CONFIG_PATH"

    fun load(): AppConfig {
        val explicitPath = System.getProperty(CONFIG_PATH_PROPERTY)?.takeIf { it.isNotBlank() }
            ?: System.getenv(CONFIG_PATH_ENV)?.takeIf { it.isNotBlank() }

        val stream = when {
            explicitPath != null -> java.io.File(explicitPath).inputStream()
            else -> AppConfigLoader::class.java.classLoader.getResourceAsStream("config.yaml")
                ?: error("Missing config.yaml resource")
        }
        return Yaml.default.decodeFromStream(AppConfig.serializer(), stream)
    }
}
