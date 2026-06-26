package com.horrorgame.screens

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.*
import com.badlogic.gdx.utils.*
import com.horrorgame.HorrorGame
import kotlin.math.*
import kotlin.random.Random

class GameScreen(val game: HorrorGame) : Screen, InputProcessor {

    // World
    private val W = 80; private val H = 60; private val TS = 32f
    private val map = Array(H) { IntArray(W) { if (it==0||it==W-1||idx==0||idx==H-1) 1 else 0 } }
    private var px = 5f * TS; private var py = 5f * TS
    private var pa = 0f // player angle in degrees
    private var hp = 100f; private var san = 100f
    private var ammo = 12; private var fl = true; private var bat = 100f
    private var atkCd = 0f; private var iframe = 0f
    private var gameOver = false; private var victory = false
    private var msg = ""; private var mt = 0f
    private var shakeT = 0f; private var flashA = 0f

    // Camera
    private val cam = OrthographicCamera()
    private var camX = 0f; private var camY = 0f

    // Monsters
    private data class Mob(var x: Float, var y: Float, val type: Int, var hp: Int = 3, var stun: Float = 0f)
    private val mobs = mutableListOf<Mob>()

    // Items
    private data class WItem(var x: Float, var y: Float, val type: Int, var alive: Boolean = true)
    private val items = mutableListOf<WItem>()

    // Bullets
    private data class Bul(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float = 1.5f)
    private val bullets = mutableListOf<Bul>()

    // Particles
    private data class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val r: Float, val g: Float, val b: Float)
    private val particles = mutableListOf<Part>()

    // Input
    private var moveX = 0f; private var moveY = 0f
    private var lookDx = 0f; private var firePressed = false
    private var joystickId = -1; private var lookId = -1
    private var jsBaseX = 0f; private var jsBaseY = 0f

    // Font
    private lateinit var font: BitmapFont

    override fun show() {
        generateMap()
        spawnEntities()
        cam.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        Gdx.input.inputProcessor = this
        font = BitmapFont()
    }

    private fun generateMap() {
        // Clear interior
        for (y in 1 until H-1) for (x in 1 until W-1) map[y][x] = 0
        // Rooms
        buildRoom(2,2,12,10); buildRoom(20,2,12,9); buildRoom(42,2,14,10)
        buildRoom(2,15,10,8); buildRoom(18,14,15,10); buildRoom(40,15,16,9)
        buildRoom(5,26,20,12); buildRoom(30,27,25,10); buildRoom(60,2,8,14)
        buildRoom(60,20,14,14); buildRoom(2,40,15,10); buildRoom(25,40,22,10)
        buildRoom(55,38,18,12)
        // Corridors connecting rooms
        buildCorridor(8,12,8,15)
        buildCorridor(26,11,42,11)
        buildCorridor(8,23,8,26)
        buildCorridor(26,26,40,26)
        buildCorridor(33,15,33,26)
        buildCorridor(48,14,48,26)
        buildCorridor(64,16,64,20)
        buildCorridor(2,26,5,26)
        buildCorridor(18,26,25,26)
        buildCorridor(15,40,25,40)
        buildCorridor(25,29,30,29)
        buildCorridor(38,29,55,29)
        buildCorridor(55,26,55,38)
        buildCorridor(34,40,34,37)
    }

    private fun buildRoom(rx: Int, ry: Int, rw: Int, rh: Int) {
        for (y in ry until ry+rh) for (x in rx until rx+rw) {
            if (y==ry||y==ry+rh-1||x==rx||x==rx+rw-1) map[y][x] = 1 else map[y][x] = 0
        }
        // Door openings
        map[ry+rh/2][rx] = 0; map[ry+rh/2][rx+rw-1] = 0
        map[ry][rx+rw/2] = 0; map[ry+rh-1][rx+rw/2] = 0
    }

    private fun buildCorridor(x1: Int, y1: Int, x2: Int, y2: Int) {
        var x=x1; var y=y1
        while (x!=x2||y!=y2) {
            map[y][x]=0; if (x<x2) x++ else if (x>x2) x--
            if (y<y2) y++ else if (y>y2) y--
        }
        map[y][x]=0
    }

    private fun spawnEntities() {
        mobs.clear(); items.clear()
        val rng = Random(42)
        // Monsters in rooms
        for (i in 0..18) {
            var mx: Int; var my: Int
            do { mx=rng.nextInt(2,W-2); my=rng.nextInt(2,H-2) } while (map[my][mx]!=0)
            mobs.add(Mob(mx*TS+TS/2, my*TS+TS/2, rng.nextInt(3), rng.nextInt(2,5)))
        }
        // Boss
        mobs.add(Mob(50*TS, 45*TS, 3, hp = 12)) // Boss type 3
        // Items
        for (i in 0..25) {
            var ix: Int; var iy: Int
            do { ix=rng.nextInt(2,W-2); iy=rng.nextInt(2,H-2) } while (map[iy][ix]!=0)
            items.add(WItem(ix*TS+TS/2, iy*TS+TS/2, rng.nextInt(4)))
        }
    }

    // ==================== UPDATE ====================
    override fun render(delta: Float) {
        if (gameOver) { draw(); return }
        val dt = delta.coerceAtMost(0.05f)
        updatePlayer(dt)
        updateMonsters(dt)
        updateBullets(dt)
        updateParticles(dt)
        updateItems()
        if (mt>0) mt-=dt; if (atkCd>0) atkCd-=dt; if (iframe>0) iframe-=dt
        if (shakeT>0) shakeT-=dt; if (flashA>0) flashA = (flashA-2f*dt).coerceAtLeast(0f)
        if (!fl && Random.nextFloat()<0.005f) san-=0.5f
        if (san<=0) { gameOver=true; msg="РАССУДОК УТРАЧЕН" }
        if (fl && bat>0) bat -= if(moveX!=0f||moveY!=0f) 3f*dt else 1f*dt
        if (bat<=0) fl=false
        draw()
    }

    private fun updatePlayer(dt: Float) {
        // Rotation from look input
        pa += lookDx * 180f * dt
        lookDx *= 0.7f

        // Movement
        if (moveX!=0f||moveY!=0f) {
            val spd = 180f
            val rad = pa * Math.PI.toFloat() / 180f
            val fx = (cos(rad)*moveY - sin(rad)*moveX).toFloat()
            val fy = (sin(rad)*moveY + cos(rad)*moveX).toFloat()
            val nx = px + fx*spd*dt; val ny = py + fy*spd*dt
            if (map[(ny/TS).toInt()][(nx/TS).toInt()]==0) { px=nx; py=ny }
        }

        // Shoot
        if (firePressed && atkCd<=0 && ammo>0) {
            ammo--; atkCd=0.3f
            val rad = pa * Math.PI.toFloat() / 180f
            bullets.add(Bul(px, py, cos(rad)*400f, sin(rad)*400f))
            flashA=0.4f; shakeT=0.08f
            for (i in 0..5) particles.add(Part(px,py,(Random.nextFloat()-0.5f)*60f,(Random.nextFloat()-0.5f)*60f,0.3f,1f,0.8f,0.2f))
        }
        firePressed=false
    }

    private fun updateMonsters(dt: Float) {
        for (m in mobs) {
            if (m.stun>0) { m.stun-=dt; continue }
            val dx=px-m.x; val dy=py-m.y; val d= sqrt(dx*dx+dy*dy)
            val spd = when(m.type){0->50f; 1->110f; 2->75f; 3->60f; else->50f}
            if (d<500f && d>25f) { m.x+=dx/d*spd*dt; m.y+=dy/d*spd*dt }
            // Wall collision
            if (map[(m.y/TS).toInt()][(m.x/TS).toInt()]!=0) { m.x-=dx/d*20f; m.y-=dy/d*20f }
            // Attack
            if (d<30f && m.stun<=0) {
                hp-= when(m.type){0->6f;1->14f;2->10f;3->22f;else->5f}
                san-= when(m.type){0->2f;1->5f;2->3f;3->8f;else->1f}
                iframe=0.4f; m.stun=1.5f; shakeT=0.2f; flashA=0.6f
                if (hp<=0) { gameOver=true; msg="ВЫ ПОГИБЛИ" }
            }
            // Sanity drain when close
            if (d<150f && m.type==3) san-=3f*dt
        }
    }

    private fun updateBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next(); b.x+=b.vx*dt; b.y+=b.vy*dt; b.life-=dt
            if (b.life<=0 || map[(b.y/TS).toInt()][(b.x/TS).toInt()]!=0) { it.remove(); continue }
            // Hit monster
            for (m in mobs) {
                val d= sqrt((b.x-m.x)*(b.x-m.x)+(b.y-m.y)*(b.y-m.y))
                if (d<25f) { m.hp--; m.stun=0.4f; it.remove()
                    for (i in 0..8) particles.add(Part(b.x,b.y,(Random.nextFloat()-0.5f)*120f,(Random.nextFloat()-0.5f)*120f,0.4f,1f,0.2f,0.2f))
                    if (m.hp<=0) { if (m.type==3) { gameOver=true; victory=true; msg="БОСС УНИЧТОЖЕН!\nВЫ ПОБЕДИЛИ!" } }
                    break }
            }
        }
    }

    private fun updateParticles(dt: Float) {
        val it=particles.iterator()
        while(it.hasNext()){val p=it.next();p.x+=p.vx*dt;p.y+=p.vy*dt;p.life-=dt;if(p.life<=0)it.remove()}
    }

    private fun updateItems() {
        val it=items.iterator()
        while(it.hasNext()){val i=it.next();if(!i.alive){it.remove();continue}
            if(sqrt((px-i.x)*(px-i.x)+(py-i.y)*(py-i.y))<28f){i.alive=false
                when(i.type){0->hp=(hp+30).coerceAtMost(100f);1->ammo+=3;2->{bat=100f;fl=true};3->san=(san+25).coerceAtMost(100f)}
                msg=when(i.type){0->"+30 HP";1->"+3 Ammo";2->"Батарейки";3->"+25 Sanity";else->""};mt=1.5f
            }}
    }

    // ==================== DRAW ====================
    private fun draw() {
        Gdx.gl.glClearColor(0.03f, 0.02f, 0.05f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val sw = Gdx.graphics.width.toFloat(); val sh = Gdx.graphics.height.toFloat()
        camX += (px - camX) * 0.1f; camY += (py - camY) * 0.1f
        cam.position.set(camX, camY, 0f); cam.update()

        val sr = game.shapes
        sr.projectionMatrix = cam.combined
        sr.begin(ShapeRenderer.ShapeType.Filled)

        // Draw map tiles
        val stx = ((camX-sw/2)/TS).toInt().coerceAtLeast(0)-1
        val sty = ((camY-sh/2)/TS).toInt().coerceAtLeast(0)-1
        val etx = ((camX+sw/2)/TS).toInt().coerceAtMost(W-1)+1
        val ety = ((camY+sh/2)/TS).toInt().coerceAtMost(H-1)+1
        for (y in sty..ety) for (x in stx..etx) {
            if (map[y][x]==1) { sr.color=Color(0.25f,0.22f,0.2f,1f); sr.rect(x*TS,y*TS,TS,TS) }
            else { sr.color=Color(0.08f,0.07f,0.06f,1f); sr.rect(x*TS,y*TS,TS,TS) }
        }

        // Items
        for (i in items) if (i.alive) {
            val c=when(i.type){0->Color(0.9f,0.2f,0.2f,1f);1->Color(0.8f,0.7f,0.15f,1f);2->Color(0.3f,0.6f,1f,1f);else->Color(0.3f,0.8f,1f,1f)}
            sr.color=c; sr.circle(i.x,i.y,10f)
        }

        // Monsters
        for (m in mobs) {
            val c=when(m.type){0->Color(0.2f,0.2f,0.2f,1f);1->Color(0.7f,0.1f,0.1f,1f);2->Color(0.1f,0.1f,0.5f,1f);else->Color(0.8f,0.05f,0.05f,1f)}
            val r=if(m.type==3)22f else 14f; sr.color=c; sr.circle(m.x,m.y,r)
            // Eyes
            val angle= atan2((py-m.y).toDouble(),(px-m.x).toDouble()).toFloat()
            val ex=m.x+ cos(angle)*r*0.5f; val ey=m.y+ sin(angle)*r*0.5f
            sr.color=if(m.type==3)Color.YELLOW else Color.WHITE
            sr.circle(ex-4f,ey-3f,3f); sr.circle(ex+4f,ey-3f,3f)
            // HP bar
            if (m.hp< (if(m.type==3)12 else 5)) {
                sr.color=Color.RED; sr.rect(m.x-15f,m.y-r-8f,30f,4f)
                sr.color=Color.GREEN; sr.rect(m.x-15f,m.y-r-8f,30f*(m.hp.toFloat()/(if(m.type==3)12f else 5f)),4f)
            }
        }

        // Player
        sr.color=Color(0.7f,0.65f,0.55f,1f); sr.circle(px,py,14f)
        val rad=pa*Math.PI.toFloat()/180f
        sr.color=Color(0.85f,0.8f,0.7f,1f)
        sr.circle(px+ cos(rad)*18f, py+ sin(rad)*18f, 6f)

        // Bullets
        sr.color=Color.YELLOW
        for(b in bullets) sr.circle(b.x,b.y,3f)

        // Particles
        for(p in particles) { sr.color=Color(p.r,p.g,p.b,p.life*2.5f); sr.circle(p.x,p.y,3f+p.life*4f) }
        sr.end()

        // Flash
        if (flashA>0) { Gdx.gl.glEnable(GL20.GL_BLEND); sr.begin(ShapeRenderer.ShapeType.Filled)
            sr.color=Color(1f,0.2f,0.2f,flashA); sr.rect(0f,0f,sw,sh); sr.end() }

        // HUD via SpriteBatch
        val sb=game.batch; sb.projectionMatrix.setToOrtho2D(0f,0f,sw,sh)
        sb.begin()
        font.color=Color.WHITE
        // HP
        font.draw(sb,"HP ${hp.toInt()}%", 16f, sh-16f)
        font.draw(sb,"SAN ${san.toInt()}%", 16f, sh-40f)
        font.draw(sb,"AMMO $ammo", sw-120f, sh-16f)
        if (fl) font.draw(sb,"BAT ${bat.toInt()}%", sw-120f, sh-40f)
        // Map name
        font.draw(sb,"ЗАБРОШЕННАЯ БОЛЬНИЦА", sw/2-80f, 24f)
        // Message
        if (mt>0) { font.color=Color(1f,1f,1f,mt.coerceAtMost(1f)); font.draw(sb,msg,sw/2-50f,sh/2+40f) }
        // Game over
        if (gameOver) {
            font.color=if(victory)Color.GREEN else Color.RED
            font.draw(sb,msg,sw/2-80f,sh/2)
            font.color=Color.WHITE; font.draw(sb,"Tap to restart",sw/2-50f,sh/2-40f)
        }
        sb.end()

        // Darkness overlay
        if (!fl || bat<=0) {
            sr.begin(ShapeRenderer.ShapeType.Filled)
            sr.color=Color(0f,0f,0f,0.75f)
            sr.rect(0f,0f,sw,sh)
            sr.end()
            // Small light circle
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_ZERO, GL20.GL_SRC_COLOR)
            sr.begin(ShapeRenderer.ShapeType.Filled)
            sr.color=Color(0.3f,0.3f,0.3f,1f)
            sr.circle(sw/2,sh/2,60f)
            sr.end()
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    // ==================== INPUT ====================
    override fun touchDown(sx: Int, sy: Int, pointer: Int, button: Int): Boolean {
        val x=sx.toFloat(); val y=(Gdx.graphics.height-sy).toFloat()
        if (gameOver) { restart(); return true }
        if (x<Gdx.graphics.width*0.4f) { joystickId=pointer; jsBaseX=x; jsBaseY=y }
        else { firePressed=true }
        return true
    }
    override fun touchDragged(sx: Int, sy: Int, pointer: Int): Boolean {
        val x=sx.toFloat(); val y=(Gdx.graphics.height-sy).toFloat()
        if (pointer==joystickId) { moveX=(x-jsBaseX)/80f; moveY=(y-jsBaseY)/80f
            val l= sqrt(moveX*moveX+moveY*moveY); if (l>1f) { moveX/=l; moveY/=l } }
        if (pointer==lookId) { lookDx=(x-Gdx.graphics.width/2f)/20f }
        return true
    }
    override fun touchUp(sx: Int, sy: Int, pointer: Int, button: Int): Boolean {
        if (pointer==joystickId) { joystickId=-1; moveX=0f; moveY=0f }
        if (pointer==lookId) { lookId=-1 }
        return true
    }
    override fun mouseMoved(sx: Int, sy: Int): Boolean { return false }
    override fun scrolled(amountX: Float, amountY: Float): Boolean { return false }
    override fun keyDown(k: Int): Boolean { return false }
    override fun keyUp(k: Int): Boolean { return false }
    override fun keyTyped(c: Char): Boolean { return false }
    override fun touchCancelled(sx: Int, sy: Int, pointer: Int, button: Int): Boolean { return touchUp(sx,sy,pointer,button) }

    private fun restart() {
        hp=100f; san=100f; ammo=12; fl=true; bat=100f; gameOver=false; victory=false; px=5f*TS; py=5f*TS
        mobs.clear(); items.clear(); bullets.clear(); particles.clear()
        spawnEntities()
    }

    override fun resize(w: Int, h: Int) { cam.setToOrtho(false, w.toFloat(), h.toFloat()) }
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() { font.dispose() }
}
