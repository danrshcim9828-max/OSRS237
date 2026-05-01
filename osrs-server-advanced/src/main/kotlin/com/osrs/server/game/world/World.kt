package com.osrs.server.game.world

import com.osrs.server.game.pathfinder.CollisionMap
import com.osrs.server.network.session.PlayerSession
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Authoritative game world.
 *
 * Owns:
 *  - The player registry (index → session map)
 *  - The NPC registry via [NpcManager]
 *  - The [CollisionMap] used by the pathfinder
 *  - The 600ms coroutine tick loop
 *
 * Thread safety: [players] is a [ConcurrentHashMap]. Tick logic runs on a
 * single coroutine; all packet reads are dispatched through Netty's
 * event loop and queued into the session before [tick] processes them.
 */
class World(
    val maxPlayers: Int = 2000,
    val tickRateMs: Long = 600L
) {
    // OSRS player slots: 1..2046
    private val nextIndex = AtomicInteger(1)
    private val players   = ConcurrentHashMap<Int, PlayerSession>()

    val npcs      = NpcManager()
    val collision = CollisionMap()

    private var tickJob: Job? = null

    // ------------------------------------------------------------------
    // Player management
    // ------------------------------------------------------------------

    fun allocatePlayerIndex(): Int {
        if (players.size >= maxPlayers) return -1
        val idx = nextIndex.getAndIncrement()
        return if (idx > 2046) -1 else idx
    }

    fun addPlayer(session: PlayerSession) {
        session.world = this
        players[session.player.index] = session
        logger.info { "Player '${session.player.username}' added (idx=${session.player.index}). Online: ${players.size}" }
    }

    fun removePlayer(session: PlayerSession) {
        players.remove(session.player.index)
        session.world = null
        logger.info { "Player '${session.player.username}' removed. Online: ${players.size}" }
    }

    fun getPlayer(index: Int): PlayerSession? = players[index]
    val playerCount: Int get() = players.size

    // ------------------------------------------------------------------
    // Game loop
    // ------------------------------------------------------------------

    fun start(scope: CoroutineScope) {
        tickJob = scope.launch {
            logger.info { "Game loop started (tickRate=${tickRateMs}ms)" }
            while (isActive) {
                val t0 = System.currentTimeMillis()
                try { tick() } catch (e: Exception) {
                    logger.error(e) { "Exception in game tick" }
                }
                val elapsed = System.currentTimeMillis() - t0
                val remaining = tickRateMs - elapsed
                if (remaining > 0) delay(remaining)
                else logger.warn { "Tick overrun by ${-remaining}ms" }
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        logger.info { "Game loop stopped" }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    private fun tick() {
        val active = players.values.filter { it.active }

        // 1. Process player movement (walk queue)
        for (session in active) {
            session.player.walkQueue.processMovement()
        }

        // 2. Tick NPCs
        npcs.tick()

        // 3. Send PLAYER_INFO to every player
        //    Full RSProt PlayerInfoEncoder integration is a TODO;
        //    for now just send the tick-end marker so the client stays in sync.
        for (session in active) {
            session.sendPing()
        }

        // 4. Flush all outbound buffers
        for (session in active) {
            session.flushPackets()
        }

        // 5. Purge disconnected sessions
        players.values.filter { !it.active }.forEach { removePlayer(it) }
    }
}
