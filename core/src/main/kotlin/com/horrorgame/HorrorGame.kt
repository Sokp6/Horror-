package com.horrorgame

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

class HorrorGame : Game() {
    lateinit var batch: SpriteBatch
    lateinit var shapes: ShapeRenderer

    override fun create() {
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        screen = GameScreen(this)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        screen.dispose()
    }
}
