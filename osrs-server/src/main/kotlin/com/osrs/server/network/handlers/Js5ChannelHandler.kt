package com.osrs.server.network.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

class Js5ChannelHandler(
    private val cacheDir: File
) : ByteToMessageDecoder() {

    private enum class State { HANDSHAKE, REQUESTS }
    private var state = State.HANDSHAKE

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        when (state) {
            State.HANDSHAKE -> handleHandshake(ctx)
            State.REQUESTS -> handleRequests(ctx, buf)
        }
    }

    private fun handleHandshake(ctx: ChannelHandlerContext) {
        val response = ctx.alloc().buffer(1)
        response.writeByte(0)
        ctx.writeAndFlush(response)
        state = State.REQUESTS
        logger.debug { "JS5 handshake complete from ${ctx.channel().remoteAddress()}" }
    }

    private fun handleRequests(ctx: ChannelHandlerContext, buf: ByteBuf) {
        while (buf.readableBytes() >= 4) {
            val type = buf.readUnsignedByte().toInt()
            val archiveId = buf.readUnsignedByte().toInt()
            val groupId = buf.readUnsignedShort()
            if (type == 2) continue
            serveFile(ctx, archiveId, groupId)
        }
    }

    private fun serveFile(ctx: ChannelHandlerContext, archiveId: Int, groupId: Int) {
        val file = File(cacheDir, "${archiveId}_${groupId}.dat")
        val payload = if (file.exists()) file.readBytes() else ByteArray(0)
        val response = ctx.alloc().buffer(8 + payload.size)
        response.writeByte(archiveId)
        response.writeShort(groupId)
        response.writeByte(0)
        response.writeInt(payload.size)
        if (payload.isNotEmpty()) response.writeBytes(payload)
        ctx.writeAndFlush(response)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.warn(cause) { "JS5 handler exception" }
        ctx.close()
    }
}
