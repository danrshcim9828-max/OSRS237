package com.osrs.server.game.entity

import com.osrs.server.game.pathfinder.BreadthFirstSearch
import com.osrs.server.game.pathfinder.CollisionMap
import com.osrs.server.game.pathfinder.Tile

/**
 * Walk queue for a [Player].
 *
 * OSRS movement model:
 *   - Each 600ms tick a player steps up to 1 tile (walking) or 2 tiles (running).
 *   - The queue is a FIFO of absolute [Tile] waypoints produced by [BreadthFirstSearch].
 *   - When the client sends MOVE_GAMECLICK, any pending queue is discarded and the
 *     new route is computed and enqueued.
 *
 * [processMovement] is called once per tick by the game loop. It consumes
 * one (walk) or two (run) tiles from the head of the queue and updates
 * the player's position. If the queue empties mid-tick the player stops.
 */
class WalkQueue(private val player: Player) {

    private val waypoints = ArrayDeque<Tile>(32)

    /** Whether there are pending movement steps. */
    val isMoving: Boolean get() = waypoints.isNotEmpty()

    /**
     * Discard any existing route and enqueue a new one computed by BFS.
     * No-op if the destination is unreachable.
     */
    fun route(
        collision: CollisionMap,
        dstX: Int,
        dstY: Int,
        forceRun: Boolean = false
    ) {
        waypoints.clear()
        val path = BreadthFirstSearch.find(
            collision,
            player.x, player.y, dstX, dstY, player.plane
        ) ?: return
        waypoints.addAll(path)
    }

    /**
     * Process one tick of movement.
     * Consumes 1 step (walk) or 2 steps (run) from the queue.
     *
     * @return [MovementResult] indicating direction(s) moved this tick.
     */
    fun processMovement(): MovementResult {
        if (waypoints.isEmpty()) return MovementResult.IDLE

        val first = waypoints.removeFirst()
        player.x = first.x
        player.y = first.y

        if (player.running && waypoints.isNotEmpty()) {
            val second = waypoints.removeFirst()
            player.x = second.x
            player.y = second.y
            return MovementResult.RAN
        }
        return MovementResult.WALKED
    }

    /** Force-clear the queue (e.g. when player is stunned or teleported). */
    fun clear() = waypoints.clear()
}

enum class MovementResult { IDLE, WALKED, RAN }
