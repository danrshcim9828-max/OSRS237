package com.osrs.server

import com.osrs.server.config.AppConfigLoader
import com.osrs.server.game.world.World
import com.osrs.server.network.codec.ClientProt
import com.osrs.server.network.codec.ServerProt
import com.osrs.server.network.handlers.GameChannelInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

private val logger = KotlinLogging.logger {}

var REVISION = 237

/**
 * RSProt 237 OSRS Private Server
 *
 * Usage:
 *   ./gradlew run
 *   -- or --
 *   java -jar osrs-server.jar
 *
 * RSProx Integration:
 *   1. Start this server on PORT (43594) or the port configured in config.yaml
 *   2. Configure RSProx to target this server's host and port
 *   3. Point the OSRS client at RSProx's listen port
 *   4. Set `rsprox.enabled: true` in config.yaml to skip ISAAC decode
 */
fun main() {
    val config = AppConfigLoader.load()
    REVISION = config.server.revision

    logger.info { "==================================================" }
    logger.info { " ${config.server.name} — RSProt Revision $REVISION   " }
    logger.info { "==================================================" }

    // Validate opcode tables on startup
    validateOpcodeTables()

    val world = World(maxPlayers = config.game.max_players, tickRateMs = config.game.tick_rate_ms)
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Start game loop
    world.start(scope)

    // Start Netty server
    val bossGroup = NioEventLoopGroup(1)
    val workerGroup = NioEventLoopGroup()

    try {
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(
                        GameChannelInitializer(
                    world = world,
                    revision = REVISION,
                    proxyMode = config.rsprox.enabled,
                    js5CacheDir = File(config.js5.cache_path)
                )
            )

        val channel = bootstrap.bind(config.server.host, config.server.port).sync().channel()

        logger.info { "Server listening on ${config.server.host}:${config.server.port} (rev=$REVISION)" }
        if (config.rsprox.enabled) {
            logger.info { "RSProx mode enabled. Point RSProx at ${config.rsprox.target_host}:${config.rsprox.target_port}" }
        } else {
            logger.info { "RSProx disabled. Connect OSRS client directly to this server." }
        }
        logger.info { "" }
        logger.info { "Loaded ${ServerProt.entries.size} server opcodes, ${ClientProt.entries.size} client opcodes" }

        channel.closeFuture().sync()
    } finally {
        world.stop()
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        logger.info { "Server shutdown complete" }
    }
}

/**
 * Validate opcode tables for duplicate mappings.
 * Duplicate opcodes indicate a misalignment with RSProt 237.
 */
fun validateOpcodeTables() {
    val serverDupes = ServerProt.validateNoDuplicates()
    val clientDupes = ClientProt.validateNoDuplicates()

    if (serverDupes.isNotEmpty()) {
        logger.error { "SERVER OPCODE CONFLICTS DETECTED:" }
        serverDupes.forEach { logger.error { "  $it" } }
        logger.warn { "These opcodes must be resolved against RSProt's ServerProt enum." }
    } else {
        logger.info { "ServerProt: ${ServerProt.entries.size} opcodes OK (no duplicates)" }
    }

    if (clientDupes.isNotEmpty()) {
        logger.error { "CLIENT OPCODE CONFLICTS DETECTED:" }
        clientDupes.forEach { logger.error { "  $it" } }
        logger.warn { "These opcodes must be resolved against RSProt's ClientProt enum." }
    } else {
        logger.info { "ClientProt: ${ClientProt.entries.size} opcodes OK (no duplicates)" }
    }

    if (serverDupes.isEmpty() && clientDupes.isEmpty()) {
        logger.info { "Opcode alignment: PASSED" }
    }
}
