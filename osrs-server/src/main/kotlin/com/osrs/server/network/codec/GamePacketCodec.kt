package com.osrs.server.network.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.MessageToByteEncoder
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

data class ClientPacket(val prot: ClientProt, val payload: ByteBuf)
data class ServerPacket(val prot: ServerProt, val payload: ByteBuf)

class GamePacketDecoder(
    private val isaac: IsaacRandom? = null
) : ByteToMessageDecoder() {

    private var currentProt: ClientProt? = null
    private var expectedLength: Int = 0

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        while (buf.isReadable) {
            if (currentProt == null) {
                if (!buf.isReadable) return
                var rawOpcode = buf.readUnsignedByte().toInt()
                if (isaac != null) rawOpcode = (rawOpcode - isaac.nextInt()) and 0xFF
                val prot = ClientProt.fromOpcode(rawOpcode)
                if (prot == null) {
                    logger.warn { "Unknown client opcode 0x${rawOpcode.toString(16)}" }
                    ctx.close()
                    return
                }
                currentProt = prot
                expectedLength = prot.size
            }

            if (expectedLength == -1) {
                if (!buf.isReadable) return
                expectedLength = buf.readUnsignedByte().toInt()
            } else if (expectedLength == -2) {
                if (buf.readableBytes() < 2) return
                expectedLength = buf.readUnsignedShort()
            }

            if (buf.readableBytes() < expectedLength) return

            val payload = buf.readRetainedSlice(expectedLength)
            out.add(ClientPacket(currentProt!!, payload))
            currentProt = null
            expectedLength = 0
        }
    }
}

class GamePacketEncoder(
    private val isaac: IsaacRandom? = null
) : MessageToByteEncoder<ServerPacket>() {

    override fun encode(ctx: ChannelHandlerContext, msg: ServerPacket, out: ByteBuf) {
        var opcode = msg.prot.opcode
        if (isaac != null) opcode = (opcode + isaac.nextInt()) and 0xFF
        out.writeByte(opcode)

        val len = msg.payload.readableBytes()
        when (msg.prot.size) {
            -1 -> {
                check(len <= 255) { "${msg.prot} payload exceeds var-byte limit: $len" }
                out.writeByte(len)
            }
            -2 -> {
                check(len <= 65535) { "${msg.prot} payload exceeds var-short limit: $len" }
                out.writeShort(len)
            }
            else -> check(len == msg.prot.size) {
                "${msg.prot}: expected ${msg.prot.size} bytes, got $len"
            }
        }
        out.writeBytes(msg.payload)
        msg.payload.release()
    }
}
