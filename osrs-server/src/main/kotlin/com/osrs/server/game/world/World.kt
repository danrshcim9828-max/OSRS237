package com.osrs.server.game.world

import com.osrs.server.game.pathfinder.CollisionMap
import com.osrs.server.network.session.PlayerSession
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class World(
    val maxPlayers: Int = 2046,
    val tickRateMs: Long = 600L
) {
    private val players = ConcurrentHashMap<Int, PlayerSession>()
    val collision = CollisionMap()
    private var tickJob: Job? = null

    fun allocatePlayerIndex(): Int {
        if (players.size >= maxPlayers) return -1
        return (1..2046).firstOrNull { !players.containsKey(it) } ?: -1
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

    fun start(scope: CoroutineScope) {
        tickJob = scope.launch {
            logger.info { "Game loop started (tickRate=${tickRateMs}ms)" }
            while (isActive) {
                val tickStart = System.currentTimeMillis()
                try { tick() } catch (t: Throwable) {
                    logger.error(t) { "Exception in game tick" }
                }
                val elapsed = System.currentTimeMillis() - tickStart
                val remaining = tickRateMs - elapsed
                if (remaining > 0) delay(remaining) else logger.warn { "Tick overrun by ${-remaining}ms" }
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        logger.info { "Game loop stopped" }
    }

    private fun tick() {
        val activeSessions = players.values.filter { it.active }
        for (session in activeSessions) {
            session.player.walkQueue.processMovement()
        }
        for (session in activeSessions) {
            session.sendPing()
            session.flushPackets()
        }
        players.values.filter { !it.active }.forEach { removePlayer(it) }
    }
}
