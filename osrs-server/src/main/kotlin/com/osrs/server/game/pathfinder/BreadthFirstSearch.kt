package com.osrs.server.game.pathfinder

object BreadthFirstSearch {
    const val MAX_REACH = 64
    const val MAX_NODES = MAX_REACH * MAX_REACH * 4

    fun find(
        collision: CollisionMap,
        srcX: Int,
        srcY: Int,
        dstX: Int,
        dstY: Int,
        plane: Int
    ): List<Tile>? {
        if (srcX == dstX && srcY == dstY) return emptyList()

        val size = MAX_REACH * 2 + 1
        val visited = IntArray(size * size) { -1 }
        val queue = IntArray(MAX_NODES)
        var head = 0
        var tail = 0

        val originX = srcX - MAX_REACH
        val originY = srcY - MAX_REACH

        fun idx(x: Int, y: Int) = (x - originX) * size + (y - originY)

        val startIdx = idx(srcX, srcY)
        visited[startIdx] = startIdx
        queue[tail++] = startIdx

        val dxArray = intArrayOf(-1, 0, 1, 0, -1, -1, 1, 1)
        val dyArray = intArrayOf(0, 1, 0, -1, -1, 1, -1, 1)

        var foundIdx = -1

        outer@ while (head < tail && tail < MAX_NODES) {
            val cur = queue[head++] 
            val cx = cur / size + originX
            val cy = cur % size + originY

            for (d in 0..7) {
                val nx = cx + dxArray[d]
                val ny = cy + dyArray[d]
                if (nx < originX || ny < originY || nx >= originX + size || ny >= originY + size) continue
                val ni = idx(nx, ny)
                if (visited[ni] != -1) continue
                if (!collision.canTravel(cx, cy, plane, dxArray[d], dyArray[d])) continue
                visited[ni] = cur
                queue[tail++] = ni
                if (nx == dstX && ny == dstY) {
                    foundIdx = ni
                    break@outer
                }
            }
        }

        if (foundIdx == -1) return null

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

data class Tile(val x: Int, val y: Int, val plane: Int)

object CollisionFlags {
    const val WALL_NORTH = 0x2
    const val WALL_EAST = 0x8
    const val WALL_SOUTH = 0x20
    const val WALL_WEST = 0x80
    const val OBJECT = 0x100
    const val OPEN = 0
}

class CollisionMap {
    private val flags = HashMap<Long, Int>()

    private fun key(x: Int, y: Int, plane: Int) = plane.toLong() shl 32 or (x.toLong() shl 16) or y.toLong()

    fun getFlags(x: Int, y: Int, plane: Int): Int = flags[key(x, y, plane)] ?: CollisionFlags.OPEN

    fun setFlags(x: Int, y: Int, plane: Int, mask: Int) {
        flags[key(x, y, plane)] = mask
    }

    fun canTravel(x: Int, y: Int, plane: Int, dx: Int, dy: Int): Boolean {
        val nx = x + dx
        val ny = y + dy
        if (dx == 0 && dy == 0) return true
        return getFlags(nx, ny, plane) == CollisionFlags.OPEN
    }
}
