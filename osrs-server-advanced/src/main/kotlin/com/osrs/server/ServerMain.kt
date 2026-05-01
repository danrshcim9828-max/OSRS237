package com.osrs.server

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

private val logger = KotlinLogging.logger {}

const val REVISION = 237
const val PORT = 43594
const val HOST = "0.0.0.0"

/**
 * RSProt 237 OSRS Private Server
 *
 * Usage:
 *   ./gradlew run
 *   -- or --
 *   java -jar osrs-server.jar
 *
 * RSProx Integration:
 *   1. Start this server on PORT (43594)
 *   2. Configure RSProx to target 127.0.0.1:43594
 *   3. Point the OSRS client at RSProx's listen port
 *   4. Set proxyMode=true in GameChannelInitializer to skip ISAAC decode
 */
fun main() {
    logger.info { "==================================================" }
    logger.info { " OSRS Private Server — RSProt Revision $REVISION   " }
    logger.info { "==================================================" }

    // Validate opcode tables on startup
    validateOpcodeTables()

    val world = World(maxPlayers = 2000, tickRateMs = 600)
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
                    proxyMode = false  // Set true when using RSProx
                )
            )

        val channel = bootstrap.bind(HOST, PORT).sync().channel()

        logger.info { "Server listening on $HOST:$PORT (rev=$REVISION)" }
        logger.info { "RSProx: point your proxy at this server's port $PORT" }
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
