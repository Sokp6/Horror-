package com.horrorgame.awakening

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnNewGame = findViewById<android.widget.Button>(R.id.btn_new_game)
        val btnExit = findViewById<android.widget.Button>(R.id.btn_exit)

        btnNewGame.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
        }

        btnExit.setOnClickListener {
            finish()
        }
    }
}
