package com.horrorgame.awakening

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.horrorgame.awakening.game2d.GameView

class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onBackPressed() {
        finish()
    }
}
