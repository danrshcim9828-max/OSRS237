package com.osrs.server.network.handlers

import com.osrs.server.game.entity.Player
import com.osrs.server.game.world.World
import com.osrs.server.login.LoginRequest
import com.osrs.server.network.codec.*
import com.osrs.server.network.session.PlayerSession
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Netty pipeline initializer for game connections.
 *
 * Pipeline (before login):
 *   [LoginDecoder] → [LoginHandler]
 *
 * Pipeline (after successful login):
 *   [GamePacketEncoder] → [GamePacketDecoder] → [GameChannelHandler]
 *
 * When [proxyMode] is true (RSProx in front), ISAAC is skipped: RSProx
 * has already unscrambled opcodes before forwarding.
 */
class GameChannelInitializer(
    private val world: World,
    private val revision: Int = 237,
    private val proxyMode: Boolean = false,
    private val js5CacheDir: File? = null
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast("login-decoder", LoginDecoder(revision = revision, js5CacheDir = js5CacheDir))
        ch.pipeline().addLast("login-handler", LoginHandler(world, proxyMode))
    }
}

/**
 * Login-phase handler.
 * Receives a decoded [LoginRequest], allocates a player slot, upgrades
 * the pipeline to game-phase codecs, and fires the initial game state.
 */
@ChannelHandler.Sharable
class LoginHandler(
    private val world: World,
    private val proxyMode: Boolean
) : SimpleChannelInboundHandler<LoginRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, request: LoginRequest) {
        val playerIndex = world.allocatePlayerIndex()
        if (playerIndex == -1) {
            sendStatus(ctx, 7); return   // world full
        }

        val player = Player(
            username = request.username,
            x = 3222, y = 3218, plane = 0
        ).also { it.index = playerIndex }

        // Build ISAAC pair from login XTEA keys (skip in proxy mode)
        val isaacPair = if (!proxyMode && request.xteaKeys.isNotEmpty()) {
            IsaacPair(request.xteaKeys)
        } else null

        val session = PlayerSession(ctx.channel(), player)
        world.addPlayer(session)

        // Swap login pipeline → game pipeline
        ctx.pipeline().apply {
            remove("login-decoder")
            remove("login-handler")
            addLast("game-encoder",  GamePacketEncoder(isaacPair?.encoder))
            addLast("game-decoder",  GamePacketDecoder(isaacPair?.decoder))
            addLast("game-handler",  GameChannelHandler(session))
        }

        sendLoginSuccess(ctx, playerIndex, player.rights)
        session.sendInitialGameState()
        logger.info { "Player '${player.username}' logged in at index $playerIndex" }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error(cause) { "LoginHandler exception" }
        ctx.close()
    }

    private fun sendStatus(ctx: ChannelHandlerContext, code: Int) {
        ctx.alloc().buffer(1).also { it.writeByte(code) }
            .let { ctx.writeAndFlush(it).addListener(ChannelFutureListener.CLOSE) }
    }
}

/**
 * Game-phase inbound handler. Dispatches [ClientPacket]s to [PlayerSession].
 */
class GameChannelHandler(
    private val session: PlayerSession
) : SimpleChannelInboundHandler<ClientPacket>() {

    override fun channelRead0(ctx: ChannelHandlerContext, packet: ClientPacket) {
        session.handlePacket(packet)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger.info { "Player '${session.player.username}' disconnected" }
        session.active = false
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.warn(cause) { "Exception for '${session.player.username}'" }
        ctx.close()
    }
}

/**
 * Sends the login-OK response (status 2) with rights and player index.
 */
fun sendLoginSuccess(ctx: ChannelHandlerContext, playerIndex: Int, rights: Int = 0) {
    val buf = ctx.alloc().buffer(5)
    buf.writeByte(2)
    buf.writeByte(rights)
    buf.writeByte(0)          // not flagged
    buf.writeShort(playerIndex)
    ctx.writeAndFlush(buf)
}

/**
 * Extension: build and flush the initial game state packet sequence.
 * Sent immediately after login-OK so the client can load the world.
 */
fun PlayerSession.sendInitialGameState() {
    sendRebuildNormal(player.zoneX, player.zoneY, IntArray(0))
    for (skill in 0..24) sendStat(skill, player.skills[skill], player.xp[skill])
    sendRunEnergy(player.runEnergy)
    sendRunWeight(0)
    sendVarp(173, 0)      // Run mode varp (0=walk, 1=run)
    sendMessage("Welcome to RSProt 237 Private Server.")
    sendMessage("Revision ${com.osrs.server.REVISION} | opcodes verified.")
    flushPackets()
}
