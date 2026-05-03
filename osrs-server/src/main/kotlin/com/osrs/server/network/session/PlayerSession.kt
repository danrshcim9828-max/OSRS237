package com.osrs.server.network.session

import com.osrs.server.game.entity.Player
import com.osrs.server.game.pathfinder.CollisionMap
import com.osrs.server.game.world.World
import com.osrs.server.network.codec.*
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class PlayerSession(
    val channel: Channel,
    val player: Player
) {
    var active: Boolean = true
    var world: World? = null
    private val pendingPackets = ArrayDeque<ServerPacket>(32)

    fun write(prot: ServerProt, block: ByteBuf.() -> Unit = {}) {
        if (!active) return
        val buf = channel.alloc().buffer()
        buf.block()
        pendingPackets.add(ServerPacket(prot, buf))
    }

    fun flushPackets() {
        if (!active || pendingPackets.isEmpty()) return
        for (packet in pendingPackets) channel.write(packet)
        pendingPackets.clear()
        channel.flush()
    }

    fun sendMessage(message: String) {
        write(ServerProt.MESSAGE_GAME) {
            writeBytes(message.toByteArray(Charsets.UTF_8))
            writeByte(0)
        }
    }

    fun sendLogout() {
        write(ServerProt.LOGOUT_FULL)
        flushPackets()
        channel.close()
        active = false
    }

    fun sendVarpSmall(id: Int, value: Int) {
        write(ServerProt.VARP_SMALL) {
            writeShort(id)
            writeByte(value)
        }
    }

    fun sendVarpLarge(id: Int, value: Int) {
        write(ServerProt.VARP_LARGE) {
            writeShort(id)
            writeInt(value)
        }
    }

    fun sendVarp(id: Int, value: Int) {
        if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) sendVarpSmall(id, value)
        else sendVarpLarge(id, value)
    }

    fun sendRunEnergy(energy: Int) {
        write(ServerProt.UPDATE_RUNORG) { writeByte(energy) }
    }

    fun sendRunWeight(weight: Int) {
        write(ServerProt.UPDATE_RUNWEIGHT) { writeShort(weight) }
    }

    fun sendStat(skill: Int, level: Int, xp: Int) {
        write(ServerProt.UPDATE_STAT) {
            writeByte(skill)
            writeByte(level)
            writeByte(level)
            writeInt(xp)
        }
    }

    fun sendRebuildNormal(zoneX: Int, zoneY: Int, keys: IntArray) {
        write(ServerProt.REBUILD_NORMAL) {
            writeShort(zoneX)
            writeShort(zoneY)
            writeShort(keys.size)
            for (key in keys) writeInt(key)
        }
    }

    fun sendPing() {
        write(ServerProt.SERVER_TICK_END)
    }

    fun sendInitialGameState() {
        sendRebuildNormal(player.zoneX, player.zoneY, IntArray(0))
        for (skill in 0..24) sendStat(skill, player.skills[skill], player.xp[skill])
        sendRunEnergy(player.runEnergy)
        sendRunWeight(0)
        sendVarp(173, 0)
        sendMessage("Welcome to the OSRS private server.")
        flushPackets()
    }

    fun handlePacket(packet: ClientPacket) {
        try {
            when (packet.prot) {
                ClientProt.PING -> { /* no-op */ }
                ClientProt.WINDOW_STATUS -> handleWindowStatus(packet.payload)
                ClientProt.MOVE_GAMECLICK -> handleMoveGameClick(packet.payload)
                ClientProt.MOVE_MINIMAPCLICK -> handleMoveMinimapClick(packet.payload)
                ClientProt.CHAT_SEND_PUBLIC -> handlePublicChat(packet.payload)
                ClientProt.CHAT_COMMAND -> handleChatCommand(packet.payload)
                ClientProt.CLOSE_MODAL -> player.closeModal()
            }
        } finally {
            packet.payload.release()
        }
    }

    private fun handleWindowStatus(buf: ByteBuf) {
        val mode = buf.readUnsignedByte().toInt()
        val width = buf.readUnsignedShort()
        val height = buf.readUnsignedShort()
        logger.debug { "${player.username} window: mode=$mode ${width}x${height}" }
    }

    private fun handleMoveGameClick(buf: ByteBuf) {
        val x = buf.readUnsignedShort()
        val y = buf.readUnsignedShort()
        val ctrl = buf.readUnsignedByte().toInt() == 1
        logger.debug { "MOVE_GAMECLICK ($x,$y) ctrl=$ctrl" }
        world?.let { player.walkTo(it.collision, x, y) } ?: run { player.x = x; player.y = y }
    }

    private fun handleMoveMinimapClick(buf: ByteBuf) {
        val x = buf.readUnsignedShort()
        val y = buf.readUnsignedShort()
        logger.debug { "MOVE_MINIMAPCLICK ($x,$y)" }
        world?.let { player.walkTo(it.collision, x, y) } ?: run { player.x = x; player.y = y }
    }

    private fun handlePublicChat(buf: ByteBuf) {
        val message = readJagString(buf)
        if (message.isBlank()) return
        logger.info { "[PUBLIC] ${player.username}: $message" }
        sendMessage("You say: $message")
        flushPackets()
    }

    private fun handleChatCommand(buf: ByteBuf) {
        val command = readJagString(buf).trim()
        when (command.lowercase()) {
            "pos" -> sendMessage("Position: ${player.x}, ${player.y}, plane ${player.plane}")
            else -> sendMessage("Unknown command: $command")
        }
        flushPackets()
    }

    private fun readJagString(buf: ByteBuf): String {
        val sb = StringBuilder()
        while (buf.isReadable) {
            val b = buf.readUnsignedByte().toInt()
            if (b == 0) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }
}
