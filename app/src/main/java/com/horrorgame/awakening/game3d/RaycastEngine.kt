package com.horrorgame.awakening.game3d

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

/**
 * Full-screen first-person 3D horror game using raycasting (Wolfenstein-style).
 * Touch: left=virtual joystick(move), right=swipe(rotate)+tap(shoot).
 */
class RaycastEngine(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: Thread? = null
    private var running = false

    // Player state
    private var px = 2.5; private var py = 1.5
    private var pangle = 0.0 // radians, 0 = looking right (east)
    private var hp = 100; private var sanity = 100
    private var ammo = 6; private var flashlight = true; private var battery = 100f
    private var attackCooldown = 0; private var invFrames = 0
    private var currentMap: RCMapData.RCMap = RCMapData.MAP_HOSPITAL
    private var gameOver = false; private var victory = false
    private var msgText = ""; private var msgTimer = 0

    // Sprites: triples of (x, y, type, alive, health)
    // type: 0=medkit, 1=ammo, 2=shadow, 3=screamer, 4=boss
    private val sprites = mutableListOf<Sprite>()
    data class Sprite(var x: Double, var y: Double, val type: Int, var alive: Boolean = true, var health: Int = 3, var stateTime: Int = 0, var patX: Double = 0.0, var patY: Double = 0.0, var chase: Boolean = false)

    // Touch
    private var jActive = false; private var jBaseX = 0f; private var jBaseY = 0f; private var jDx = 0f; private var jDy = 0f
    private var jPtrId = -1; private var rotateActive = false; private var rotPtrId = -1; private var rotLastX = 0f
    private var shootPressed = false; private var screenW = 1; private var screenH = 1
    private var elapsed = 0L

    // Pre-allocated
    private val wallPaint = Paint()
    private val texPaint = Paint()

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(h: SurfaceHolder) { startLoop() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) { screenW = w; screenH = h2 }
    override fun surfaceDestroyed(h: SurfaceHolder) { stopLoop() }

    private fun startLoop() {
        running = true
        spawnSprites()
        thread = Thread {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000f).coerceAtMost(50f); last = now
                elapsed++
                update(dt)
                render()
                try { Thread.sleep(16) } catch (_: InterruptedException) { break }
            }
        }; thread?.start()
    }

    private fun stopLoop() { running = false; thread?.interrupt(); thread = null }

    private fun spawnSprites() {
        sprites.clear()
        // Items
        sprites.add(Sprite(5.0, 3.0, 0)); sprites.add(Sprite(20.0, 3.0, 0))  // medkits
        sprites.add(Sprite(8.0, 10.0, 1)); sprites.add(Sprite(28.0, 25.0, 1)) // ammo
        sprites.add(Sprite(25.0, 9.0, 1))
        // Monsters
        sprites.add(Sprite(6.0, 15.0, 2, true, 2))   // shadow
        sprites.add(Sprite(22.0, 13.0, 2, true, 2))  // shadow
        sprites.add(Sprite(14.0, 8.0, 3, true, 1))   // screamer
        sprites.add(Sprite(28.0, 17.0, 3, true, 1))  // screamer
        // Boss in basement
        sprites.add(Sprite(15.0, 13.0, 4, true, 6))  // boss
    }

    // ==================== UPDATE ====================

    private fun update(dt: Float) {
        if (gameOver) return
        if (invFrames > 0) invFrames--
        if (attackCooldown > 0) attackCooldown--
        if (msgTimer > 0) msgTimer--

        // Movement from joystick
        if (jActive) {
            val dx = jDx / 80f; val dy = jDy / 80f
            val len = sqrt(dx*dx + dy*dy).toFloat().coerceAtMost(1f)
            if (len > 0.1f) {
                val nx = dx / len; val ny = dy / len
                val speed = 0.06
                val mx = cos(pangle) * nx.toDouble() * speed - sin(pangle) * ny.toDouble() * speed
                val my = sin(pangle) * nx.toDouble() * speed + cos(pangle) * ny.toDouble() * speed
                val newX = px + mx; val newY = py + my
                if (currentMap.grid[(newY).toInt()][(newX).toInt()] != 1) px = newX
                if (currentMap.grid[(py).toInt()][(newY).toInt()] != 1) py = newY
                // Battery drain
                if (flashlight) { battery -= 0.05f; if (battery <= 0) { flashlight = false; msg("Батарейки сели!", 90) } }
            }
        }

        // Sanity drain in dark
        if (elapsed % 180L == 0L && !flashlight) { sanity -= 1; if (sanity < 30) msg("Тьма сводит с ума...", 60) }

        // Sprite AI
        for (s in sprites) {
            if (!s.alive || s.type < 2) continue
            val sdx = px - s.x; val sdy = py - s.y
            val dist = sqrt(sdx*sdx + sdy*sdy)
            val spd = when(s.type) { 2->0.015; 3->0.04; 4->0.025; else->0.0 }

            if (dist < 0.4 && s.stateTime <= 0) {
                hp -= when(s.type) { 2->6; 3->12; 4->20; else->0 }
                sanity -= when(s.type) { 2->2; 3->6; 4->10; else->0 }
                invFrames = 40
                msg("-${when(s.type){2->6;3->12;4->20;else->0}} HP!", 60)
                s.stateTime = 60
                if (hp <= 0) { gameOver = true; victory = false; msg("ВЫ ПОГИБЛИ", 999) }
            }
            if (s.stateTime > 0) { s.stateTime--; continue }

            if (dist < 5.0) {
                // Chase
                if (dist > 0.2) { s.x += (sdx/dist) * spd; s.y += (sdy/dist) * spd }
                s.chase = true
            } else if (s.chase) {
                // Return to patrol
                s.chase = false
            } else {
                // Patrol
                if (s.patX == 0.0) { s.patX = s.x; s.patY = s.y }
                if (elapsed % 120L == 0L) {
                    s.x += (Math.random() - 0.5) * 2; s.y += (Math.random() - 0.5) * 2
                    s.x = s.x.coerceIn(1.0, (currentMap.w-2).toDouble())
                    s.y = s.y.coerceIn(1.0, (currentMap.h-2).toDouble())
                }
            }

            // Don't let monsters walk into walls
            val gx = s.x.toInt().coerceIn(0, currentMap.w-1)
            val gy = s.y.toInt().coerceIn(0, currentMap.h-1)
            if (currentMap.grid[gy][gx] == 1) { s.x = s.patX; s.y = s.patY }
            s.patX = s.x; s.patY = s.y
        }

        // Check exit
        val cx = px.toInt(); val cy = py.toInt()
        if (currentMap.grid[cy][cx] == 3) {
            currentMap.exits[cx to cy]?.let { exit ->
                val nextMap = RCMapData.maps[exit.mapId] ?: return@let
                currentMap = nextMap; px = exit.toX; py = exit.toY
                spawnSprites(); msg(currentMap.name, 120)
            }
        }

        // Shoot
        if (shootPressed && attackCooldown <= 0 && ammo > 0) {
            ammo--; attackCooldown = 20
            // Hit closest sprite in line of sight
            var best: Sprite? = null; var bestDist = 6.0
            for (s in sprites) {
                if (!s.alive || s.type < 2) continue
                val sdx = s.x - px; val sdy = s.y - py
                val dist = sqrt(sdx*sdx + sdy*sdy)
                val angle = atan2(sdy, sdx)
                var diff = abs(angle - pangle)
                if (diff > PI) diff = 2*PI - diff
                if (diff < 0.3 && dist < bestDist) { best = s; bestDist = dist }
            }
            if (best != null) {
                best!!.health--
                if (best!!.health <= 0) {
                    best!!.alive = false
                    if (best!!.type == 4) { gameOver = true; victory = true; msg("БОСС УНИЧТОЖЕН!\nВЫ ПОБЕДИЛИ!", 999) }
                    else msg("Убит!", 80)
                } else { msg("Попадание!", 40) }
            } else { msg("Мимо!", 40) }
        }
        shootPressed = false

        // Pickup items
        val it = sprites.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (!s.alive) continue
            if (s.type >= 2) continue // not an item
            val dist = sqrt((px-s.x)*(px-s.x) + (py-s.y)*(py-s.y))
            if (dist < 0.5) {
                when (s.type) {
                    0 -> { hp = (hp + 30).coerceAtMost(100); msg("+30 HP", 60) }
                    1 -> { ammo += 3; msg("+3 патрона", 60) }
                }
                it.remove()
            }
        }
    }

    private fun msg(text: String, dur: Int) { msgText = text; msgTimer = dur }

    // ==================== RENDER ====================

    private fun render() {
        val canvas = holder.lockCanvas() ?: return
        try {
            val W = screenW.toDouble(); val H = screenH.toDouble()
            val hH = H / 2.0; val hW = W / 2.0
            val map = currentMap

            // Ceiling
            val ceilPaint = Paint().apply { color = Color.rgb(5, 3, 10) }
            canvas.drawRect(0f, 0f, screenW.toFloat(), hH.toFloat(), ceilPaint)

            // Ceiling gradient
            val ceilGrad = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, hH.toFloat(),
                    Color.rgb(3, 1, 5), Color.rgb(8, 5, 15), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, screenW.toFloat(), hH.toFloat(), ceilGrad)

            // Floor
            canvas.drawRect(0f, hH.toFloat(), screenW.toFloat(), screenH.toFloat(), Paint().apply { color = Color.rgb(15, 10, 8) })

            // Raycasting
            val numRays = screenW / 2
            val fov = PI / 3.0 // 60 degrees
            val rayStep = fov / numRays

            // Collect sprite projections for later drawing
            val spriteProj = mutableListOf<Triple<Double, Int, Sprite>>() // distance, screenX, sprite

            for (i in 0 until numRays) {
                val rayAngle = pangle - fov/2 + i * rayStep
                val sinA = sin(rayAngle); val cosA = cos(rayAngle)

                // DDA raycasting
                var rx = px; var ry = py; var hit = false; var side = 0; var dist = 0.0
                var wallType = 1
                for (step in 0..60) {
                    rx += cosA * 0.1; ry += sinA * 0.1
                    val mx = rx.toInt().coerceIn(0, map.w-1)
                    val my = ry.toInt().coerceIn(0, map.h-1)
                    val cell = map.grid[my][mx]
                    if (cell == 1 || cell == 2) { wallType = cell; hit = true
                        dist = sqrt((rx-px)*(rx-px) + (ry-py)*(ry-py))
                        // Determine which side was hit
                        val cx = (rx / 1.0) % 1.0; val cy = (ry / 1.0) % 1.0
                        side = if (abs(cx-0.5) < abs(cy-0.5)) 0 else 1
                        break
                    }
                }
                if (!hit) { dist = 10.0; wallType = 1 }

                // Fish-eye correction
                val corrDist = dist * cos(rayAngle - pangle)
                if (corrDist < 0.2) continue

                // Wall height on screen
                val wallHeight = (H / corrDist).coerceAtMost(H * 2)
                val wallTop = hH - wallHeight / 2
                val wallBottom = hH + wallHeight / 2

                // Wall color with distance shading
                val shade = (1.0 / (1.0 + corrDist * corrDist * 0.3)).coerceIn(0.05, 1.0)
                val baseColor = when {
                    wallType == 2 -> intArrayOf(80, 40, 20) // Door brown
                    side == 1 -> intArrayOf(50, 45, 55)     // N/S wall
                    else -> intArrayOf(70, 60, 65)           // E/W wall (brighter)
                }
                val r = (baseColor[0] * shade).toInt(); val g = (baseColor[1] * shade).toInt(); val b = (baseColor[2] * shade).toInt()
                wallPaint.color = Color.rgb(r, g, b)

                // Darken distant walls for horror
                val darkShade = if (corrDist > 4) (corrDist - 4) * 20 else 0

                // Draw wall slice
                val x = i * 2f
                canvas.drawLine(x, wallTop.toFloat().coerceAtLeast(0f) + darkShade.toFloat(),
                    x + 2f, wallBottom.toFloat().coerceAtMost(screenH.toFloat()), wallPaint)

                // Wall edge lines for detail
                if (i % 8 == 0) {
                    val edgePaint = Paint().apply { color = Color.argb((shade*60).toInt(), 0,0,0) }
                    canvas.drawLine(x, wallTop.toFloat(), x+2f, wallTop.toFloat(), edgePaint)
                }

                // Floor/crack texture on close walls
                if (corrDist < 2.5 && abs(wallTop) < screenH) {
                    val flrPaint = Paint().apply { color = Color.rgb((30*shade).toInt(), (25*shade).toInt(), (22*shade).toInt()) }
                    canvas.drawRect(x, wallBottom.toFloat().coerceAtMost(screenH.toFloat()),
                        x+2f, screenH.toFloat(), flrPaint)
                }
            }

            // Draw sprites (items & monsters)
            for (s in sprites) {
                if (!s.alive) continue
                val sdx = s.x - px; val sdy = s.y - py
                val sdist = sqrt(sdx*sdx + sdy*sdy)
                if (sdist < 0.3) continue

                // Angle from player to sprite
                val sAngle = atan2(sdy, sdx)
                var angleDiff = sAngle - pangle
                // Normalize to [-PI, PI]
                while (angleDiff > PI) angleDiff -= 2*PI
                while (angleDiff < -PI) angleDiff += 2*PI

                // Is sprite in FOV?
                if (abs(angleDiff) > fov/2 + 0.2) continue

                // Screen X position
                val screenX = hW + (angleDiff / (fov/2)) * hW
                if (screenX < -50 || screenX > W + 50) continue

                // Size based on distance
                val size = (H / sdist * 0.5).coerceAtMost(H * 2).coerceAtLeast(15.0)

                spriteProj.add(Triple(sdist, screenX.toInt(), s))
            }

            // Sort sprites far to near (draw far ones first)
            spriteProj.sortByDescending { it.first }

            for ((dist, screenX, s) in spriteProj) {
                val size = (H / dist * 0.5).coerceAtMost(H * 2).coerceAtLeast(15.0)
                val top = (hH - size/2).toFloat(); val left = (screenX - size/2).toFloat()

                when {
                    s.type == 0 -> { // Medkit
                        canvas.drawRect(left, top, left+size.toFloat(), top+size.toFloat(), Paint().apply { color = Color.rgb(200,40,40) })
                        canvas.drawRect(left+size.toFloat()*0.3f, top, left+size.toFloat()*0.7f, top+size.toFloat(), Paint().apply { color = Color.WHITE })
                    }
                    s.type == 1 -> { // Ammo
                        canvas.drawCircle(left+size.toFloat()/2, top+size.toFloat()/2, size.toFloat()/2, Paint().apply { color = Color.rgb(180,160,40) })
                    }
                    s.type == 2 -> { // Shadow monster
                        val dark = (1.0/(1.0+dist*0.2)).coerceIn(0.1, 1.0)
                        canvas.drawOval(left, top, left+size.toFloat(), top+size.toFloat(), Paint().apply { color = Color.argb(200, (30*dark).toInt(), (30*dark).toInt(), (30*dark).toInt()) })
                        // Glowing eyes
                        val ex = left+size.toFloat()*0.35f; val ey = top+size.toFloat()*0.35f
                        canvas.drawCircle(ex, ey, size.toFloat()*0.12f, Paint().apply { color = Color.rgb(180,180,255) })
                        canvas.drawCircle(left+size.toFloat()-ex+left, ey, size.toFloat()*0.12f, Paint().apply { color = Color.rgb(180,180,255) })
                    }
                    s.type == 3 -> { // Screamer
                        val dark = (1.0/(1.0+dist*0.2)).coerceIn(0.1, 1.0)
                        canvas.drawRect(left, top, left+size.toFloat(), top+size.toFloat(), Paint().apply { color = Color.argb(220, (120*dark).toInt(), (20*dark).toInt(), (20*dark).toInt()) })
                        // Jagged teeth
                        val teethPaint = Paint().apply { color = Color.WHITE; strokeWidth = 2f }
                        for (tx in 0..3) { canvas.drawLine(left+size.toFloat()*0.2f+tx*size.toFloat()*0.15f, top+size.toFloat()*0.5f, left+size.toFloat()*0.25f+tx*size.toFloat()*0.15f, top+size.toFloat()*0.8f, teethPaint) }
                    }
                    s.type == 4 -> { // Boss
                        val dark = (1.0/(1.0+dist*0.15)).coerceIn(0.1, 1.0)
                        canvas.drawOval(left-10, top-10, left+size.toFloat()+10, top+size.toFloat()+10, Paint().apply { color = Color.argb(230, (180*dark).toInt(), (10*dark).toInt(), (10*dark).toInt()) })
                        // Glowing yellow eyes
                        canvas.drawCircle(left+size.toFloat()*0.3f, top+size.toFloat()*0.35f, size.toFloat()*0.15f, Paint().apply { color = Color.YELLOW })
                        canvas.drawCircle(left+size.toFloat()*0.7f, top+size.toFloat()*0.35f, size.toFloat()*0.15f, Paint().apply { color = Color.YELLOW })
                        // Health bar
                        val barW = size; val barH = 5f
                        canvas.drawRect(left, top-12f, left+barW.toFloat(), top-7f, Paint().apply { color = Color.RED })
                        canvas.drawRect(left, top-12f, left+barW.toFloat()*(s.health/6f), top-7f, Paint().apply { color = Color.GREEN })
                    }
                }
            }

            // Flashlight overlay
            if (!flashlight || battery <= 0) {
                // Full darkness with small circle
                val darkBmp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
                val dc = Canvas(darkBmp)
                dc.drawColor(Color.argb(200, 0, 0, 0))
                val lp = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); isAntiAlias = true }
                dc.drawCircle(screenW/2f, screenH/2f, 70f, lp)
                canvas.drawBitmap(darkBmp, 0f, 0f, null); darkBmp.recycle()
            } else if (battery < 30) {
                // Flicker
                val flickAlpha = if (elapsed % 15 < 2) 80 else 40
                canvas.drawColor(Color.argb(flickAlpha, 0, 0, 0))
            }

            // Flashlight beam effect
            if (flashlight && battery > 0) {
                val beamGrad = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, screenH/2f,
                        Color.argb(0, 255, 240, 200),
                        Color.argb(15, 255, 240, 200),
                        Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, screenW.toFloat(), screenH/2f, beamGrad)
            }

            // Vignette
            val vigBmp = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            val vc = Canvas(vigBmp)
            vc.drawColor(Color.TRANSPARENT)
            val vigPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = (screenW*0.35f) }
            vigPaint.color = Color.argb(100, 0, 0, 0)
            vc.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), vigPaint)
            canvas.drawBitmap(vigBmp, 0f, 0f, null); vigBmp.recycle()

            // Damage flash
            if (invFrames > 0 && invFrames % 6 < 3) {
                canvas.drawColor(Color.argb(40, 255, 30, 30))
            }

            // ===== HUD =====
            drawHUD(canvas, W, H)

            // Message
            if (msgTimer > 0) {
                val mp = Paint().apply { color = Color.WHITE; textSize = 44f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
                val alpha = (msgTimer.coerceAtMost(60) * 255 / 60).coerceIn(0, 255)
                mp.alpha = alpha
                val lines = msgText.split("\n")
                lines.forEachIndexed { idx, line ->
                    canvas.drawText(line, screenW/2f, screenH*0.35f + idx*50f, mp)
                }
            }

            // Game over screen
            if (gameOver) {
                canvas.drawColor(Color.argb(190, 0, 0, 0))
                val tp = Paint().apply {
                    color = if (victory) Color.rgb(50, 220, 50) else Color.rgb(220, 30, 30)
                    textSize = 56f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
                }
                canvas.drawText(msgText, screenW/2f, screenH/2f, tp)
                val rp = Paint().apply { color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.CENTER }
                canvas.drawText("Назад — выход", screenW/2f, screenH/2f+60f, rp)
            }
        } finally { holder.unlockCanvasAndPost(canvas) }
    }

    private fun drawHUD(canvas: Canvas, W: Double, H: Double) {
        // Crosshair
        val chPaint = Paint().apply { color = Color.argb(120, 255, 255, 255); strokeWidth = 2f; style = Paint.Style.STROKE }
        canvas.drawCircle(screenW/2f, screenH/2f, 12f, chPaint)
        canvas.drawLine(screenW/2f-20f, screenH/2f, screenW/2f+20f, screenH/2f, chPaint)
        canvas.drawLine(screenW/2f, screenH/2f-20f, screenW/2f, screenH/2f+20f, chPaint)

        // HP bar (bottom-left)
        val bw = 180f; val bh = 16f; val bx = 20f; val by = screenH - 50f
        canvas.drawRoundRect(bx-2, by-2, bx+bw+2, by+bh+2, 6f, 6f, Paint().apply { color = Color.argb(150,0,0,0) })
        canvas.drawRoundRect(bx, by, bx+bw, by+bh, 6f, 6f, Paint().apply { color = Color.rgb(60,15,15) })
        val hw = bw * (hp/100f)
        val hc = if(hp>60) Color.rgb(60,200,60) else if(hp>25) Color.rgb(220,180,20) else Color.rgb(220,20,20)
        canvas.drawRoundRect(bx, by, bx+hw, by+bh, 6f, 6f, Paint().apply { color = hc })
        canvas.drawText("HP $hp%", bx+4, by+12, Paint().apply { color=Color.WHITE; textSize=13f; typeface=Typeface.MONOSPACE })

        // Sanity bar
        val sby = by - bh - 4f
        canvas.drawRoundRect(bx-2, sby-2, bx+bw+2, sby+bh+2, 6f, 6f, Paint().apply { color = Color.argb(150,0,0,0) })
        canvas.drawRoundRect(bx, sby, bx+bw, sby+bh, 6f, 6f, Paint().apply { color = Color.rgb(15,15,60) })
        val sw = bw * (sanity/100f)
        canvas.drawRoundRect(bx, sby, bx+sw, sby+bh, 6f, 6f, Paint().apply { color = Color.rgb(100,100,220) })
        canvas.drawText("SAN $sanity%", bx+4, sby+12, Paint().apply { color=Color.WHITE; textSize=13f; typeface=Typeface.MONOSPACE })

        // Ammo (bottom-right)
        canvas.drawText("🔫 $ammo", screenW-120f, screenH-30f, Paint().apply { color=Color.WHITE; textSize=24f; typeface=Typeface.MONOSPACE })
        if (flashlight) {
            canvas.drawText("🔦 ${battery.toInt()}%", screenW-120f, screenH-60f,
                Paint().apply { color=if(battery>30)Color.WHITE else Color.rgb(255,200,50); textSize=22f; typeface=Typeface.MONOSPACE })
        }

        // Minimap (top-right)
        val mmSize = 80f; val mmX = screenW - mmSize - 10f; val mmY = 10f
        canvas.drawRect(mmX-2, mmY-2, mmX+mmSize+2, mmY+mmSize+2, Paint().apply { color=Color.argb(120,0,0,0) })
        val cellW = mmSize / currentMap.w; val cellH = mmSize / currentMap.h
        for (cy in 0 until currentMap.h) {
            for (cx in 0 until currentMap.w) {
                if (currentMap.grid[cy][cx] == 1) {
                    canvas.drawRect(mmX+cx*cellW, mmY+cy*cellH, mmX+(cx+1)*cellW, mmY+(cy+1)*cellH,
                        Paint().apply { color = Color.rgb(60,60,70) })
                }
            }
        }
        // Player dot on minimap
        val pdx = (mmX + (px/currentMap.w)*mmSize).toFloat(); val pdy = (mmY + (py/currentMap.h)*mmSize).toFloat()
        canvas.drawCircle(pdx, pdy, 2.5f, Paint().apply { color = Color.GREEN })
        // Player direction
        canvas.drawLine(pdx, pdy, pdx+cos(pangle).toFloat()*5f, pdy+sin(pangle).toFloat()*5f,
            Paint().apply { color=Color.GREEN; strokeWidth=1.5f })

        // Zone name
        canvas.drawText(currentMap.name, screenW/2f, 30f,
            Paint().apply { color=Color.argb(160,200,200,200); textSize=18f; textAlign=Paint.Align.CENTER; typeface=Typeface.MONOSPACE })

        // Touch hints
        val hintPaint = Paint().apply { color=Color.argb(60,255,255,255); textSize=14f; typeface=Typeface.MONOSPACE; textAlign=Paint.Align.CENTER }
        canvas.drawText("ПЕРЕДВИЖЕНИЕ", screenW*0.25f, screenH-8f, hintPaint)
        canvas.drawText("ПОВОРОТ / ВЫСТРЕЛ", screenW*0.75f, screenH-8f, hintPaint)
    }

    // ==================== TOUCH ====================

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex; val pid = e.getPointerId(idx)
                val x = e.getX(idx); val y = e.getY(idx)
                if (x < screenW * 0.4f) {
                    // Left side = joystick
                    jActive = true; jPtrId = pid; jBaseX = x; jBaseY = y
                } else if (x < screenW * 0.8f) {
                    // Center-right = rotate
                    rotateActive = true; rotPtrId = pid; rotLastX = x
                } else {
                    // Far right = shoot
                    shootPressed = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i); val x = e.getX(i); val y = e.getY(i)
                    when (pid) {
                        jPtrId -> { jDx = x - jBaseX; jDy = y - jBaseY }
                        rotPtrId -> {
                            val dx = (x - rotLastX) * 0.01; rotLastX = x
                            pangle += dx; if (pangle < 0) pangle += 2*PI; if (pangle > 2*PI) pangle -= 2*PI
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pid = e.getPointerId(e.actionIndex)
                if (pid == jPtrId) { jActive = false; jDx = 0f; jDy = 0f }
                if (pid == rotPtrId) { rotateActive = false }
                if (e.actionMasked == MotionEvent.ACTION_UP) shootPressed = false
            }
        }
        return true
    }
}
