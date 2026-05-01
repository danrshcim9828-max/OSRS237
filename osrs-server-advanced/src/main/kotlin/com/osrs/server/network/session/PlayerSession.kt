package com.osrs.server.network.session

import com.osrs.server.game.entity.Player
import com.osrs.server.game.world.World
import com.osrs.server.network.codec.*
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Represents one active client connection.
 *
 * All outbound packets are queued during the tick via [write] and flushed
 * in batch at tick end via [flushPackets]. This minimises syscalls and
 * keeps Netty's pipeline traversal cost off the critical path.
 *
 * Packet handlers receive the raw [ByteBuf] payload; they must NOT
 * release it — [handlePacket] owns the lifecycle and calls release()
 * in a finally block after dispatch.
 */
class PlayerSession(
    val channel: Channel,
    val player: Player
) {
    var active: Boolean = true

    // World reference set by World.addPlayer — needed for collision map access
    var world: World? = null

    private val pendingPackets = ArrayDeque<ServerPacket>(32)

    // ------------------------------------------------------------------
    // Core write API
    // ------------------------------------------------------------------

    /** Queue a packet; [block] writes into its payload buffer. */
    fun write(prot: ServerProt, block: ByteBuf.() -> Unit = {}) {
        if (!active) return
        val buf = channel.alloc().buffer()
        buf.block()
        pendingPackets.add(ServerPacket(prot, buf))
    }

    /** Flush all queued packets to the channel in one batch. */
    fun flushPackets() {
        if (!active || pendingPackets.isEmpty()) return
        for (pkt in pendingPackets) channel.write(pkt)
        pendingPackets.clear()
        channel.flush()
    }

    // ------------------------------------------------------------------
    // Outbound helpers — ServerProt 237
    // ------------------------------------------------------------------

    fun sendMessage(message: String, type: Int = 0) {
        write(ServerProt.MESSAGE_GAME) {
            writeByte(type)
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
        write(ServerProt.VARP_SMALL) { writeShort(id); writeByte(value) }
    }

    fun sendVarpLarge(id: Int, value: Int) {
        write(ServerProt.VARP_LARGE) { writeShort(id); writeInt(value) }
    }

    fun sendVarp(id: Int, value: Int) =
        if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) sendVarpSmall(id, value)
        else sendVarpLarge(id, value)

    fun sendVarbit(id: Int, value: Int) {
        if (value in Byte.MIN_VALUE..Byte.MAX_VALUE)
            write(ServerProt.VARBIT_SMALL) { writeShort(id); writeByte(value) }
        else
            write(ServerProt.VARBIT_LARGE) { writeShort(id); writeInt(value) }
    }

    /**
     * Send a skill update.
     * @param skill  0..24 (OSRS skill index)
     * @param level  current (potentially boosted) level
     * @param xp     total XP (0..200_000_000)
     */
    fun sendStat(skill: Int, level: Int, xp: Int) {
        write(ServerProt.UPDATE_STAT) {
            writeByte(skill)
            writeByte(level)   // boosted
            writeByte(level)   // base
            writeInt(xp)
        }
    }

    /** Sync a stat from the player's own arrays. */
    fun syncStat(skillId: Int) =
        sendStat(skillId, player.skills[skillId], player.xp[skillId])

    fun sendRunEnergy(energy: Int) {
        write(ServerProt.UPDATE_RUNORG) { writeByte(energy) }
    }

    fun sendRunWeight(weight: Int) {
        write(ServerProt.UPDATE_RUNWEIGHT) { writeShort(weight) }
    }

    fun sendMidiSong(id: Int) {
        write(ServerProt.MIDI_SONG) { writeShort(id) }
    }

    fun sendSynthSound(id: Int, loops: Int = 1, delay: Int = 0) {
        write(ServerProt.SYNTH_SOUND) {
            writeShort(id); writeByte(loops); writeShort(delay)
        }
    }

    // -- Interface --

    fun openTopInterface(id: Int) {
        write(ServerProt.IF_OPENTOP) { writeShort(id) }
    }

    fun openSubInterface(parentId: Int, parentComponent: Int, childId: Int, type: Int) {
        write(ServerProt.IF_OPENSUB) {
            writeInt((parentId shl 16) or parentComponent)
            writeShort(childId)
            writeByte(type)
        }
    }

    fun closeSubInterface(parentId: Int, componentId: Int) {
        write(ServerProt.IF_CLOSESUB) {
            writeInt((parentId shl 16) or componentId)
        }
    }

    fun setInterfaceText(parentId: Int, componentId: Int, text: String) {
        write(ServerProt.IF_SETTEXT) {
            writeInt((parentId shl 16) or componentId)
            writeBytes(text.toByteArray(Charsets.UTF_8))
            writeByte(0)
        }
    }

    fun hideInterface(parentId: Int, componentId: Int, hidden: Boolean) {
        write(ServerProt.IF_SETHIDE) {
            writeInt((parentId shl 16) or componentId)
            writeByte(if (hidden) 1 else 0)
        }
    }

    fun setInterfaceObject(parentId: Int, componentId: Int, itemId: Int, zoom: Int) {
        write(ServerProt.IF_SETOBJECT) {
            writeInt((parentId shl 16) or componentId)
            writeShort(itemId)
            writeShort(zoom)
        }
    }

    fun runClientScript(id: Int, vararg args: Any) {
        write(ServerProt.CLIENTSCRIPT) {
            writeInt(id)
            // Write type descriptor + values — simplified (int-only)
            val descriptor = "i".repeat(args.size)
            writeBytes(descriptor.toByteArray())
            writeByte(0) // null terminator
            for (arg in args) writeInt(arg as Int)
        }
    }

    // -- Map --

    fun sendRebuildNormal(zoneX: Int, zoneY: Int, keys: IntArray) {
        write(ServerProt.REBUILD_NORMAL) {
            writeShort(zoneX); writeShort(zoneY)
            writeShort(keys.size)
            for (k in keys) writeInt(k)
        }
    }

    fun sendPlayerInfo(infoBytes: ByteArray) {
        write(ServerProt.PLAYER_INFO) { writeBytes(infoBytes) }
    }

    // -- Camera --

    fun sendCamMoveTo(x: Int, y: Int, height: Int, speed: Int, angle: Int) {
        write(ServerProt.CAM_MOVETO) {
            writeShort(x); writeShort(y); writeShort(height)
            writeShort(speed); writeShort(angle)
        }
    }

    fun sendCamReset() = write(ServerProt.CAM_RESET)

    fun sendCamShake(type: Int, jitter: Int, amplitude: Int, speed: Int) {
        write(ServerProt.CAM_SHAKE) {
            writeByte(type); writeByte(jitter)
            writeByte(amplitude); writeByte(speed)
        }
    }

    // -- Hints --

    fun showHintArrowTile(x: Int, y: Int, plane: Int) {
        write(ServerProt.HINT_ARROW) {
            writeByte(2 + plane)    // type 2/3/4 = tile on plane 0/1/2
            writeShort(x); writeShort(y)
            writeShort(64); writeByte(0); writeShort(0)
        }
    }

    fun clearHintArrow() = write(ServerProt.CLEAR_HINT_ARROW)

    // -- Ping (tick-end) --
    fun sendPing() = write(ServerProt.SERVER_TICK_END)

    // ------------------------------------------------------------------
    // Inbound dispatch
    // ------------------------------------------------------------------

    fun handlePacket(packet: ClientPacket) {
        try {
            when (packet.prot) {
                ClientProt.PING              -> { /* keep-alive */ }
                ClientProt.WINDOW_STATUS     -> handleWindowStatus(packet.payload)
                ClientProt.MOVE_GAMECLICK    -> handleMoveGameClick(packet.payload)
                ClientProt.MOVE_MINIMAPCLICK -> handleMoveMinimapClick(packet.payload)
                ClientProt.CAMERA_ROTATION   -> { /* cosmetic — no server action */ }
                ClientProt.CLOSE_MODAL       -> player.closeModal()

                ClientProt.CHAT_SEND_PUBLIC  -> handlePublicChat(packet.payload)
                ClientProt.CHAT_COMMAND      -> handleChatCommand(packet.payload)
                ClientProt.CHAT_SET_FILTER   -> { /* store filter preference — TODO */ }

                ClientProt.OPNPC1, ClientProt.OPNPC2, ClientProt.OPNPC3,
                ClientProt.OPNPC4, ClientProt.OPNPC5 ->
                    handleNpcInteraction(packet.prot, packet.payload)

                ClientProt.OPOBJ1, ClientProt.OPOBJ2, ClientProt.OPOBJ3,
                ClientProt.OPOBJ4, ClientProt.OPOBJ5 ->
                    handleGroundItemInteraction(packet.prot, packet.payload)

                ClientProt.OPLOC1, ClientProt.OPLOC2, ClientProt.OPLOC3,
                ClientProt.OPLOC4, ClientProt.OPLOC5 ->
                    handleLocInteraction(packet.prot, packet.payload)

                ClientProt.OPPLAYER1, ClientProt.OPPLAYER2, ClientProt.OPPLAYER3,
                ClientProt.OPPLAYER4, ClientProt.OPPLAYER5, ClientProt.OPPLAYER6,
                ClientProt.OPPLAYER7, ClientProt.OPPLAYER8 ->
                    handlePlayerInteraction(packet.prot, packet.payload)

                ClientProt.OPITEM1, ClientProt.OPITEM2, ClientProt.OPITEM3,
                ClientProt.OPITEM4, ClientProt.OPITEM5 ->
                    handleItemInteraction(packet.prot, packet.payload)
                ClientProt.OPITEMT  -> handleUseItemOnItem(packet.payload)

                ClientProt.IF_BUTTON1, ClientProt.IF_BUTTON2, ClientProt.IF_BUTTON3,
                ClientProt.IF_BUTTON4, ClientProt.IF_BUTTON5, ClientProt.IF_BUTTON6,
                ClientProt.IF_BUTTON7, ClientProt.IF_BUTTON8, ClientProt.IF_BUTTON9,
                ClientProt.IF_BUTTON10 ->
                    handleIfButton(packet.prot, packet.payload)

                ClientProt.FRIENDS_ADD   -> handleFriendsAdd(packet.payload)
                ClientProt.FRIENDS_DEL   -> handleFriendsDel(packet.payload)
                ClientProt.IGNORE_ADD    -> handleIgnoreAdd(packet.payload)
                ClientProt.IGNORE_DEL    -> handleIgnoreDel(packet.payload)

                ClientProt.RESUME_PAUSEBUTTON     -> { /* dialogue continue — TODO */ }
                ClientProt.RESUME_P_STRINGDIALOG  -> { /* string resume — TODO */ }
                ClientProt.RESUME_P_COUNTDIALOG   -> { /* count resume — TODO */ }
                ClientProt.RESUME_P_OBJDIALOG     -> { /* object dialog — TODO */ }
                ClientProt.COUNTDIALOG            -> { /* count input — TODO */ }

                ClientProt.EVENT_MOUSE_CLICK,
                ClientProt.EVENT_MOUSE_MOVE,
                ClientProt.EVENT_KEYBOARD,
                ClientProt.EVENT_NATIVE_MOUSE_CLICK,
                ClientProt.SEND_SNAPSHOT,
                ClientProt.ANTICHEAT_CLIENTSYNC  -> { /* telemetry — discard */ }

                else -> logger.debug { "Unhandled packet ${packet.prot} from '${player.username}'" }
            }
        } finally {
            packet.payload.release()
        }
    }

    // ------------------------------------------------------------------
    // Handler implementations
    // ------------------------------------------------------------------

    private fun handleWindowStatus(buf: ByteBuf) {
        val mode   = buf.readUnsignedByte().toInt()
        val width  = buf.readUnsignedShort()
        val height = buf.readUnsignedShort()
        logger.debug { "'${player.username}' window: mode=$mode ${width}x${height}" }
    }

    private fun handleMoveGameClick(buf: ByteBuf) {
        val x        = buf.readUnsignedShort()
        val y        = buf.readUnsignedShort()
        val ctrlHeld = buf.readUnsignedByte().toInt() == 1
        // remaining bytes: addedSteps — ignored for now
        logger.debug { "MOVE_GAMECLICK ($x,$y) ctrl=$ctrlHeld" }
        world?.let { player.walkTo(it.collision, x, y) }
            ?: run { player.x = x; player.y = y }  // fallback: direct teleport
    }

    private fun handleMoveMinimapClick(buf: ByteBuf) {
        val x = buf.readUnsignedShort()
        val y = buf.readUnsignedShort()
        logger.debug { "MOVE_MINIMAPCLICK ($x,$y)" }
        world?.let { player.walkTo(it.collision, x, y) }
            ?: run { player.x = x; player.y = y }
    }

    private fun handlePublicChat(buf: ByteBuf) {
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        val message = String(bytes).trim()
        if (message.isBlank()) return
        logger.info { "[PUBLIC] ${player.username}: $message" }
        sendMessage("You say: $message")
    }

    private fun handleChatCommand(buf: ByteBuf) {
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        val cmd = String(bytes).trim().lowercase()
        logger.info { "[CMD] ${player.username}: ::$cmd" }
        when {
            cmd == "pos"  -> sendMessage("Position: ${player.x}, ${player.y}, plane ${player.plane}")
            cmd == "tele" -> { player.teleport(3222, 3218); sendRebuildNormal(player.zoneX, player.zoneY, IntArray(0)); sendMessage("Teleported to spawn.") }
            cmd.startsWith("tele ") -> {
                val parts = cmd.removePrefix("tele ").split(",").map { it.trim().toIntOrNull() }
                if (parts.size >= 2 && parts[0] != null && parts[1] != null) {
                    player.teleport(parts[0]!!, parts[1]!!, parts.getOrNull(2) ?: 0)
                    sendRebuildNormal(player.zoneX, player.zoneY, IntArray(0))
                    sendMessage("Teleported to ${player.x}, ${player.y}.")
                } else {
                    sendMessage("Usage: ::tele x,y[,plane]")
                }
            }
            else -> sendMessage("Unknown command: $cmd")
        }
    }

    private fun handleNpcInteraction(prot: ClientProt, buf: ByteBuf) {
        val npcIndex = buf.readUnsignedShort()
        val option   = prot.ordinal - ClientProt.OPNPC1.ordinal + 1
        logger.debug { "NPC $npcIndex option $option by '${player.username}'" }
        // TODO: dispatch to NPC interaction handlers
    }

    private fun handleGroundItemInteraction(prot: ClientProt, buf: ByteBuf) {
        val x  = buf.readUnsignedShort()
        val y  = buf.readUnsignedShort()
        val id = buf.readUnsignedShort()
        logger.debug { "OBJ id=$id @ ($x,$y) ${prot.name}" }
        // TODO: item pickup / use
    }

    private fun handleLocInteraction(prot: ClientProt, buf: ByteBuf) {
        val x    = buf.readUnsignedShort()
        val y    = buf.readUnsignedShort()
        val id   = buf.readUnsignedShort()
        val type = buf.readUnsignedByte().toInt()
        logger.debug { "LOC id=$id @ ($x,$y) type=$type ${prot.name}" }
        // TODO: door/object handlers
    }

    private fun handlePlayerInteraction(prot: ClientProt, buf: ByteBuf) {
        val targetIndex = buf.readUnsignedShort()
        logger.debug { "OPPLAYER target=$targetIndex ${prot.name}" }
        // TODO: follow, trade, attack
    }

    private fun handleItemInteraction(prot: ClientProt, buf: ByteBuf) {
        val widgetId = buf.readInt()
        val slot     = buf.readUnsignedShort()
        val itemId   = buf.readUnsignedShort()
        logger.debug { "OPITEM widget=$widgetId slot=$slot item=$itemId ${prot.name}" }
        // TODO: item use, equip, drop
    }

    private fun handleUseItemOnItem(buf: ByteBuf) {
        val srcWidget = buf.readInt()
        val srcSlot   = buf.readUnsignedShort()
        val srcItem   = buf.readUnsignedShort()
        val dstWidget = buf.readInt()
        val dstSlot   = buf.readUnsignedShort()
        val dstItem   = buf.readUnsignedShort()
        logger.debug { "ITEM_ON_ITEM src=(widget=$srcWidget slot=$srcSlot item=$srcItem) dst=(widget=$dstWidget slot=$dstSlot item=$dstItem)" }
        // TODO: skilling combine handlers
    }

    private fun handleIfButton(prot: ClientProt, buf: ByteBuf) {
        val widgetId = buf.readInt()
        val slotId   = buf.readUnsignedShort()
        val itemId   = buf.readUnsignedShort()
        logger.debug { "IF_BUTTON widget=$widgetId slot=$slotId item=$itemId ${prot.name}" }
        // TODO: interface action handlers
    }

    private fun handleFriendsAdd(buf: ByteBuf) {
        val name = readJagString(buf)
        logger.debug { "'${player.username}' adds friend '$name'" }
        // TODO: friends list
    }

    private fun handleFriendsDel(buf: ByteBuf) {
        val name = readJagString(buf)
        logger.debug { "'${player.username}' deletes friend '$name'" }
    }

    private fun handleIgnoreAdd(buf: ByteBuf) {
        val name = readJagString(buf)
        logger.debug { "'${player.username}' ignores '$name'" }
    }

    private fun handleIgnoreDel(buf: ByteBuf) {
        val name = readJagString(buf)
        logger.debug { "'${player.username}' un-ignores '$name'" }
    }

    // ------------------------------------------------------------------
    // Util
    // ------------------------------------------------------------------

    /** Read a null-terminated (0x00) UTF-8 string from [buf]. */
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
