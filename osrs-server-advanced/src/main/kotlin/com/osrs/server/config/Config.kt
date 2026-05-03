package com.osrs.server.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val server: ServerConfig = ServerConfig(),
    val rsprox: RsProxConfig = RsProxConfig(),
    val login: LoginConfig = LoginConfig(),
    val js5: Js5Config = Js5Config(),
    val game: GameConfig = GameConfig(),
    val logging: LoggingConfig = LoggingConfig()
)

@Serializable
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 43594,
    val revision: Int = 237,
    val name: String = "RSProt237 Server"
)

@Serializable
data class RsProxConfig(
    val enabled: Boolean = false,
    val proxy_port: Int = 40000,
    val target_host: String = "127.0.0.1",
    val target_port: Int = 43594
)

@Serializable
data class LoginConfig(
    val max_connections_per_ip: Int = 10,
    val token: String = "testtoken"
)

@Serializable
data class Js5Config(
    val port: Int = 43595,
    val cache_path: String = "./cache"
)

@Serializable
data class GameConfig(
    val tick_rate_ms: Long = 600,
    val max_players: Int = 2000,
    val starting_x: Int = 3222,
    val starting_y: Int = 3218,
    val starting_plane: Int = 0
)

@Serializable
data class LoggingConfig(
    val level: String = "DEBUG"
)

object AppConfigLoader {
    fun load(): AppConfig {
        val stream = AppConfigLoader::class.java.classLoader.getResourceAsStream("config.yaml")
            ?: error("Missing config.yaml resource")
        return Yaml.default.decodeFromStream(AppConfig.serializer(), stream)
    }
}
