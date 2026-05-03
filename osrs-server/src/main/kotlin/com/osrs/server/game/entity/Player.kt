package com.osrs.server.game.entity

import com.osrs.server.game.pathfinder.CollisionMap

class Player(
    val username: String,
    val rights: Int = 0,
    var x: Int = 3222,
    var y: Int = 3218,
    var plane: Int = 0
) {
    var index: Int = -1
    var runEnergy: Int = 100
    val skills = IntArray(25) { id -> if (id == 3) 10 else 1 }
    val xp = IntArray(25) { id -> if (id == 3) 1154 else 0 }
    val walkQueue = WalkQueue(this)
    var modalOpen: Boolean = false
    val zoneX: Int get() = x ushr 3
    val zoneY: Int get() = y ushr 3

    fun walkTo(collision: CollisionMap, targetX: Int, targetY: Int) {
        walkQueue.route(collision, targetX, targetY)
    }

    fun teleport(toX: Int, toY: Int, toPlane: Int = plane) {
        walkQueue.clear()
        x = toX; y = toY; plane = toPlane
    }

    fun closeModal() {
        modalOpen = false
    }
}
