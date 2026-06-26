package com.horrorgame.awakening.game3d

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

/**
 * Full-screen FPS horror — raycasting with procedural textures,
 * animated weapon, particles, and atmosphere.
 * Left=joystick  Mid=rotate  Right=shoot
 */
class RaycastEngine(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var t: Thread? = null; private var run = false
    private var sw = 1; private var sh = 1

    // Player
    private var px = 2.5; private var py = 1.5; private var pa = 0.0
    private var hp = 100; private var san = 100; private var ammo = 12
    private var fl = true; private var bat = 100f; private var atkCd = 0; private var iframe = 0
    private var map = RCMapData.MAP_HOSPITAL
    private var over = false; private var win = false; private var msg = ""; private var mt = 0
    private var bob = 0f; private var bobSpd = 0f
    private var shakeT = 0; private var flashA = 0f

    // Sprites
    private val spr = mutableListOf<Sp>()
    data class Sp(var x: Double, var y: Double, val tp: Int, var al: Boolean = true,
                   var hp: Int = 3, var st: Int = 0, var ch: Boolean = false)

    // Particles
    private val parts = mutableListOf<Pt>()
    data class Pt(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, val col: Int)

    // Touch
    private var ja = false; private var jbx = 0f; private var jby = 0f; private var jdx = 0f; private var jdy = 0f; private var jpi = -1
    private var ra = false; private var rpi = -1; private var rlx = 0f
    private var shoot = false

    // Vibration
    private val vib: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // Paints cache
    private val cp = Paint(); private val tp = Paint()

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(h: SurfaceHolder) { start() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) { sw = w; sh = h2 }
    override fun surfaceDestroyed(h: SurfaceHolder) { stop() }

    private fun start() {
        run = true; spawn()
        t = Thread {
            var lt = System.nanoTime()
            while (run) {
                val nt = System.nanoTime(); val dt = ((nt - lt) / 1_000_000f).coerceAtMost(50f); lt = nt
                tick(dt); draw()
                try { Thread.sleep(16) } catch (_: InterruptedException) { break }
            }
        }; t?.start()
    }
    private fun stop() { run = false; t?.interrupt(); t = null }

    private fun spawn() {
        spr.clear()
        spr.add(Sp(5.0,3.0,0)); spr.add(Sp(22.0,4.0,0)); spr.add(Sp(29.0,20.0,0)) // medkit
        spr.add(Sp(8.0,10.0,1)); spr.add(Sp(27.0,8.0,1)); spr.add(Sp(15.0,27.0,1)) // ammo
        spr.add(Sp(6.0,16.0,2,hp=2)); spr.add(Sp(25.0,14.0,2,hp=2)); spr.add(Sp(18.0,5.0,2,hp=2))
        spr.add(Sp(12.0,22.0,3,hp=1)); spr.add(Sp(28.0,25.0,3,hp=1))
        spr.add(Sp(15.0,13.0,4,hp=8)) // boss
    }

    // ================ TICK ================
    private fun tick(dt: Float) {
        if (over) return
        if (iframe > 0) iframe--; if (atkCd > 0) atkCd--; if (mt > 0) mt--
        if (shakeT > 0) shakeT--
        if (flashA > 0) flashA = (flashA - 0.08f).coerceAtLeast(0f)

        // Movement
        if (ja) {
            val dx = jdx / 80f; val dy = jdy / 80f
            val l = sqrt(dx*dx + dy*dy).coerceAtMost(1f)
            if (l > 0.1f) {
                val spd = 0.07; bobSpd = l * 0.15f
                val fx = cos(pa)*dx/l - sin(pa)*dy/l; val fy = sin(pa)*dx/l + cos(pa)*dy/l
                val nx = px + fx*spd; val ny = py + fy*spd
                if (map.grid[py.toInt()][nx.toInt()] != 1) px = nx
                if (map.grid[ny.toInt()][px.toInt()] != 1) py = ny
                if (fl) { bat -= 0.04f; if (bat <= 0) { fl = false; msg("Батарейки сели!", 90) } }
            } else { bobSpd = 0f }
        }
        bob += bobSpd

        // Sanity
        if (!fl && System.nanoTime() % 180L == 0L) { san = (san-1).coerceAtLeast(0); if (san<25 && san>0) msg("Тьма давит...", 60) }

        // Monsters
        for (s in spr) { if (!s.al || s.tp < 2) continue
            val dx = px - s.x; val dy = py - s.y; val d = sqrt(dx*dx+dy*dy)
            val spd = when(s.tp) { 2->0.018; 3->0.045; 4->0.028; else->0.0 }
            if (d < 0.45 && s.st <= 0) {
                hp -= when(s.tp) { 2->7; 3->14; 4->22; else->0 }
                san -= when(s.tp) { 2->3; 3->7; 4->12; else->0 }
                iframe = 45; s.st = 70; shakeT = 12; flashA = 0.7f
                vib(40)
                msg("-${when(s.tp){2->7;3->14;4->22;else->0}} HP!", 60)
                if (hp <= 0) { over = true; msg("ВЫ ПОГИБЛИ", 999) }
            }
            if (s.st > 0) { s.st--; continue }
            if (d < 6.0 && d > 0.15) { s.x += (dx/d)*spd; s.y += (dy/d)*spd; s.ch = true }
            else if (s.ch) { s.ch = false }
            else if (d > 0.5 && System.nanoTime() % 100L == 0L) { s.x += (Math.random()-0.5)*0.8; s.y += (Math.random()-0.5)*0.8 }
            val gx = s.x.toInt().coerceIn(0,map.w-1); val gy = s.y.toInt().coerceIn(0,map.h-1)
            if (map.grid[gy][gx]==1) { s.x = (s.x - (dx/d)*spd*2).coerceIn(1.0,(map.w-2).toDouble()); s.y = (s.y - (dy/d)*spd*2).coerceIn(1.0,(map.h-2).toDouble()) }
        }

        // Exit
        map.exits[px.toInt() to py.toInt()]?.let { ex ->
            RCMapData.maps[ex.mapId]?.let { m ->
                map = m; px = ex.toX; py = ex.toY; spawn(); msg(map.name, 120)
            }
        }

        // Shoot
        if (shoot && atkCd <= 0 && ammo > 0) { ammo--; atkCd = 18
            vib(25)
            // Muzzle flash particles
            for (i in 0..4) parts.add(Pt(sw/2f, sh-150f, (Math.random()*4-2).toFloat(), (Math.random()*-6-2).toFloat(), 12, Color.rgb(255,200,50)))
            var best: Sp? = null; var bd = 7.0
            for (s in spr) { if (!s.al || s.tp < 2) continue
                val sdx = s.x-px; val sdy = s.y-py; val d = sqrt(sdx*sdx+sdy*sdy)
                val ang = atan2(sdy,sdx); var diff = abs(ang-pa); if (diff>PI) diff=2*PI-diff
                if (diff < 0.35 && d < bd) { best = s; bd = d } }
            if (best != null) { best!!.hp--
                if (best!!.hp<=0) { best!!.al=false; if (best!!.tp==4) { over=true; win=true; msg("БОСС УНИЧТОЖЕН!\nВЫ ПОБЕДИЛИ!",999) } else msg("Убит!",80) } else msg("Попадание!",40) } else msg("Мимо!",40)
        }; shoot = false

        // Pickup items
        val it = spr.iterator()
        while (it.hasNext()) { val s = it.next(); if (!s.al||s.tp>=2) continue
            if (sqrt((px-s.x)*(px-s.x)+(py-s.y)*(py-s.y)) < 0.5) {
                when(s.tp) { 0->{hp=(hp+30).coerceAtMost(100);msg("+30 HP",60)} 1->{ammo+=3;msg("+3 патрона",60)} }; it.remove() } }

        // Particles
        for (p in parts) { p.x+=p.vx; p.y+=p.vy; p.life-- }; parts.removeAll { it.life <= 0 }

        // Sanity death
        if (san <= 0) { over = true; win = false; msg("РАССУДОК УТРАЧЕН", 999) }
    }

    private fun msg(t: String, d: Int) { msg = t; mt = d }
    private fun vib(ms: Long) { vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }

    // ================ DRAW ================
    private fun draw() {
        val c = holder.lockCanvas() ?: return
        try {
            val W = sw.toDouble(); val H = sh.toDouble(); val hH = H/2; val hW = W/2
            val fov = PI/3.0; val nR = sw/2

            // Sky/ceiling with gradient
            val sg = Paint().apply { shader = LinearGradient(0f,0f,0f,hH.toFloat(), Color.rgb(3,1,8), Color.rgb(12,8,22), Shader.TileMode.CLAMP) }
            c.drawRect(0f,0f,sw.toFloat(),hH.toFloat(), sg)
            c.drawRect(0f,hH.toFloat(),sw.toFloat(),sh.toFloat(), Paint().apply { color = Color.rgb(18,12,10) })
            val fg = Paint().apply { shader = LinearGradient(0f,hH.toFloat(),0f,sh.toFloat(), Color.rgb(18,12,10), Color.rgb(6,4,3), Shader.TileMode.CLAMP) }
            c.drawRect(0f,hH.toFloat(),sw.toFloat(),sh.toFloat(), fg)

            // Raycast walls
            for (i in 0 until nR) {
                val ra = pa - fov/2 + i * (fov/nR)
                val sa = sin(ra); val ca = cos(ra)
                var rx = px; var ry = py; var d = 0.0; var side = 0; var wt = 1; var tx = 0.0
                for (_s in 0..70) { rx += ca*0.08; ry += sa*0.08
                    val mx = rx.toInt().coerceIn(0,map.w-1); val my = ry.toInt().coerceIn(0,map.h-1)
                    if (map.grid[my][mx] == 1 || map.grid[my][mx] == 2) {
                        wt = map.grid[my][mx]; d = sqrt((rx-px)*(rx-px)+(ry-py)*(ry-py))
                        val fx = rx%1.0; val fy = ry%1.0; side = if (abs(fx-0.5) < abs(fy-0.5)) 0 else 1
                        tx = if (side==0) ry%1.0 else rx%1.0; break } }
                if (d < 0.01) { d = 10.0; wt = 1 }

                val cd = d * cos(ra - pa)
                if (cd < 0.15) continue
                val wh = (H / cd).coerceAtMost(H*2.5)
                val wt2 = wh/2; val top = (hH - wt2).toFloat()
                val bot = (hH + wt2).toFloat().coerceAtMost(sh.toFloat())

                // Shading
                val shade = (1.0 / (1.0 + cd*cd*0.25)).coerceIn(0.04, 1.0)
                val texCol = tx.toFloat()
                val x = i*2f

                // Draw textured wall slice — brick pattern
                val bw = 2f; val bh = (wh / 16).toFloat().coerceAtLeast(2f).coerceAtMost(12f)
                var py2 = top
                var row = (top / bh).toInt()
                while (py2 < bot) {
                    val brickCol = ((row + (texCol*10).toInt()) % 2)
                    val bc = when {
                        wt == 2 -> if (brickCol==0) intArrayOf(100,60,35) else intArrayOf(80,45,25) // door brown
                        side == 1 -> if (brickCol==0) intArrayOf(70,65,60) else intArrayOf(55,50,46) // N/S
                        else -> if (brickCol==0) intArrayOf(85,78,72) else intArrayOf(65,58,53) // E/W
                    }
                    val r = (bc[0]*shade + (1-shade)*bc[0]*0.2).toInt()
                    val g = (bc[1]*shade + (1-shade)*bc[1]*0.2).toInt()
                    val bl = (bc[2]*shade + (1-shade)*bc[2]*0.2).toInt()
                    cp.color = Color.rgb(r, g, bl)

                    // Random blood stains
                    val blood = (cd < 3 && (px.toInt()+py.toInt()+row) % 7 == 0)
                    if (blood) cp.color = Color.rgb((r*0.4+60).toInt(), (g*0.2).toInt(), (bl*0.2).toInt())

                    val nextY = (py2 + bh).coerceAtMost(bot)
                    c.drawRect(x+0.5f, py2, x+bw-0.5f, nextY, cp)
                    // Mortar lines
                    if (py2 > top+1 && row%4==0) {
                        val mp = Paint().apply { color = Color.argb((shade*40).toInt(),0,0,0) }
                        c.drawRect(x+0.5f, py2-1f, x+bw-0.5f, py2+1f, mp)
                    }
                    py2 = nextY; row++
                }
            }

            // Sprites
            val sp = mutableListOf<Triple<Double,Int,Sp>>()
            for (s in spr) { if (!s.al) continue
                val dx = s.x-px; val dy = s.y-py; val d = sqrt(dx*dx+dy*dy); if (d<0.3) continue
                val ang = atan2(dy,dx); var diff = ang-pa
                while(diff>PI) diff-=2*PI; while(diff<-PI) diff+=2*PI
                if (abs(diff) > fov/2+0.3) continue
                val sx = (hW + (diff/(fov/2))*hW).toInt(); if (sx<-60||sx>W+60) continue
                sp.add(Triple(d, sx, s)) }
            sp.sortByDescending { it.first }

            for ((d, sx, s) in sp) {
                val sz = (H/d*0.55).coerceAtMost(H*2).coerceAtLeast(18.0)
                val l = (sx-sz/2).toFloat(); val tp2 = (hH-sz/2).toFloat()
                val dark = (1.0/(1.0+d*0.2)).coerceIn(0.08,1.0)
                when(s.tp) {
                    0 -> { c.drawRect(l,tp2,l+sz.toFloat(),tp2+sz.toFloat(), Paint().apply { color=Color.argb(230,(200*dark).toInt(),(40*dark).toInt(),(40*dark).toInt()) })
                        c.drawRect(l+sz.toFloat()*0.3f,tp2,l+sz.toFloat()*0.7f,tp2+sz.toFloat(), Paint().apply { color=Color.WHITE }) }
                    1 -> c.drawRoundRect(l,tp2,l+sz.toFloat(),tp2+sz.toFloat(),6f,6f, Paint().apply { color=Color.rgb((180*dark).toInt(),(150*dark).toInt(),(40*dark).toInt()) })
                    2 -> { c.drawOval(l,tp2,l+sz.toFloat(),tp2+sz.toFloat(), Paint().apply { color=Color.argb(200,(35*dark).toInt(),(35*dark).toInt(),(35*dark).toInt()) })
                        c.drawCircle(l+sz.toFloat()*0.3f,tp2+sz.toFloat()*0.35f,sz.toFloat()*0.1f, Paint().apply { color=Color.rgb(180,180,255) })
                        c.drawCircle(l+sz.toFloat()*0.7f,tp2+sz.toFloat()*0.35f,sz.toFloat()*0.1f, Paint().apply { color=Color.rgb(180,180,255) }) }
                    3 -> { c.drawRect(l,tp2,l+sz.toFloat(),tp2+sz.toFloat(), Paint().apply { color=Color.argb(220,(130*dark).toInt(),(20*dark).toInt(),(20*dark).toInt()) })
                        for (i2 in 0..3) c.drawLine(l+sz.toFloat()*0.2f+i2*sz.toFloat()*0.15f,tp2+sz.toFloat()*0.5f,l+sz.toFloat()*0.25f+i2*sz.toFloat()*0.15f,tp2+sz.toFloat()*0.8f, Paint().apply { color=Color.WHITE;strokeWidth=1.5f}) }
                    4 -> { c.drawOval(l-8f,tp2-8f,l+sz.toFloat()+8f,tp2+sz.toFloat()+8f, Paint().apply { color=Color.argb(230,(190*dark).toInt(),(8*dark).toInt(),(8*dark).toInt()) })
                        c.drawCircle(l+sz.toFloat()*0.3f,tp2+sz.toFloat()*0.35f,sz.toFloat()*0.14f, Paint().apply { color=Color.YELLOW })
                        c.drawCircle(l+sz.toFloat()*0.7f,tp2+sz.toFloat()*0.35f,sz.toFloat()*0.14f, Paint().apply { color=Color.YELLOW })
                        c.drawRect(l-4f,tp2-14f,l+sz.toFloat()+4f,tp2-8f, Paint().apply { color=Color.RED })
                        c.drawRect(l-4f,tp2-14f,l-4f+sz.toFloat()*(s.hp/8f),tp2-8f, Paint().apply { color=Color.GREEN }) }
                }
            }

            // Weapon at bottom
            drawWeapon(c, W, H)

            // Particles
            for (p in parts) {
                val a = (p.life * 20).coerceIn(0, 255)
                val r = (p.col shr 16) and 0xFF
                val g = (p.col shr 8) and 0xFF
                val b = p.col and 0xFF
                c.drawCircle(p.x, p.y, 3f + p.life / 4f, Paint().apply { color = Color.argb(a, r, g, b) })
            }

            // Darkness overlay
            if (!fl || bat <= 0) {
                val db = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888); val dc = Canvas(db)
                dc.drawColor(Color.argb(210,0,0,0))
                val lp = Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR); isAntiAlias=true }
                dc.drawCircle(sw/2f, sh/2f, 80f, lp); c.drawBitmap(db,0f,0f,null); db.recycle()
            } else if (bat < 30 && System.nanoTime()%15L < 2L) c.drawColor(Color.argb(60,0,0,0))

            // Vignette
            c.drawColor(Color.argb(70,0,0,0))
            val vb = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888); val vc = Canvas(vb)
            val vp = Paint().apply { style=Paint.Style.STROKE; strokeWidth=sw*0.3f; isAntiAlias=true; color=Color.argb(80,0,0,0) }
            vc.drawRect(0f,0f,sw.toFloat(),sh.toFloat(),vp); c.drawBitmap(vb,0f,0f,null); vb.recycle()

            // Damage flash
            if (iframe>0 && iframe%6<3) c.drawColor(Color.argb(45,255,25,25))

            // Shake
            val sx2 = if (shakeT>0) (Math.random()*10-5).toFloat() else 0f
            val sy2 = if (shakeT>0) (Math.random()*10-5).toFloat() else 0f

            // HUD
            drawHUD(c, W, H)

            // Message
            if (mt > 0) {
                val mp = Paint().apply { color=Color.WHITE; textSize=48f; textAlign=Paint.Align.CENTER; isFakeBoldText=true
                    alpha = (mt.coerceAtMost(60)*255/60).coerceIn(0,255) }
                msg.split("\n").forEachIndexed { i2, l -> c.drawText(l, sw/2f+sx2, sh*0.35f+i2*55f, mp) } }

            // Game over
            if (over) {
                c.drawColor(Color.argb(190,0,0,0))
                val ep = Paint().apply { color=if(win)Color.rgb(50,220,50) else Color.rgb(220,30,30); textSize=60f; textAlign=Paint.Align.CENTER; isFakeBoldText=true }
                c.drawText(msg, sw/2f, sh/2f, ep)
                c.drawText("Назад — выход", sw/2f, sh/2f+65f, Paint().apply { color=Color.WHITE; textSize=26f; textAlign=Paint.Align.CENTER })
            }
        } finally { holder.unlockCanvasAndPost(c) }
    }

    private fun drawWeapon(c: Canvas, W: Double, H: Double) {
        // Gun base at bottom center
        val bx = sw/2f - 50f; val by = sh - 180f + sin(bob)*8f
        // Gun body
        val gunBody = Paint().apply { color = Color.rgb(40,40,45) }
        c.drawRoundRect(bx, by, bx+100f, by+140f, 8f, 8f, gunBody)
        // Barrel
        val barrel = Paint().apply { color = Color.rgb(50,50,55) }
        c.drawRoundRect(bx+35f, by-40f, bx+65f, by+5f, 4f, 4f, barrel)
        // Grip
        val grip = Paint().apply { color = Color.rgb(60,40,25) }
        c.drawRoundRect(bx+60f, by+90f, bx+90f, by+140f, 4f, 4f, grip)
        // Trigger guard
        val guard = Paint().apply { color = Color.rgb(45,45,50); style = Paint.Style.STROKE; strokeWidth = 3f }
        c.drawArc(bx+70f, by+70f, bx+100f, by+100f, 0f, 180f, false, guard)

        // Muzzle flash
        if (flashA > 0) {
            val mf = Paint().apply { color = Color.argb((flashA*255).toInt(), 255, 220, 60) }
            c.drawCircle(bx+50f, by-45f, 25f*flashA, mf)
            c.drawCircle(bx+50f, by-45f, 40f*flashA, Paint().apply { color = Color.argb((flashA*120).toInt(), 255, 255, 200) })
        }
    }

    private fun drawHUD(c: Canvas, W: Double, H: Double) {
        // Crosshair
        val ch = Paint().apply { color=Color.argb(140,255,255,255); strokeWidth=1.8f; style=Paint.Style.STROKE }
        c.drawCircle(sw/2f, sh/2f, 10f, ch)
        c.drawLine(sw/2f-16f,sh/2f, sw/2f+16f,sh/2f, ch)
        c.drawLine(sw/2f,sh/2f-16f, sw/2f,sh/2f+16f, ch)

        // HP
        val bw=180f; val bh2=15f; val bx=16f; val by2=sh-45f
        c.drawRoundRect(bx-2,by2-2,bx+bw+2,by2+bh2+2,6f,6f, Paint().apply { color=Color.argb(160,0,0,0) })
        c.drawRoundRect(bx,by2,bx+bw,by2+bh2,6f,6f, Paint().apply { color=Color.rgb(50,12,12) })
        c.drawRoundRect(bx,by2,bx+bw*(hp/100f),by2+bh2,6f,6f, Paint().apply { color=if(hp>60)Color.rgb(50,200,50) else if(hp>25)Color.rgb(220,180,20) else Color.rgb(220,20,20) })
        c.drawText("HP $hp%", bx+4, by2+11, Paint().apply { color=Color.WHITE; textSize=13f; typeface=Typeface.MONOSPACE })

        // Sanity
        val sby = by2-bh2-4f
        c.drawRoundRect(bx-2,sby-2,bx+bw+2,sby+bh2+2,6f,6f, Paint().apply { color=Color.argb(160,0,0,0) })
        c.drawRoundRect(bx,sby,bx+bw,sby+bh2,6f,6f, Paint().apply { color=Color.rgb(12,12,50) })
        c.drawRoundRect(bx,sby,bx+bw*(san/100f),sby+bh2,6f,6f, Paint().apply { color=Color.rgb(100,100,220) })
        c.drawText("SAN $san%", bx+4, sby+11, Paint().apply { color=Color.WHITE; textSize=13f; typeface=Typeface.MONOSPACE })

        // Ammo & battery
        c.drawText("🔫 $ammo", sw-110f, sh-25f, Paint().apply { color=Color.WHITE; textSize=24f; typeface=Typeface.MONOSPACE })
        if (fl) c.drawText("🔦 ${bat.toInt()}%", sw-110f, sh-55f, Paint().apply { color=if(bat>30)Color.WHITE else Color.rgb(255,200,50); textSize=22f; typeface=Typeface.MONOSPACE })

        // Minimap
        val ms = 75f; val mx2 = sw-ms-8f; val my2 = 8f
        c.drawRect(mx2-2,my2-2,mx2+ms+2,my2+ms+2, Paint().apply { color=Color.argb(130,0,0,0) })
        val cw = ms/map.w; val ch2 = ms/map.h
        for (cy in 0 until map.h) for (cx in 0 until map.w) if (map.grid[cy][cx]==1)
            c.drawRect(mx2+cx*cw, my2+cy*ch2, mx2+(cx+1)*cw, my2+(cy+1)*ch2, Paint().apply { color=Color.rgb(80,80,90) })
        // Player on minimap
        val pdx = (mx2 + (px/map.w)*ms).toFloat(); val pdy = (my2 + (py/map.h)*ms).toFloat()
        c.drawCircle(pdx, pdy, 2.5f, Paint().apply { color=Color.GREEN })
        c.drawLine(pdx, pdy, pdx+cos(pa).toFloat()*5f, pdy+sin(pa).toFloat()*5f, Paint().apply { color=Color.GREEN; strokeWidth=1.5f })
        // Monsters on minimap
        for (s in spr) if (s.al && s.tp>=2) c.drawCircle(mx2+(s.x/map.w*ms).toFloat(), my2+(s.y/map.h*ms).toFloat(), 1.5f, Paint().apply { color=if(s.tp==4)Color.RED else Color.rgb(255,100,50) })

        // Zone
        c.drawText(map.name, sw/2f, 28f, Paint().apply { color=Color.argb(150,200,200,200); textSize=18f; textAlign=Paint.Align.CENTER; typeface=Typeface.MONOSPACE })

        // Controls hint
        val hp2 = Paint().apply { color=Color.argb(50,255,255,255); textSize=13f; typeface=Typeface.MONOSPACE; textAlign=Paint.Align.CENTER }
        c.drawText("ДВИЖЕНИЕ", sw*0.2f, sh-6f, hp2)
        c.drawText("ПОВОРОТ / ВЫСТРЕЛ", sw*0.7f, sh-6f, hp2)
    }

    // ================ TOUCH ================
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex; val pi = e.getPointerId(i); val x = e.getX(i)
                if (x < sw*0.35f) { ja=true; jpi=pi; jbx=e.getX(i); jby=e.getY(i) }
                else if (x < sw*0.78f) { ra=true; rpi=pi; rlx=e.getX(i) }
                else shoot = true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    when (e.getPointerId(i)) {
                        jpi -> { jdx=e.getX(i)-jbx; jdy=e.getY(i)-jby }
                        rpi -> { pa += (e.getX(i)-rlx)*0.009; rlx=e.getX(i); if(pa<0)pa+=2*PI; if(pa>2*PI)pa-=2*PI }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pi = e.getPointerId(e.actionIndex)
                if (pi==jpi) { ja=false; jdx=0f; jdy=0f }
                if (pi==rpi) { ra=false }
                if (e.actionMasked==MotionEvent.ACTION_UP) shoot=false
            }
        }
        return true
    }
}
