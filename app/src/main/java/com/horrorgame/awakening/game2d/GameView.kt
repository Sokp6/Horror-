package com.horrorgame.awakening.game2d

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

/**
 * Main game view — renders the 2D top-down horror world using Canvas.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: Thread? = null
    private var running = false
    val world = GameWorld()

    // Paint objects
    private val wallPaint = Paint().apply { color = Color.rgb(60, 60, 70) }
    private val floorPaint = Paint().apply { color = Color.rgb(40, 38, 45) }
    private val grassPaint = Paint().apply { color = Color.rgb(25, 35, 20) }
    private val roadPaint = Paint().apply { color = Color.rgb(55, 52, 50) }
    private val treePaint = Paint().apply { color = Color.rgb(15, 40, 10) }
    private val waterPaint = Paint().apply { color = Color.rgb(10, 20, 50) }
    private val gravePaint = Paint().apply { color = Color.rgb(50, 45, 40) }
    private val doorPaint = Paint().apply { color = Color.rgb(100, 70, 40) }
    private val lockedPaint = Paint().apply { color = Color.rgb(80, 30, 30) }
    private val exitPaint = Paint().apply { color = Color.rgb(30, 80, 30) }

    private val playerPaint = Paint().apply { color = Color.rgb(200, 180, 160); isAntiAlias = true }
    private val playerDirPaint = Paint().apply { color = Color.rgb(220, 210, 190) }
    private val shadowPaint = Paint().apply { color = Color.rgb(30, 30, 30) }
    private val screamerPaint = Paint().apply { color = Color.rgb(120, 20, 20) }
    private val wraithPaint = Paint().apply { color = Color.rgb(40, 30, 80) }
    private val bossPaint = Paint().apply { color = Color.rgb(180, 10, 10) }

    private val medkitPaint = Paint().apply { color = Color.rgb(200, 40, 40); isAntiAlias = true }
    private val ammoPaint = Paint().apply { color = Color.rgb(180, 160, 40); isAntiAlias = true }
    private val keyPaint = Paint().apply { color = Color.rgb(200, 180, 40); isAntiAlias = true }
    private val genericItemPaint = Paint().apply { color = Color.rgb(100, 180, 200); isAntiAlias = true }

    private val hudPaint = Paint().apply { color = Color.WHITE; textSize = 36f; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val hudSmallPaint = Paint().apply { color = Color.WHITE; textSize = 24f; typeface = Typeface.MONOSPACE }
    private val hudDangerPaint = Paint().apply { color = Color.rgb(255, 50, 50); textSize = 36f; isFakeBoldText = true; typeface = Typeface.MONOSPACE }
    private val msgPaint = Paint().apply { color = Color.WHITE; textSize = 28f; typeface = Typeface.MONOSPACE; isAntiAlias = true }
    private val msgDangerPaint = Paint().apply { color = Color.rgb(255, 50, 50); textSize = 28f; typeface = Typeface.MONOSPACE }
    private val darkOverlay = Paint().apply { color = Color.argb(180, 0, 0, 0) }
    private val flashPaint = Paint().apply { color = Color.argb(0, 255, 0, 0) }

    // Virtual joystick
    private var joystickActive = false
    private var joystickBaseX = 0f
    private var joystickBaseY = 0f
    private var joystickDx = 0f
    private var joystickDy = 0f
    private var joystickPointerId = -1
    private var attackPressed = false

    // Camera
    private var camX = 0f
    private var camY = 0f

    // Screen dimensions
    private var screenW = 1
    private var screenH = 1

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(holder: SurfaceHolder) { startGameLoop() }
    override fun surfaceChanged(holder: SurfaceHolder, fmt: Int, w: Int, h: Int) {
        screenW = w; screenH = h
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) { stopGameLoop() }

    private fun startGameLoop() {
        running = true
        gameThread = Thread {
            var lastTime = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val delta = (now - lastTime) / 1_000_000_000f
                lastTime = now

                update(delta)
                draw()

                try { Thread.sleep(16) } catch (e: InterruptedException) { break }
            }
        }
        gameThread?.start()
    }

    private fun stopGameLoop() {
        running = false
        gameThread?.interrupt()
        gameThread = null
    }

    private fun update(delta: Float) {
        val joystickDxNorm = if (joystickActive) joystickDx / 80f else 0f
        val joystickDyNorm = if (joystickActive) joystickDy / 80f else 0f
        world.update(joystickDxNorm, joystickDyNorm, attackPressed)
        attackPressed = false
    }

    // ==================== TOUCH ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val px = event.getX(idx)
                if (px < screenW / 2) {
                    // Left side = joystick
                    joystickActive = true
                    joystickPointerId = event.getPointerId(idx)
                    joystickBaseX = px; joystickBaseY = event.getY(idx)
                    joystickDx = 0f; joystickDy = 0f
                } else {
                    // Right side = attack
                    attackPressed = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (event.getPointerId(i) == joystickPointerId) {
                        joystickDx = event.getX(i) - joystickBaseX
                        joystickDy = event.getY(i) - joystickBaseY
                        val len = sqrt(joystickDx * joystickDx + joystickDy * joystickDy)
                        if (len > 80f) {
                            joystickDx = joystickDx / len * 80f
                            joystickDy = joystickDy / len * 80f
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == joystickPointerId) {
                    joystickActive = false; joystickPointerId = -1
                    joystickDx = 0f; joystickDy = 0f
                }
            }
        }
        return true
    }

    // ==================== RENDER ====================

    private fun draw() {
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(Color.rgb(10, 8, 12))

            // Camera follows player
            camX = world.player.x - screenW / 2f
            camY = world.player.y - screenH / 2f

            canvas.save()
            // Screen shake
            val shakeX = if (world.screenShake > 0) (Math.random() * 8 - 4).toFloat() else 0f
            val shakeY = if (world.screenShake > 0) (Math.random() * 8 - 4).toFloat() else 0f
            canvas.translate(-camX + shakeX, -camY + shakeY)

            drawTiles(canvas)
            drawItems(canvas)
            drawMonsters(canvas)
            drawPlayer(canvas)

            canvas.restore()

            // Dark overlay for dark zones
            if (world.currentZone.isDark) {
                drawDarkness(canvas)
            }

            // HUD
            drawHUD(canvas)
            drawMessages(canvas)
            drawJoystick(canvas)

            // Flash effect
            if (world.flashAlpha > 0) {
                flashPaint.alpha = (world.flashAlpha * 200).toInt()
                canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), flashPaint)
            }

            // Game over screen
            if (world.isGameOver) {
                drawGameOver(canvas)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawTiles(canvas: Canvas) {
        val ts = MapData.TILE_SIZE
        val zone = world.currentZone
        val startTX = ((camX) / ts).toInt().coerceAtLeast(0)
        val startTY = ((camY) / ts).toInt().coerceAtLeast(0)
        val endTX = ((camX + screenW) / ts + 1).toInt().coerceAtMost(zone.width - 1)
        val endTY = ((camY + screenH) / ts + 1).toInt().coerceAtMost(zone.height - 1)

        for (ty in startTY..endTY) {
            for (tx in startTX..endTX) {
                val tile = zone.tiles[ty][tx]
                val paint = when (tile) {
                    MapData.WALL -> wallPaint
                    MapData.FLOOR -> floorPaint
                    MapData.GRASS -> grassPaint
                    MapData.ROAD -> roadPaint
                    MapData.TREE -> treePaint
                    MapData.WATER -> waterPaint
                    MapData.GRAVE -> gravePaint
                    MapData.DOOR -> doorPaint
                    MapData.DOOR_LOCKED -> lockedPaint
                    MapData.EXIT -> exitPaint
                    else -> grassPaint
                }
                val x = tx * ts; val y = ty * ts
                canvas.drawRect(Rect(x, y, x + ts, y + ts), paint)

                // Wall edges for depth
                if (tile == MapData.WALL) {
                    val edgePaint = Paint().apply { color = Color.rgb(45, 45, 52) }
                    canvas.drawLine(x.toFloat(), y.toFloat(), (x+ts).toFloat(), y.toFloat(), edgePaint)
                    canvas.drawLine(x.toFloat(), y.toFloat(), x.toFloat(), (y+ts).toFloat(), edgePaint)
                }
                // Tree details
                if (tile == MapData.TREE) {
                    val detailPaint = Paint().apply { color = Color.rgb(10, 30, 5) }
                    canvas.drawCircle(x + ts/2f, y + ts/2f, ts/3f, detailPaint)
                }
                // Exit marker
                if (tile == MapData.EXIT) {
                    val arrowPaint = Paint().apply { color = Color.rgb(50, 200, 50); isAntiAlias = true }
                    canvas.drawCircle(x + ts/2f, y + ts/2f, ts/4f, arrowPaint)
                }
            }
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val p = world.player
        val x = p.x; val y = p.y; val r = p.size / 2f

        // Body glow in dark zones
        if (world.currentZone.isDark && p.hasFlashlight) {
            val glowPaint = Paint().apply {
                shader = RadialGradient(x, y, 120f, Color.argb(60, 255, 240, 200), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(x, y, 120f, glowPaint)
        }

        // Body
        canvas.drawCircle(x, y, r, playerPaint)

        // Direction indicator
        val dx = x + cos(p.facingAngle.toDouble()).toFloat() * r * 1.5f
        val dy = y + sin(p.facingAngle.toDouble()).toFloat() * r * 1.5f
        canvas.drawCircle(dx, dy, r * 0.4f, playerDirPaint)

        // Invincibility flash
        if (p.invincibleFrames > 0 && p.invincibleFrames % 6 < 3) {
            val shieldPaint = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 3f
                color = Color.argb(100, 255, 255, 255)
            }
            canvas.drawCircle(x, y, r + 4f, shieldPaint)
        }
    }

    private fun drawMonsters(canvas: Canvas) {
        for (m in world.monsters) {
            if (!m.isAlive) continue
            val paint = when (m.type) {
                MapData.MonsterType.SHADOW -> shadowPaint
                MapData.MonsterType.SCREAMER -> screamerPaint
                MapData.MonsterType.WRAITH -> wraithPaint
                MapData.MonsterType.BOSS -> bossPaint
            }
            val r = m.size / 2f

            // Monster body
            canvas.drawCircle(m.x, m.y, r, paint)

            // Eyes (glowing)
            val eyePaint = Paint().apply {
                color = when (m.type) {
                    MapData.MonsterType.BOSS -> Color.rgb(255, 255, 0)
                    MapData.MonsterType.SCREAMER -> Color.rgb(255, 100, 0)
                    else -> Color.rgb(200, 200, 255)
                }
            }
            val dx = m.x - world.player.x; val dy = m.y - world.player.y
            val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
            val ex = m.x + cos(angle.toDouble()).toFloat() * r * 0.5f
            val ey = m.y + sin(angle.toDouble()).toFloat() * r * 0.5f
            canvas.drawCircle(ex - 4f, ey - 3f, r * 0.25f, eyePaint)
            canvas.drawCircle(ex + 4f, ey - 3f, r * 0.25f, eyePaint)

            // Health bar for damaged monsters
            if (m.health < m.maxHealth) {
                val barW = m.size; val barH = 4f
                val barX = m.x - barW/2; val barY = m.y - r - 8f
                canvas.drawRect(barX, barY, barX + barW, barY + barH, Paint().apply { color = Color.RED })
                canvas.drawRect(barX, barY, barX + barW * (m.health.toFloat() / m.maxHealth), barY + barH,
                    Paint().apply { color = Color.GREEN })
            }
        }
    }

    private fun drawItems(canvas: Canvas) {
        for (item in world.worldItems) {
            if (item.collected) continue
            val x = item.x; val y = item.y
            val paint = when (item.type) {
                MapData.ItemType.MEDKIT, MapData.ItemType.ADRENALINE -> medkitPaint
                MapData.ItemType.AMMO -> ammoPaint
                MapData.ItemType.KEY_HOSPITAL, MapData.ItemType.KEY_CHURCH, MapData.ItemType.KEY_BUNKER -> keyPaint
                else -> genericItemPaint
            }

            // Pulsing effect
            val pulse = sin(world.gameTime * 0.1).toFloat() * 3f + 10f
            canvas.drawRect(x - pulse/2, y - pulse/2, x + pulse/2, y + pulse/2, paint)
        }
    }

    private fun drawDarkness(canvas: Canvas) {
        val p = world.player
        val hasLight = p.hasFlashlight && p.flashlightBattery > 0
        val lightRadius = if (hasLight) 140f else 60f
        val alpha = if (hasLight) 160 else 220

        // Create darkness with light circle around player
        val darknessBitmap = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
        val darkCanvas = Canvas(darknessBitmap)
        darkCanvas.drawColor(Color.argb(alpha, 0, 0, 0))

        // Cut out light circle
        val lightPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }
        val screenPX = p.x - camX
        val screenPY = p.y - camY
        darkCanvas.drawCircle(screenPX, screenPY, lightRadius, lightPaint)

        // Flicker effect for flashlight
        if (hasLight && p.flashlightBattery < 30 && world.gameTime % 20 < 3) {
            darkCanvas.drawColor(Color.argb(60, 0, 0, 0))
        }

        canvas.drawBitmap(darknessBitmap, 0f, 0f, null)
        darknessBitmap.recycle()
    }

    // ==================== HUD ====================

    private fun drawHUD(canvas: Canvas) {
        // Health bar - top left
        val barX = 20f; val barY = 20f; val barW = 150f; val barH = 18f
        canvas.drawRoundRect(barX-2, barY-2, barX+barW+2, barY+barH+2, 6f, 6f, Paint().apply { color = Color.argb(150, 0, 0, 0) })
        canvas.drawRoundRect(barX, barY, barX+barW, barY+barH, 6f, 6f, Paint().apply { color = Color.rgb(80, 20, 20) })
        val hpW = barW * (world.player.health.toFloat() / world.player.maxHealth)
        val hpColor = when { world.player.health > 60 -> Color.rgb(60, 200, 60); world.player.health > 25 -> Color.rgb(220, 180, 20); else -> Color.rgb(220, 20, 20) }
        canvas.drawRoundRect(barX, barY, barX+hpW, barY+barH, 6f, 6f, Paint().apply { color = hpColor })
        canvas.drawText("HP ${world.player.health}%", barX+4, barY+13, Paint().apply { color = Color.WHITE; textSize = 12f; typeface = Typeface.MONOSPACE })

        // Sanity bar
        val sBarY = barY + barH + 8f
        canvas.drawRoundRect(barX-2, sBarY-2, barX+barW+2, sBarY+barH+2, 6f, 6f, Paint().apply { color = Color.argb(150, 0, 0, 0) })
        canvas.drawRoundRect(barX, sBarY, barX+barW, sBarY+barH, 6f, 6f, Paint().apply { color = Color.rgb(20, 20, 80) })
        val spW = barW * (world.player.sanity.toFloat() / world.player.maxSanity)
        canvas.drawRoundRect(barX, sBarY, barX+spW, sBarY+barH, 6f, 6f, Paint().apply { color = Color.rgb(100, 100, 220) })
        canvas.drawText("SAN ${world.player.sanity}%", barX+4, sBarY+13, Paint().apply { color = Color.WHITE; textSize = 12f; typeface = Typeface.MONOSPACE })

        // Zone name - top center
        val zonePaint = Paint().apply { color = Color.argb(180, 200, 200, 200); textSize = 22f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
        canvas.drawText(world.currentZone.name, screenW/2f, 40f, zonePaint)

        // Bottom right: weapon & ammo
        if (world.player.hasRevolver) {
            val weaponText = "🔫 x${world.player.ammo}"
            canvas.drawText(weaponText, screenW - 140f, screenH - 30f,
                Paint().apply { color = Color.WHITE; textSize = 22f; typeface = Typeface.MONOSPACE })
        }

        // Flashlight indicator
        if (world.player.hasFlashlight) {
            val battText = if (world.player.flashlightBattery > 0) "🔦 ${world.player.flashlightBattery}%" else "🔦 0%"
            val battPaint = Paint().apply {
                color = if (world.player.flashlightBattery > 20) Color.WHITE else Color.rgb(255, 200, 50)
                textSize = 22f; typeface = Typeface.MONOSPACE
            }
            canvas.drawText(battText, screenW - 140f, screenH - 60f, battPaint)
        }

        // Keys indicator
        if (world.player.keys.isNotEmpty()) {
            canvas.drawText("🔑 ${world.player.keys.size}", 20f, screenH - 30f,
                Paint().apply { color = Color.rgb(255, 215, 0); textSize = 22f; typeface = Typeface.MONOSPACE })
        }
    }

    private fun drawMessages(canvas: Canvas) {
        var msgY = screenH / 2f + 40f
        for (msg in world.messages) {
            val paint = if (msg.isDanger) msgDangerPaint else msgPaint
            paint.alpha = (msg.timer.coerceAtMost(60) * 255 / 60).coerceIn(0, 255)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(msg.text, screenW / 2f, msgY, paint)
            msgY += 32f
        }
    }

    private fun drawJoystick(canvas: Canvas) {
        if (!joystickActive) {
            // Draw hint
            val hintPaint = Paint().apply { color = Color.argb(60, 255, 255, 255); textSize = 16f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
            canvas.drawText("ДЖОЙСТИК", screenW * 0.25f, screenH - 20f, hintPaint)
            canvas.drawText("АТАКА", screenW * 0.75f, screenH - 20f, hintPaint)
            return
        }

        val basePaint = Paint().apply { color = Color.argb(100, 200, 200, 200); style = Paint.Style.STROKE; strokeWidth = 3f }
        val stickPaint = Paint().apply { color = Color.argb(150, 255, 255, 255) }

        canvas.drawCircle(joystickBaseX, joystickBaseY, 80f, basePaint)
        canvas.drawCircle(joystickBaseX + joystickDx, joystickBaseY + joystickDy, 30f, stickPaint)
    }

    private fun drawGameOver(canvas: Canvas) {
        val overlayPaint = Paint().apply { color = Color.argb(180, 0, 0, 0) }
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)

        val titlePaint = Paint().apply {
            color = if (world.isVictory) Color.rgb(50, 220, 50) else Color.rgb(220, 30, 30)
            textSize = 56f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
        }
        canvas.drawText(world.gameOverMessage, screenW / 2f, screenH / 2f, titlePaint)

        val restartPaint = Paint().apply { color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
        canvas.drawText("Нажмите НАЗАД для выхода", screenW / 2f, screenH / 2f + 50f, restartPaint)
    }
}
