package com.horrorgame.awakening.game

/**
 * Represents an item that can be collected and used.
 */
data class Item(
    val id: String,
    val name: String,
    val description: String,
    val examineText: String,
    val isUsable: Boolean = false,
    val useOnRoom: String? = null,          // room ID where item can be used
    val useResult: String? = null,          // result description when used correctly
    val useGrantsItem: String? = null,      // item granted when used
    val useOpensExit: String? = null,       // exit direction unlocked when used
    val isKey: Boolean = false,             // whether this is a key item
    val consumeOnUse: Boolean = true,        // removed from inventory after use
    val healthRestore: Int = 0,             // health restored on use
    val sanityRestore: Int = 0              // sanity restored on use
)
