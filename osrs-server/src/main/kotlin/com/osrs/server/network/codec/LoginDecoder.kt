package com.osrs.server.network.codec

import com.osrs.server.login.LoginRequest
import com.osrs.server.network.handlers.Js5ChannelHandler
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

class LoginDecoder(
    private val revision: Int = 237,
    private val rsaPrivateKey: BigInteger? = null,
    private val rsaModulus: BigInteger? = null,
    private val js5CacheDir: File? = null
) : ByteToMessageDecoder() {

    private enum class State {
        READ_CONNECTION_TYPE,
        READ_LOGIN_TYPE,
        READ_PAYLOAD
    }

    private var state = State.READ_CONNECTION_TYPE
    private var loginType = 0
    private var payloadLength = 0

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        when (state) {
            State.READ_CONNECTION_TYPE -> readConnectionType(ctx, buf, out)
            State.READ_LOGIN_TYPE -> readLoginType(ctx, buf)
            State.READ_PAYLOAD -> readPayload(ctx, buf, out)
        }
    }

    private fun readConnectionType(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        if (!buf.isReadable) return
        val connectionType = buf.readUnsignedByte().toInt()
        when (connectionType) {
            14 -> {
                logger.debug { "Game login connection from ${ctx.channel().remoteAddress()}" }
                val response = ctx.alloc().buffer(17)
                response.writeByte(0)
                repeat(8) { response.writeByte(0) }
                response.writeLong(System.currentTimeMillis())
                ctx.writeAndFlush(response)
                state = State.READ_LOGIN_TYPE
            }
            15 -> {
                if (js5CacheDir == null) {
                    logger.warn { "JS5 requested but cache directory is not configured" }
                    ctx.close()
                    return
                }
                logger.debug { "JS5 update connection from ${ctx.channel().remoteAddress()}" }
                val pipeline = ctx.pipeline()
                pipeline.remove("login-handler")
                pipeline.addLast("js5-handler", Js5ChannelHandler(js5CacheDir))
                pipeline.remove(this)
            }
            else -> {
                logger.warn { "Unknown connection type: $connectionType" }
                ctx.close()
            }
        }
    }

    private fun readLoginType(ctx: ChannelHandlerContext, buf: ByteBuf) {
        if (buf.readableBytes() < 3) return
        loginType = buf.readUnsignedByte().toInt()
        payloadLength = buf.readUnsignedShort()
        state = State.READ_PAYLOAD
    }

    private fun readPayload(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        if (buf.readableBytes() < payloadLength) return
        val payload = buf.readSlice(payloadLength)
        val magic = payload.readUnsignedByte().toInt()
        if (magic != 255) {
            logger.warn { "Bad magic byte in login: $magic" }
            ctx.close()
            return
        }
        val clientRevision = payload.readUnsignedShort()
        if (clientRevision != revision) {
            logger.warn { "Revision mismatch: client=$clientRevision server=$revision" }
            sendLoginResponse(ctx, 6)
            ctx.close()
            return
        }
        val lowMemory = payload.readUnsignedByte().toInt() == 1
        payload.readByte(); payload.readByte()
        val xteaKeys = IntArray(4) { payload.readInt() }
        val rsaBlockLen = payload.readUnsignedShort()
        val rsaBlock = ByteArray(rsaBlockLen)
        payload.readBytes(rsaBlock)

        val decryptedBlock = if (rsaPrivateKey != null && rsaModulus != null) {
            val decrypted = BigInteger(rsaBlock).modPow(rsaPrivateKey, rsaModulus).toByteArray()
            ctx.alloc().buffer().writeBytes(decrypted)
        } else {
            ctx.alloc().buffer().writeBytes(rsaBlock)
        }

        try {
            val rsaMagic = decryptedBlock.readUnsignedByte().toInt()
            if (rsaMagic != 10) {
                logger.warn { "RSA magic mismatch: $rsaMagic" }
                sendLoginResponse(ctx, 11)
                ctx.close()
                return
            }
            val innerXteaKeys = IntArray(4) { decryptedBlock.readInt() }
            decryptedBlock.readLong()
            val password = readString(decryptedBlock)
            val username = readString(decryptedBlock)
            logger.info { "Login attempt: user=$username" }
            out.add(
                LoginRequest(
                    username = username,
                    password = password,
                    xteaKeys = innerXteaKeys,
                    lowMemory = lowMemory,
                    reconnect = loginType == 18
                )
            )
        } finally {
            decryptedBlock.release()
        }
    }

    private fun readString(buf: ByteBuf): String {
        val sb = StringBuilder()
        while (buf.isReadable) {
            val b = buf.readUnsignedByte().toInt()
            if (b == 0) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun sendLoginResponse(ctx: ChannelHandlerContext, status: Int) {
        val buf = ctx.alloc().buffer(1)
        buf.writeByte(status)
        ctx.writeAndFlush(buf)
    }
}
