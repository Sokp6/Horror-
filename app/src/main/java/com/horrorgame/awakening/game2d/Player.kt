package com.horrorgame.awakening.game2d

import kotlin.math.*

/**
 * Player entity for 2D top-down horror game.
 */
data class Player(
    var x: Float,
    var y: Float,
    var health: Int = 100,
    val maxHealth: Int = 100,
    var sanity: Int = 100,
    val maxSanity: Int = 100,
    var hasFlashlight: Boolean = false,
    var flashlightBattery: Int = 100,
    var hasRevolver: Boolean = false,
    var ammo: Int = 0,
    val inventory: MutableList<MapData.ItemType> = mutableListOf(),
    val keys: MutableSet<String> = mutableSetOf(),
    var speed: Float = 2.5f,
    val size: Float = 20f,
    var currentZone: String = "outdoor",
    var facingAngle: Float = 0f,
    var isMoving: Boolean = false,
    var attackCooldown: Int = 0,
    var invincibleFrames: Int = 0
) {
    val isAlive: Boolean get() = health > 0 && sanity > 0
    val isDead: Boolean get() = health <= 0
    val isInsane: Boolean get() = sanity <= 0

    fun takeDamage(amount: Int) {
        if (invincibleFrames > 0) return
        health = (health - amount).coerceAtLeast(0)
        invincibleFrames = 60 // ~1 second at 60fps
    }

    fun heal(amount: Int) { health = (health + amount).coerceAtMost(maxHealth) }
    fun loseSanity(amount: Int) { sanity = (sanity - amount).coerceAtLeast(0) }
    fun restoreSanity(amount: Int) { sanity = (sanity + amount).coerceAtMost(maxSanity) }

    fun addItem(item: MapData.ItemType) {
        when (item) {
            MapData.ItemType.FLASHLIGHT -> hasFlashlight = true
            MapData.ItemType.REVOLVER -> hasRevolver = true
            MapData.ItemType.BATTERY -> flashlightBattery = (flashlightBattery + 40).coerceAtMost(100)
            MapData.ItemType.KEY_HOSPITAL -> keys.add("hospital")
            MapData.ItemType.KEY_CHURCH -> keys.add("church")
            MapData.ItemType.KEY_BUNKER -> keys.add("bunker")
            MapData.ItemType.MEDKIT -> heal(30)
            MapData.ItemType.ADRENALINE -> { heal(20); restoreSanity(30) }
            MapData.ItemType.AMMO -> ammo += 3
            MapData.ItemType.CROSS -> restoreSanity(20)
            MapData.ItemType.GASOLINE -> { speed = 3.5f; flashlightBattery = 100 }
            else -> inventory.add(item)
        }
    }

    fun hasKey(zoneId: String): Boolean = when(zoneId) {
        "church" -> keys.contains("church")
        "hospital_bsmt" -> keys.contains("bunker")
        else -> true
    }

    fun update() {
        if (attackCooldown > 0) attackCooldown--
        if (invincibleFrames > 0) invincibleFrames--
        if (hasFlashlight && isMoving) flashlightBattery = (flashlightBattery - 1).coerceAtLeast(0)
    }
}
