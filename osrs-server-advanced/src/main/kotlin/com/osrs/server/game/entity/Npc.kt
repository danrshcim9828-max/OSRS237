package com.osrs.server.game.entity

/**
 * Server-side NPC entity.
 *
 * Lifecycle is managed by [com.osrs.server.game.world.NpcManager].
 * Per-tick update flags follow the same model as [Player]:
 * set by game logic, consumed by NpcInfoBuilder, cleared after flush.
 */
class Npc(
    val id: Int,           // NPC definition ID (matches client cache)
    var x: Int,
    var y: Int,
    var plane: Int = 0
) {
    var index: Int = -1

    /** Walk bounds — NPC stays within this box (inclusive). */
    var walkRadiusX: Int = 0
    var walkRadiusY: Int = 0
    val spawnX: Int = x
    val spawnY: Int = y

    var hitpoints: Int = 1
    var maxHitpoints: Int = 1

    // Per-tick flags
    var animationUpdateRequired: Boolean = false
    var spotAnimUpdateRequired:  Boolean = false
    var hitUpdateRequired:       Boolean = false
    var transformUpdateRequired: Boolean = false

    var pendingAnimation: Int = -1
    var pendingSpotAnim:  Int = -1
    var pendingTransform: Int = -1   // NPC ID to morph into

    /** NPC is active (alive / not despawned). */
    var active: Boolean = true

    val zoneX get() = x ushr 3
    val zoneY get() = y ushr 3
}
