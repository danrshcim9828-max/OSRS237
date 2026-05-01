package com.osrs.server.game.pathfinder

/**
 * BFS pathfinder over the OSRS tile grid.
 *
 * Produces a walk route (list of absolute tile coordinates) from a
 * source tile to a destination tile. The search is bounded by
 * [MAX_REACH] tiles in any axis and [MAX_NODES] total expansions so it
 * stays bounded even on open terrain.
 *
 * Collision is checked via [CollisionMap]. A tile is walkable when its
 * mask has none of the [FLAG_BLOCK_WALK] bits set for the current plane.
 *
 * Usage:
 *   val route = BreadthFirstSearch.find(collision, srcX, srcY, dstX, dstY, plane)
 *   if (route != null) player.setWalkRoute(route)
 */
object BreadthFirstSearch {

    const val MAX_REACH = 64          // Maximum distance searched in each axis
    const val MAX_NODES = MAX_REACH * MAX_REACH * 4

    /**
     * Find a path from (srcX, srcY) to (dstX, dstY) on [plane].
     *
     * @return ordered list of [Tile] waypoints, or null if unreachable.
     *         The source tile is NOT included; the destination tile IS
     *         included as the last entry (if reachable).
     */
    fun find(
        collision: CollisionMap,
        srcX: Int,
        srcY: Int,
        dstX: Int,
        dstY: Int,
        plane: Int
    ): List<Tile>? {
        if (srcX == dstX && srcY == dstY) return emptyList()

        // BFS state: parent pointer encoded as flat index into the local grid
        val size  = MAX_REACH * 2 + 1
        val visited = IntArray(size * size) { -1 }       // -1 = unvisited
        val queue   = IntArray(MAX_NODES)
        var head = 0
        var tail = 0

        val originX = srcX - MAX_REACH
        val originY = srcY - MAX_REACH

        fun idx(x: Int, y: Int) = (x - originX) * size + (y - originY)

        val startIdx = idx(srcX, srcY)
        visited[startIdx] = startIdx  // source is its own parent
        queue[tail++] = startIdx

        val dxArray = intArrayOf(-1, 0, 1, 0, -1, -1, 1, 1)
        val dyArray = intArrayOf(0, 1, 0, -1, -1, 1, -1, 1)

        var found = false
        var foundIdx = -1

        outer@ while (head < tail && tail < MAX_NODES) {
            val cur = queue[head++]
            val cx  = cur / size + originX
            val cy  = cur % size + originY

            // Expand cardinal + diagonal neighbours
            for (d in 0..7) {
                val nx = cx + dxArray[d]
                val ny = cy + dyArray[d]

                if (nx < originX || ny < originY ||
                    nx >= originX + size || ny >= originY + size) continue

                val ni = idx(nx, ny)
                if (visited[ni] != -1) continue

                // Diagonal moves require both adjacent cardinals to be clear
                val walkable = if (d >= 4) {
                    collision.canTravel(cx, cy, plane, dxArray[d], dyArray[d])
                } else {
                    collision.canTravel(cx, cy, plane, dxArray[d], dyArray[d])
                }
                if (!walkable) continue

                visited[ni] = cur
                queue[tail++] = ni

                if (nx == dstX && ny == dstY) {
                    foundIdx = ni
                    found = true
                    break@outer
                }
            }
        }

        if (!found) return null

        // Reconstruct path by back-tracking parent pointers
        val path = mutableListOf<Tile>()
        var cur = foundIdx
        while (cur != startIdx) {
            val x = cur / size + originX
            val y = cur % size + originY
            path.add(Tile(x, y, plane))
            cur = visited[cur]
        }
        path.reverse()
        return path
    }
}

/**
 * A coordinate triple (absolute tile).
 */
data class Tile(val x: Int, val y: Int, val plane: Int)

/**
 * Collision flag constants — matches OSRS client flag layout.
 */
object CollisionFlags {
    const val WALL_NORTH      = 0x2
    const val WALL_EAST       = 0x8
    const val WALL_SOUTH      = 0x20
    const val WALL_WEST       = 0x80
    const val OBJECT          = 0x100
    const val WALL_NORTH_WEST = 0x1
    const val WALL_NORTH_EAST = 0x4
    const val WALL_SOUTH_EAST = 0x10
    const val WALL_SOUTH_WEST = 0x40
    const val FLOOR_DECO      = 0x40000
    const val PLAYER          = 0x80000
    const val NPC             = 0x100000
    const val OPEN             = 0           // no flags set = fully open
}

/**
 * Sparse collision map backed by a HashMap.
 * In a full implementation this would be populated from the OSRS map data
 * (xteas + cache loader). Here it defaults everything to passable so the
 * server can at least move players without crashing.
 */
class CollisionMap {

    /** key = plane * PLANE_STRIDE + y * WIDTH + x */
    private val flags = HashMap<Long, Int>()

    private fun key(x: Int, y: Int, plane: Int): Long =
        plane.toLong() * 0x1000000L + y.toLong() * 0x10000L + x.toLong()

    fun getFlags(x: Int, y: Int, plane: Int): Int =
        flags[key(x, y, plane)] ?: CollisionFlags.OPEN

    fun setFlags(x: Int, y: Int, plane: Int, mask: Int) {
        flags[key(x, y, plane)] = mask
    }

    fun addFlags(x: Int, y: Int, plane: Int, mask: Int) {
        val k = key(x, y, plane)
        flags[k] = (flags[k] ?: 0) or mask
    }

    fun removeFlags(x: Int, y: Int, plane: Int, mask: Int) {
        val k = key(x, y, plane)
        val cur = flags[k] ?: return
        val next = cur and mask.inv()
        if (next == 0) flags.remove(k) else flags[k] = next
    }

    /**
     * Check whether a step from (x, y) by (dx, dy) is passable.
     * For cardinals: checks the leaving-wall flag on source + arriving-wall on dest.
     * For diagonals: both constituent cardinals must be clear.
     */
    fun canTravel(x: Int, y: Int, plane: Int, dx: Int, dy: Int): Boolean {
        return when {
            dx == -1 && dy == 0 -> {
                getFlags(x, y, plane)     and CollisionFlags.WALL_WEST  == 0 &&
                getFlags(x - 1, y, plane) and CollisionFlags.WALL_EAST  == 0 &&
                getFlags(x - 1, y, plane) and CollisionFlags.OBJECT     == 0
            }
            dx == 1 && dy == 0 -> {
                getFlags(x, y, plane)     and CollisionFlags.WALL_EAST  == 0 &&
                getFlags(x + 1, y, plane) and CollisionFlags.WALL_WEST  == 0 &&
                getFlags(x + 1, y, plane) and CollisionFlags.OBJECT     == 0
            }
            dx == 0 && dy == -1 -> {
                getFlags(x, y, plane)     and CollisionFlags.WALL_SOUTH == 0 &&
                getFlags(x, y - 1, plane) and CollisionFlags.WALL_NORTH == 0 &&
                getFlags(x, y - 1, plane) and CollisionFlags.OBJECT     == 0
            }
            dx == 0 && dy == 1 -> {
                getFlags(x, y, plane)     and CollisionFlags.WALL_NORTH == 0 &&
                getFlags(x, y + 1, plane) and CollisionFlags.WALL_SOUTH == 0 &&
                getFlags(x, y + 1, plane) and CollisionFlags.OBJECT     == 0
            }
            // Diagonal: both cardinals must be traversable
            dx == -1 && dy == 1  -> canTravel(x, y, plane, -1, 0) && canTravel(x - 1, y, plane, 0, 1)
            dx == 1  && dy == 1  -> canTravel(x, y, plane, 1, 0)  && canTravel(x + 1, y, plane, 0, 1)
            dx == -1 && dy == -1 -> canTravel(x, y, plane, -1, 0) && canTravel(x - 1, y, plane, 0, -1)
            dx == 1  && dy == -1 -> canTravel(x, y, plane, 1, 0)  && canTravel(x + 1, y, plane, 0, -1)
            else -> true
        }
    }
}
