package com.horrorgame.awakening.game

/**
 * Represents a location/room in the horror game.
 */
data class Room(
    val id: String,
    val name: String,
    val description: String,
    val detailedDescription: String,
    val exits: Map<String, String> = emptyMap(), // direction -> roomId
    val items: List<String> = emptyList(),        // item IDs initially in room
    val isDark: Boolean = false,
    val isLocked: Boolean = false,
    val unlockItem: String? = null,
    val unlockMessage: String? = null,
    val eventTrigger: String? = null,             // event ID triggered on entry
    val ambientText: String? = null               // random ambient description
)
