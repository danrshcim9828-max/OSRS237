package com.osrs.server.network.codec

import com.osrs.server.login.LoginRequest
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

/**
 * OSRS Revision 237 Login Handshake Decoder
 *
 * The OSRS login flow (rev 237):
 *
 * 1. Client → Server: [u8 connection type]
 *    - 14 = New game connection
 *    - 15 = JS5 (cache update) connection
 *    - 16 = Login (normal)
 *    - 18 = Reconnect login
 *
 * 2. Server → Client: [u8 status]
 *    - 0 = OK, exchange seeds
 *
 * 3. Server → Client: [u8 0][u8 0][u8 0][u64 server seed]
 *    (8 zero bytes + 8-byte seed in some revisions; varies)
 *
 * 4. Client → Server: [u8 login type][u16 payload length]
 *    Then the encrypted RSA block + XTEA block.
 *
 * 5. Server decrypts, validates, sends [u8 2][u8 rights][u8 flagged]
 *
 * For RSProx / proxy mode, the handshake may already be decoded upstream.
 */
class LoginDecoder(
    private val revision: Int = 237,
    private val rsaPrivateKey: BigInteger? = null,   // null = skip RSA (proxy mode)
    private val rsaModulus: BigInteger? = null
) : ByteToMessageDecoder() {

    private enum class State {
        READ_CONNECTION_TYPE,
        READ_LOGIN_TYPE,
        READ_PAYLOAD
    }

    private var state = State.READ_CONNECTION_TYPE
    private var loginType: Int = 0
    private var payloadLength: Int = 0

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
                // Initial connection — send seed response
                logger.debug { "JS5/init connection from ${ctx.channel().remoteAddress()}" }
                val response = ctx.alloc().buffer(17)
                response.writeByte(0) // status OK
                repeat(8) { response.writeByte(0) }
                // 8-byte server seed (use random in prod)
                response.writeLong(System.currentTimeMillis())
                ctx.writeAndFlush(response)
                state = State.READ_LOGIN_TYPE
            }
            15 -> {
                // JS5 update connection
                logger.debug { "JS5 update server connection" }
                // Hand off to JS5 handler — for now just close
                ctx.close()
            }
            else -> {
                logger.warn { "Unknown connection type: $connectionType" }
                ctx.close()
            }
        }
    }

    private fun readLoginType(ctx: ChannelHandlerContext, buf: ByteBuf) {
        if (buf.readableBytes() < 3) return
        loginType = buf.readUnsignedByte().toInt()     // 16=login, 18=reconnect
        payloadLength = buf.readUnsignedShort()
        state = State.READ_PAYLOAD
    }

    private fun readPayload(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        if (buf.readableBytes() < payloadLength) return

        val payload = buf.readSlice(payloadLength)

        // Rev 237 login block:
        // [u8 magic 255][u16 revision][u8 low-mem flag][u8 0][u8 0]
        // [u8 isaac/XTEA key count][... 4x u32 XTEA keys]
        // [RSA block: u8 RSA block len, then RSA-encrypted data]

        val magic = payload.readUnsignedByte().toInt()
        if (magic != 255) {
            logger.warn { "Bad magic byte in login: $magic" }
            ctx.close()
            return
        }

        val clientRevision = payload.readUnsignedShort()
        if (clientRevision != revision) {
            logger.warn { "Revision mismatch: client=$clientRevision server=$revision" }
            sendLoginResponse(ctx, 6) // Out of date
            ctx.close()
            return
        }

        val lowMemory = payload.readUnsignedByte().toInt() == 1
        payload.readByte() // skip
        payload.readByte() // skip

        // XTEA keys (client→server session keys)
        val xteaKeys = IntArray(4) { payload.readInt() }

        // RSA block
        val rsaBlockLen = payload.readUnsignedShort()
        val rsaBlock = ByteArray(rsaBlockLen)
        payload.readBytes(rsaBlock)

        val decryptedBlock: ByteBuf = if (rsaPrivateKey != null && rsaModulus != null) {
            val decrypted = BigInteger(rsaBlock).modPow(rsaPrivateKey, rsaModulus).toByteArray()
            ctx.alloc().buffer().writeBytes(decrypted)
        } else {
            // No RSA (proxy/dev mode) — treat block as plaintext
            ctx.alloc().buffer().writeBytes(rsaBlock)
        }

        try {
            val rsaMagic = decryptedBlock.readUnsignedByte().toInt()
            if (rsaMagic != 10) {
                logger.warn { "RSA magic mismatch: $rsaMagic" }
                sendLoginResponse(ctx, 11) // login server rejected session
                ctx.close()
                return
            }

            // XTEA keys inside RSA block (redundant but standard)
            val innerXteaKeys = IntArray(4) { decryptedBlock.readInt() }
            val reportedSeed = decryptedBlock.readLong()

            // Password
            val password = readString(decryptedBlock)

            // Username comes after XTEA decrypt of second block — simplified here
            // In full impl, decrypt remaining payload with innerXteaKeys
            val username = readString(payload) // simplified: username in plaintext segment

            logger.info { "Login attempt: user=$username type=${if (loginType == 16) "new" else "reconnect"}" }

            out.add(LoginRequest(
                username = username,
                password = password,
                xteaKeys = innerXteaKeys,
                lowMemory = lowMemory,
                reconnect = loginType == 18
            ))
        } finally {
            decryptedBlock.release()
        }
    }

    private fun readString(buf: ByteBuf): String {
        val sb = StringBuilder()
        var b: Int
        while (buf.isReadable) {
            b = buf.readUnsignedByte().toInt()
            if (b == 10) break   // OSRS uses 0x0A (newline) as string terminator
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

/**
 * Sends the successful login response to the client.
 *
 * Format (rev 237):
 *   [u8 2]          = OK status
 *   [u8 rights]     = 0=player, 1=mod, 2=admin
 *   [u8 flagged]    = 0=normal, 1=flagged account
 *   [u16 player index]
 */
fun sendLoginSuccess(ctx: ChannelHandlerContext, playerIndex: Int, rights: Int = 0) {
    val buf = ctx.alloc().buffer(5)
    buf.writeByte(2)           // Login OK
    buf.writeByte(rights)
    buf.writeByte(0)           // not flagged
    buf.writeShort(playerIndex)
    ctx.writeAndFlush(buf)
}
