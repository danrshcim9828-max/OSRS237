package com.osrs.server.network.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * JS5 (cache update) connection handler.
 *
 * The client initiates a JS5 connection before the login connection so it
 * can download any updated cache archives. The wire protocol (rev 237):
 *
 *   Client → Server:  [u8 0x0F] (connection type 15)
 *   Server → Client:  [u8 0] (status OK)
 *   Client → Server:  [u8 type][u8 archiveId][u16 groupId]  × N
 *   Server → Client:  [u8 archiveId][u16 groupId][u8 priority][u32 compressedLen][bytes…]
 *
 * Request type byte:
 *   0 = low-priority (background)
 *   1 = high-priority (needed now)
 *   2 = rekey (ignored here; only used in newer revisions)
 *
 * This implementation serves files from a local flat-file cache directory
 * at [cacheDir]. Files are named `archive_group.dat`.
 * When a requested file is absent, an empty response with length 0 is sent
 * so the client does not hang.
 */
class Js5ChannelHandler(
    private val cacheDir: java.io.File
) : ByteToMessageDecoder() {

    private enum class State { HANDSHAKE, REQUESTS }
    private var state = State.HANDSHAKE

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        when (state) {
            State.HANDSHAKE -> handleHandshake(ctx, buf)
            State.REQUESTS  -> handleRequests(ctx, buf)
        }
    }

    // ------------------------------------------------------------------
    // Handshake
    // ------------------------------------------------------------------

    private fun handleHandshake(ctx: ChannelHandlerContext, buf: ByteBuf) {
        // The login decoder already consumed the initial connection-type byte.
        val resp = ctx.alloc().buffer(1)
        resp.writeByte(0) // status OK
        ctx.writeAndFlush(resp)

        state = State.REQUESTS
        logger.debug { "JS5 handshake complete from ${ctx.channel().remoteAddress()}" }
    }

    // ------------------------------------------------------------------
    // Request loop
    // ------------------------------------------------------------------

    private fun handleRequests(ctx: ChannelHandlerContext, buf: ByteBuf) {
        while (buf.readableBytes() >= 4) {
            val type      = buf.readUnsignedByte().toInt()
            val archiveId = buf.readUnsignedByte().toInt()
            val groupId   = buf.readUnsignedShort()

            if (type == 2) { /* rekey — ignore */ continue }

            logger.debug { "JS5 request: archive=$archiveId group=$groupId priority=${if (type == 1) "HIGH" else "LOW"}" }
            serveFile(ctx, archiveId, groupId)
        }
    }

    // ------------------------------------------------------------------
    // File serving
    // ------------------------------------------------------------------

    private fun serveFile(ctx: ChannelHandlerContext, archiveId: Int, groupId: Int) {
        val file = java.io.File(cacheDir, "${archiveId}_${groupId}.dat")

        val payload: ByteArray = if (file.exists()) file.readBytes() else ByteArray(0)

        // Response header: [u8 archiveId][u16 groupId][u8 priority=0][u32 compressedLen]
        val resp = ctx.alloc().buffer(8 + payload.size)
        resp.writeByte(archiveId)
        resp.writeShort(groupId)
        resp.writeByte(0)                // compression type (0 = none)
        resp.writeInt(payload.size)
        if (payload.isNotEmpty()) resp.writeBytes(payload)

        ctx.writeAndFlush(resp)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.warn(cause) { "JS5 handler exception" }
        ctx.close()
    }
}
