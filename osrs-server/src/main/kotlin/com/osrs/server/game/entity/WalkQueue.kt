package com.osrs.server.game.entity

import com.osrs.server.game.pathfinder.BreadthFirstSearch
import com.osrs.server.game.pathfinder.CollisionMap

class WalkQueue(private val player: Player) {

    private val waypoints = ArrayDeque<Tile>(32)

    val isMoving: Boolean get() = waypoints.isNotEmpty()

    fun route(collision: CollisionMap, dstX: Int, dstY: Int) {
        waypoints.clear()
        val path = BreadthFirstSearch.find(collision, player.x, player.y, dstX, dstY, player.plane)
            ?: return
        waypoints.addAll(path)
    }

    fun processMovement(): MovementResult {
        if (waypoints.isEmpty()) return MovementResult.IDLE
        val first = waypoints.removeFirst()
        player.x = first.x
        player.y = first.y
        return MovementResult.WALKED
    }

    fun clear() {
        waypoints.clear()
    }
}

enum class MovementResult { IDLE, WALKED }
