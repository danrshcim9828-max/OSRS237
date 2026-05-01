package com.osrs.server.game.world

import com.osrs.server.game.entity.Npc
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Registry and tick processor for all [Npc] entities.
 *
 * OSRS supports up to 32767 NPC slots (indices 1..32767).
 * For a private server, the practical limit depends on the client's
 * NPC_INFO viewport; keeping under ~5000 active NPCs is safe.
 *
 * Integration point: call [tick] from [World.tick] before the
 * NPC info packet is built.
 */
class NpcManager(private val maxNpcs: Int = 32767) {

    private val nextIndex = AtomicInteger(1)
    private val npcs = ConcurrentHashMap<Int, Npc>()

    // ------------------------------------------------------------------
    // Spawn / remove
    // ------------------------------------------------------------------

    /**
     * Spawn an NPC at (x, y, plane).
     * @return the [Npc] instance, or null if the slot pool is exhausted.
     */
    fun spawn(id: Int, x: Int, y: Int, plane: Int = 0): Npc? {
        if (npcs.size >= maxNpcs) return null
        val idx = nextIndex.getAndIncrement().takeIf { it <= maxNpcs } ?: return null
        val npc = Npc(id = id, x = x, y = y, plane = plane).also { it.index = idx }
        npcs[idx] = npc
        return npc
    }

    fun remove(npc: Npc) { npcs.remove(npc.index) }

    fun getAll(): Collection<Npc> = npcs.values

    fun get(index: Int): Npc? = npcs[index]

    val count: Int get() = npcs.size

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /**
     * Process one game tick for all active NPCs.
     * Clears per-tick update flags that were consumed this tick.
     */
    fun tick() {
        for (npc in npcs.values) {
            if (!npc.active) {
                remove(npc)
                continue
            }
            // TODO: wander AI, combat tick, respawn timer
            clearFlags(npc)
        }
    }

    private fun clearFlags(npc: Npc) {
        npc.animationUpdateRequired = false
        npc.spotAnimUpdateRequired  = false
        npc.hitUpdateRequired       = false
        npc.transformUpdateRequired = false
        npc.pendingAnimation = -1
        npc.pendingSpotAnim  = -1
        npc.pendingTransform = -1
    }
}
