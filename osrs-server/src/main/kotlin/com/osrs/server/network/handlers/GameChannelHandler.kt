package com.osrs.server.network.handlers

import com.osrs.server.config.GameConfig
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

class GameChannelInitializer(
    private val world: World,
    private val revision: Int = 237,
    private val proxyMode: Boolean = false,
    private val js5CacheDir: File? = null,
    private val gameConfig: GameConfig = GameConfig()
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast("login-decoder", LoginDecoder(revision = revision, js5CacheDir = js5CacheDir))
        ch.pipeline().addLast("login-handler", LoginHandler(world, proxyMode, gameConfig))
    }
}

@ChannelHandler.Sharable
class LoginHandler(
    private val world: World,
    private val proxyMode: Boolean,
    private val gameConfig: GameConfig
) : SimpleChannelInboundHandler<LoginRequest>() {

    override fun channelRead0(ctx: ChannelHandlerContext, request: LoginRequest) {
        val playerIndex = world.allocatePlayerIndex()
        if (playerIndex == -1) {
            sendStatus(ctx, 7); return
        }

        val player = Player(
            username = request.username,
            x = gameConfig.starting_x,
            y = gameConfig.starting_y,
            plane = gameConfig.starting_plane
        ).also { it.index = playerIndex }

        val isaacPair = if (!proxyMode && request.xteaKeys.isNotEmpty()) {
            IsaacPair(request.xteaKeys)
        } else null

        val session = PlayerSession(ctx.channel(), player)
        world.addPlayer(session)

        ctx.pipeline().apply {
            remove("login-decoder")
            remove("login-handler")
            addLast("game-encoder", GamePacketEncoder(isaacPair?.encoder))
            addLast("game-decoder", GamePacketDecoder(isaacPair?.decoder))
            addLast("game-handler", GameChannelHandler(session))
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

fun sendLoginSuccess(ctx: ChannelHandlerContext, playerIndex: Int, rights: Int = 0) {
    val buf = ctx.alloc().buffer(5)
    buf.writeByte(2)
    buf.writeByte(rights)
    buf.writeByte(0)
    buf.writeShort(playerIndex)
    ctx.writeAndFlush(buf)
}
