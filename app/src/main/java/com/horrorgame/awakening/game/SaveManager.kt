package com.horrorgame.awakening.game

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Handles saving and loading game state using SharedPreferences.
 */
class SaveManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("horror_save", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PLAYER = "player_data"
        private const val KEY_HAS_SAVE = "has_save"
    }

    fun saveGame(player: Player) {
        val json = gson.toJson(player)
        prefs.edit()
            .putString(KEY_PLAYER, json)
            .putBoolean(KEY_HAS_SAVE, true)
            .apply()
    }

    fun loadGame(): Player? {
        if (!hasSave()) return null

        val json = prefs.getString(KEY_PLAYER, null) ?: return null
        return try {
            val type = object : TypeToken<Player>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun hasSave(): Boolean = prefs.getBoolean(KEY_HAS_SAVE, false)

    fun deleteSave() {
        prefs.edit()
            .remove(KEY_PLAYER)
            .putBoolean(KEY_HAS_SAVE, false)
            .apply()
    }
}
