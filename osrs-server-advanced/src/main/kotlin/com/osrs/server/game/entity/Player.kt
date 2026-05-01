package com.osrs.server.game.entity

import com.osrs.server.game.pathfinder.CollisionMap

/**
 * Authoritative server-side player entity.
 *
 * Owns position, skill/XP state, the walk queue, and pending update
 * flags consumed each tick by the tick loop / PlayerInfoBuilder.
 */
class Player(
    val username: String,
    val rights: Int = 0,
    var x: Int = 3222,
    var y: Int = 3218,
    var plane: Int = 0
) {
    var index: Int = -1
    var running: Boolean = false
    var runEnergy: Int = 100

    // ------------------------------------------------------------------
    // Skills  (index = OSRS skill ID 0..24)
    // ------------------------------------------------------------------
    val skills = IntArray(25) { id -> if (id == 3) 10 else 1 }  // HP starts at 10
    val xp     = IntArray(25) { id -> if (id == 3) 1154 else 0 }

    // ------------------------------------------------------------------
    // Walk queue
    // ------------------------------------------------------------------
    val walkQueue = WalkQueue(this)

    // ------------------------------------------------------------------
    // Per-tick update flags — set by game logic, cleared after flush
    // ------------------------------------------------------------------
    var appearanceUpdateRequired: Boolean = true  // forced on first login
    var chatUpdateRequired:       Boolean = false
    var facingUpdateRequired:     Boolean = false
    var animationUpdateRequired:  Boolean = false
    var spotAnimUpdateRequired:   Boolean = false

    var pendingAnimation:  Int    = -1
    var pendingSpotAnim:   Int    = -1
    var pendingFaceEntity: Int    = -1
    var pendingChatMessage: String = ""

    // ------------------------------------------------------------------
    // Interface state
    // ------------------------------------------------------------------
    var modalOpen: Boolean = false

    // ------------------------------------------------------------------
    // Derived coordinates
    // ------------------------------------------------------------------
    val zoneX   get() = x ushr 3
    val zoneY   get() = y ushr 3
    val regionX get() = x ushr 6
    val regionY get() = y ushr 6

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    /** Queue a BFS walk to (targetX, targetY). */
    fun walkTo(collision: CollisionMap, targetX: Int, targetY: Int) {
        walkQueue.route(collision, targetX, targetY)
    }

    /** Instant teleport — clears walk queue, marks appearance dirty. */
    fun teleport(toX: Int, toY: Int, toPlane: Int = plane) {
        walkQueue.clear()
        x = toX; y = toY; plane = toPlane
        appearanceUpdateRequired = true
    }

    fun closeModal() { modalOpen = false }

    // ------------------------------------------------------------------
    // XP helpers
    // ------------------------------------------------------------------

    /** Add [amount] XP to [skillId], clamp to 200M, recalculate level. */
    fun grantXp(skillId: Int, amount: Int) {
        xp[skillId] = minOf(xp[skillId] + amount, 200_000_000)
        skills[skillId] = xpToLevel(xp[skillId])
    }

    companion object {
        val XP_TABLE: IntArray = buildXpTable()

        private fun buildXpTable(): IntArray {
            val t = IntArray(100)
            var pts = 0.0
            for (l in 1..99) {
                pts += kotlin.math.floor(l + 300.0 * Math.pow(2.0, l / 7.0))
                t[l] = if (l < 99) (pts / 4).toInt() else 13_034_431
            }
            return t
        }

        fun xpToLevel(xp: Int): Int {
            for (l in 98 downTo 1) if (xp >= XP_TABLE[l]) return l + 1
            return 1
        }
    }
}
