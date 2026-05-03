package com.osrs.server

import com.osrs.server.config.AppConfigLoader
import com.osrs.server.game.world.World
import com.osrs.server.network.codec.ClientProt
import com.osrs.server.network.codec.ServerProt
import com.osrs.server.network.handlers.GameChannelInitializer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

private val logger = KotlinLogging.logger {}
var REVISION = 237

fun main() {
    val config = AppConfigLoader.load()
    REVISION = config.server.revision

    logger.info { "==================================================" }
    logger.info { " ${config.server.name} — Revision $REVISION " }
    logger.info { "==================================================" }

    validateOpcodeTables()

    val world = World(maxPlayers = config.game.max_players, tickRateMs = config.game.tick_rate_ms)
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    world.start(scope)

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
                    js5CacheDir = File(config.js5.cache_path),
                    gameConfig = config.game
                )
            )

        val channel = bootstrap.bind(config.server.host, config.server.port).sync().channel()
        logger.info { "Server listening on ${config.server.host}:${config.server.port} (rev=$REVISION)" }
        if (config.rsprox.enabled) {
            logger.info { "RSProx mode enabled. Target: ${config.rsprox.target_host}:${config.rsprox.target_port}" }
        }
        logger.info { "Loaded ${ServerProt.entries.size} server opcodes, ${ClientProt.entries.size} client opcodes" }
        channel.closeFuture().sync()
    } finally {
        world.stop()
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        logger.info { "Server shutdown complete" }
    }
}

fun validateOpcodeTables() {
    val serverDupes = ServerProt.validateNoDuplicates()
    val clientDupes = ClientProt.validateNoDuplicates()

    if (serverDupes.isNotEmpty()) {
        logger.error { "SERVER OPCODE CONFLICTS DETECTED:" }
        serverDupes.forEach { logger.error { "  $it" } }
    } else {
        logger.info { "ServerProt: ${ServerProt.entries.size} opcodes OK" }
    }

    if (clientDupes.isNotEmpty()) {
        logger.error { "CLIENT OPCODE CONFLICTS DETECTED:" }
        clientDupes.forEach { logger.error { "  $it" } }
    } else {
        logger.info { "ClientProt: ${ClientProt.entries.size} opcodes OK" }
    }

    if (serverDupes.isEmpty() && clientDupes.isEmpty()) {
        logger.info { "Opcode alignment: PASSED" }
    }
}
