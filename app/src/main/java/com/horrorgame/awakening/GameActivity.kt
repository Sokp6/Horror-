package com.horrorgame.awakening

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.horrorgame.awakening.game3d.RaycastEngine

class GameActivity : AppCompatActivity() {

    private lateinit var engine: RaycastEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = RaycastEngine(this)
        setContentView(engine)
    }

    override fun onBackPressed() { finish() }
}
