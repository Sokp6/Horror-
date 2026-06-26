package com.horrorgame.awakening.game

/**
 * Player state: health, sanity, inventory, and current location.
 */
data class Player(
    val currentRoomId: String = "entrance",
    var health: Int = 100,
    val maxHealth: Int = 100,
    var sanity: Int = 100,
    val maxSanity: Int = 100,
    val inventory: MutableList<String> = mutableListOf(),
    val visitedRooms: MutableSet<String> = mutableSetOf(),
    val flags: MutableMap<String, Boolean> = mutableMapOf(),
    var gameTime: Int = 0 // minutes elapsed
) {
    val isAlive: Boolean get() = health > 0 && sanity > 0

    val isInsane: Boolean get() = sanity <= 0

    val isDead: Boolean get() = health <= 0

    fun addItem(itemId: String) {
        if (!inventory.contains(itemId)) {
            inventory.add(itemId)
        }
    }

    fun removeItem(itemId: String) {
        inventory.remove(itemId)
    }

    fun hasItem(itemId: String): Boolean = inventory.contains(itemId)

    fun takeDamage(amount: Int) {
        health = (health - amount).coerceAtLeast(0)
    }

    fun heal(amount: Int) {
        health = (health + amount).coerceAtMost(maxHealth)
    }

    fun loseSanity(amount: Int) {
        sanity = (sanity - amount).coerceAtLeast(0)
    }

    fun restoreSanity(amount: Int) {
        sanity = (sanity + amount).coerceAtMost(maxSanity)
    }

    fun setFlag(key: String, value: Boolean = true) {
        flags[key] = value
    }

    fun getFlag(key: String): Boolean = flags[key] ?: false

    fun advanceTime(minutes: Int) {
        gameTime += minutes
    }
}
