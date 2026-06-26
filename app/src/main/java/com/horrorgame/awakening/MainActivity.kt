package com.horrorgame.awakening

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.horrorgame.awakening.game.SaveManager

/**
 * Splash / Main Menu activity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var saveManager: SaveManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        saveManager = SaveManager(this)

        val btnNewGame = findViewById<android.widget.Button>(R.id.btn_new_game)
        val btnContinue = findViewById<android.widget.Button>(R.id.btn_continue)
        val btnSettings = findViewById<android.widget.Button>(R.id.btn_settings)
        val btnExit = findViewById<android.widget.Button>(R.id.btn_exit)

        // Show/hide continue button based on save existence
        btnContinue.isEnabled = saveManager.hasSave()
        btnContinue.alpha = if (saveManager.hasSave()) 1.0f else 0.4f

        btnNewGame.setOnClickListener {
            startGame(newGame = true)
        }

        btnContinue.setOnClickListener {
            if (saveManager.hasSave()) {
                startGame(newGame = false)
            }
        }

        btnSettings.setOnClickListener {
            // Could open settings dialog — for now toggle vibration
            val prefs = getSharedPreferences("horror_settings", MODE_PRIVATE)
            val vib = prefs.getBoolean("vibration", true)
            prefs.edit().putBoolean("vibration", !vib).apply()
            btnSettings.text = if (!vib) "Вибрация: ВЫКЛ" else "Вибрация: ВКЛ"
        }

        btnExit.setOnClickListener {
            finish()
        }
    }

    private fun startGame(newGame: Boolean) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("new_game", newGame)
        }
        startActivity(intent)
    }
}
