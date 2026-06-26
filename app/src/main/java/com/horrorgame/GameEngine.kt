package com.horrorgame

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

// ============ ACTIVITY ============
class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); setContentView(GameView(this)) }
}

// ============ GAME VIEW ============
class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback {
    private var t: Thread? = null; private var run = false
    private var W = 1f; private var H = 1f

    // World
    private val MW = 60; private val MH = 40; private val TS = 36f
    private val map = Array(MH) { IntArray(MW) }
    private var px = 5f * TS; private var py = 5f * TS; private var pa = 0f
    private var hp = 100f; private var san = 100f; private var ammo = 12
    private var fl = true; private var bat = 100f; private var atkCd = 0f
    private var iframe = 0f; private var over = false; private var win = false
    private var msg = ""; private var mt = 0f; private var shake = 0f; private var flash = 0f
    private var elapsed = 0L

    // Entities
    private data class M(var x: Float, var y: Float, val tp: Int, var hp: Int, var stun: Float = 0f)
    private data class B(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float = 1.2f)
    private data class I(var x: Float, var y: Float, val tp: Int, var al: Boolean = true)
    private val mobs = mutableListOf<M>(); private val bullets = mutableListOf<B>()
    private val items = mutableListOf<I>(); private val parts = mutableListOf<FloatArray>()

    // Input
    private var ja = false; private var jbx = 0f; private var jby = 0f; private var jdx = 0f; private var jdy = 0f
    private var fire = false; private var jpi = -1; private var camX = 0f; private var camY = 0f

    // Paints
    private val wallP = Paint().apply { color = Color.rgb(55, 50, 48) }
    private val floorP = Paint().apply { color = Color.rgb(25, 22, 20) }
    private val playerP = Paint().apply { color = Color.rgb(200, 180, 155) }
    private val playerFP = Paint().apply { color = Color.rgb(220, 210, 185) }
    private val bulletP = Paint().apply { color = Color.YELLOW }
    private val hudP = Paint().apply { color = Color.WHITE; textSize = 28f; typeface = Typeface.MONOSPACE; isAntiAlias = true }
    private val hudSmall = Paint().apply { color = Color.WHITE; textSize = 20f; typeface = Typeface.MONOSPACE }
    private val msgP = Paint().apply { color = Color.WHITE; textSize = 40f; typeface = Typeface.MONOSPACE; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    private val darkP = Paint().apply { color = Color.argb(200, 0, 0, 0) }

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(h: SurfaceHolder) {
        genMap(); spawn()
        run = true
        t = Thread {
            var lt = System.nanoTime()
            while (run) {
                val nt = System.nanoTime(); val dt = ((nt - lt) / 1_000_000_000f).coerceAtMost(0.05f); lt = nt
                elapsed++; tick(dt); draw()
                try { Thread.sleep(16) } catch (_: InterruptedException) { break }
            }
        }; t?.start()
    }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) { W = w.toFloat(); H = h2.toFloat() }
    override fun surfaceDestroyed(h: SurfaceHolder) { run = false; t?.interrupt(); t = null }

    // ==================== MAP GEN ====================
    private fun genMap() {
        for (y in 0 until MH) for (x in 0 until MW) map[y][x] = if (y==0||y==MH-1||x==0||x==MW-1) 1 else 0
        // Rooms
        room(2,2,10,8); room(16,2,12,8); room(32,2,10,8); room(46,2,12,8)
        room(2,13,10,8); room(16,13,14,9); room(34,13,10,8); room(48,13,10,8)
        room(2,25,12,10); room(18,25,16,10); room(38,25,12,9)
        // Corridors
        cor(7,10,7,13); cor(22,10,22,13); cor(37,10,37,13); cor(52,10,52,13)
        cor(7,21,7,25); cor(23,22,23,25); cor(39,21,39,25)
        cor(12,20,16,20); cor(23,17,34,17); cor(43,17,48,17)
        cor(8,30,18,30); cor(26,30,38,30)
    }
    private fun room(rx: Int, ry: Int, rw: Int, rh: Int) {
        for (y in ry until ry+rh) for (x in rx until rx+rw)
            map[y][x] = if (y==ry||y==ry+rh-1||x==rx||x==rx+rw-1) 1 else 0
        map[ry+rh/2][rx]=0; map[ry+rh/2][rx+rw-1]=0; map[ry][rx+rw/2]=0; map[ry+rh-1][rx+rw/2]=0
    }
    private fun cor(x1: Int, y1: Int, x2: Int, y2: Int) {
        var x=x1; var y=y1
        while (x!=x2||y!=y2) { map[y][x]=0; if(x<x2)x++ else if(x>x2)x--; if(y<y2)y++ else if(y>y2)y-- }
        map[y][x]=0
    }

    // ==================== SPAWN ====================
    private fun spawn() {
        mobs.clear(); items.clear(); bullets.clear(); parts.clear()
        val r= Random(42)
        for (i in 0..20) { var mx:Int; var my:Int; do { mx=r.nextInt(2,MW-2); my=r.nextInt(2,MH-2) } while(map[my][mx]!=0)
            mobs.add(M(mx*TS+TS/2, my*TS+TS/2, r.nextInt(3), r.nextInt(2,5))) }
        mobs.add(M(45f*TS, 30f*TS, 3, 12)) // boss
        for (i in 0..30) { var ix:Int; var iy:Int; do { ix=r.nextInt(2,MW-2); iy=r.nextInt(2,MH-2) } while(map[iy][ix]!=0)
            items.add(I(ix*TS+TS/2, iy*TS+TS/2, r.nextInt(4))) }
         px=8f*TS; py=5f*TS
    }

    // ==================== TICK ====================
    private fun tick(dt: Float) {
        if (over) return
        if (atkCd>0) atkCd-=dt; if (iframe>0) iframe-=dt; if (mt>0) mt-=dt
        if (shake>0) shake-=dt; if (flash>0) flash=(flash-2f*dt).coerceAtLeast(0f)

        // Move player
        if (ja && (jdx!=0f||jdy!=0f)) {
            val l= sqrt(jdx*jdx+jdy*jdy); val mx=jdx/l; val my=jdy/l
            val spd=180f; val nx=px+mx*spd*dt; val ny=py+my*spd*dt
            if (map[(ny/TS).toInt()][(nx/TS).toInt()]==0) { px=nx; py=ny }
            pa= atan2(my.toDouble(), mx.toDouble()).toFloat()
            if (fl) { bat-=2f*dt; if (bat<=0) fl=false }
        }
        // Shoot
        if (fire && atkCd<=0 && ammo>0) { ammo--; atkCd=0.25f; flash=0.5f; shake=0.1f
            bullets.add(B(px, py, cos(pa)*450f, sin(pa)*450f))
            for (i in 0..6) parts.add(floatArrayOf(px,py,(Random.nextFloat()-0.5f)*80f,(Random.nextFloat()-0.5f)*80f,0.3f,1f,0.8f,0.2f)) }
        fire=false

        // Monsters
        for (m in mobs) { if (m.stun>0) { m.stun-=dt; continue }
            val dx=px-m.x; val dy=py-m.y; val d= sqrt(dx*dx+dy*dy)
            val spd = when(m.tp){0->55f; 1->120f; 2->80f; 3->70f; else->50f}
            if (d<450f && d>22f) { m.x+=dx/d*spd*dt; m.y+=dy/d*spd*dt }
            if (map[(m.y/TS).toInt()][(m.x/TS).toInt()]!=0) { m.x-=dx/d*30f; m.y-=dy/d*30f }
            if (d<28f && m.stun<=0) { hp-= when(m.tp){0->7f;1->15f;2->11f;3->24f;else->5f}
                san-= when(m.tp){0->2f;1->5f;2->3f;3->8f;else->1f}
                iframe=0.35f; m.stun=1.4f; shake=0.25f; flash=0.7f
                if (hp<=0) { over=true; msg="ВЫ ПОГИБЛИ" } }
            if (d<120f && m.tp==3) san-=4f*dt
        }

        // Bullets
        val bi=bullets.iterator(); while(bi.hasNext()){val b=bi.next();b.x+=b.vx*dt;b.y+=b.vy*dt;b.life-=dt
            if(b.life<=0||map[(b.y/TS).toInt()][(b.x/TS).toInt()]!=0){bi.remove();continue}
            for (m in mobs) {
                val d2 = (b.x-m.x)*(b.x-m.x)+(b.y-m.y)*(b.y-m.y)
                if (d2 < 24f*24f) { m.hp--; m.stun=0.35f; bi.remove()
                    for (i in 0..10) parts.add(floatArrayOf(b.x,b.y,(Random.nextFloat()-0.5f)*150f,(Random.nextFloat()-0.5f)*150f,0.35f,1f,0.15f,0.15f))
                    if (m.hp <= 0 && m.tp == 3) { over=true; win=true; msg="BOSS KILLED"; }
                    break
                }
            }

        // Particles
        val pi=parts.iterator(); while(pi.hasNext()){val p=pi.next();p[0]+=p[2]*dt;p[1]+=p[3]*dt;p[4]-=dt;if(p[4]<=0)pi.remove()}

        // Items
        val ii=items.iterator(); while(ii.hasNext()){val i=ii.next();if(!i.al){ii.remove();continue}
            if(sqrt((px-i.x)*(px-i.x)+(py-i.y)*(py-i.y))<26f){i.al=false
                when(i.tp){0->hp=(hp+30).coerceAtMost(100f);1->ammo+=3;2->{bat=100f;fl=true};3->san=(san+25).coerceAtMost(100f)}
                msg=when(i.tp){0->"+30 HP";1->"+3 Ammo";2->"Батарейки";3->"+25 San";else->""};mt=1.5f}}
        if (!fl && Random.nextFloat()<0.006f) san-=0.6f
        if (san<=0) { over=true; msg="РАССУДОК УТРАЧЕН" }
    }

    // ==================== DRAW ====================
    private fun draw() {
        val c = holder.lockCanvas() ?: return
        try {
            val sw=W; val sh=H
            // Camera
            camX += (px - camX) * 0.08f; camY += (py - camY) * 0.08f
            val ox = camX - sw/2; val oy = camY - sh/2
            val shakeX = if(shake>0) (Math.random()*8-4).toFloat() else 0f
            val shakeY = if(shake>0) (Math.random()*8-4).toFloat() else 0f

            // Background
            c.drawColor(Color.rgb(8, 6, 12))

            // Tiles
            val stx=((ox)/TS).toInt().coerceAtLeast(0)-1; val sty=((oy)/TS).toInt().coerceAtLeast(0)-1
            val etx=((ox+sw)/TS).toInt().coerceAtMost(MW-1)+1; val ety=((oy+sh)/TS).toInt().coerceAtMost(MH-1)+1
            for (y in sty..ety) for (x in stx..etx) {
                val sx = (x*TS-ox+shakeX); val sy = (y*TS-oy+shakeY)
                if (map[y][x]==1) c.drawRect(sx,sy,sx+TS,sy+TS,wallP)
                else c.drawRect(sx,sy,sx+TS,sy+TS,floorP)
            }

            // Items
            for (i in items) if (i.al) {
                val sx=i.x-ox+shakeX; val sy=i.y-oy+shakeY
                val ip=Paint().apply { color=when(i.tp){0->Color.rgb(220,40,40);1->Color.rgb(200,170,30);2->Color.rgb(60,140,240);else->Color.rgb(60,200,220)} }
                c.drawCircle(sx,sy,9f,ip)
            }

            // Monsters
            for (m in mobs) {
                val sx=m.x-ox+shakeX; val sy=m.y-oy+shakeY
                val r=if(m.tp==3)20f else 13f
                val col=when(m.tp){0->Color.rgb(35,35,35);1->Color.rgb(170,20,20);2->Color.rgb(20,20,100);else->Color.rgb(200,10,10)}
                c.drawCircle(sx,sy,r,Paint().apply{color=col})
                // Eyes
                val ang= atan2((py-m.y).toDouble(),(px-m.x).toDouble()).toFloat()
                val ex=sx+ cos(ang)*r*0.5f; val ey=sy+ sin(ang)*r*0.5f
                c.drawCircle(ex-4f,ey-3f,3f,Paint().apply{color=if(m.tp==3)Color.YELLOW else Color.WHITE})
                c.drawCircle(ex+4f,ey-3f,3f,Paint().apply{color=if(m.tp==3)Color.YELLOW else Color.WHITE})
                // HP bar
                val maxH = if(m.tp==3) 12f else 5f
                if(m.hp<maxH){c.drawRect(sx-14f,sy-r-10f,sx+14f,sy-r-6f,Paint().apply{color=Color.RED})
                    c.drawRect(sx-14f,sy-r-10f,sx-14f+28f*(m.hp/maxH),sy-r-6f,Paint().apply{color=Color.GREEN})}
            }

            // Player
            val ppx=px-ox+shakeX; val ppy=py-oy+shakeY
            c.drawCircle(ppx,ppy,13f,playerP)
            c.drawCircle(ppx+ cos(pa)*16f, ppy+ sin(pa)*16f, 5f, playerFP)

            // Bullets
            for (b in bullets) c.drawCircle(b.x-ox+shakeX, b.y-oy+shakeY, 3f, bulletP)

            // Particles
            for (p in parts) { val a=(p[4]*255).toInt().coerceIn(0,255)
                c.drawCircle(p[0]-ox+shakeX,p[1]-oy+shakeY,2f+p[4]*5f,Paint().apply{color=Color.argb(a,p[5].toInt()*255,p[6].toInt()*255,p[7].toInt()*255)})}

            // Flash
            if (flash>0) { val fp=Paint().apply{color=Color.argb((flash*180).toInt(),255,30,30)}; c.drawRect(0f,0f,sw,sh,fp) }

            // Darkness
            if (!fl||bat<=0) {
                val db=Bitmap.createBitmap(sw.toInt(),sh.toInt(),Bitmap.Config.ARGB_8888); val dc=Canvas(db)
                dc.drawColor(Color.argb(195,0,0,0))
                val lp=Paint().apply{xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR);isAntiAlias=true}
                dc.drawCircle(sw/2,sh/2,65f,lp); c.drawBitmap(db,0f,0f,null); db.recycle()
            }

            // HUD
            c.drawText("HP ${hp.toInt()}%", 16f, sh-16f, hudP)
            c.drawText("SAN ${san.toInt()}%", 16f, sh-44f, hudP)
            c.drawText("AMMO $ammo", sw-130f, sh-16f, hudP)
            if (fl) { val bp=Paint(hudP); bp.color=if(bat>30)Color.WHITE else Color.rgb(255,200,50)
                c.drawText("BAT ${bat.toInt()}%", sw-130f, sh-44f, bp) }
            c.drawText("ЗАБРОШЕННАЯ БОЛЬНИЦА", sw/2-100f, 28f, hudSmall)

            // Message
            if (mt>0) { msgP.alpha=(mt.coerceAtMost(1f)*255).toInt(); c.drawText(msg,sw/2,sh/2+50f,msgP) }

            // Crosshair
            val ch=Paint().apply{color=Color.argb(100,255,255,255);strokeWidth=1.5f;style=Paint.Style.STROKE}
            c.drawCircle(sw/2,sh/2,10f,ch); c.drawLine(sw/2-14f,sh/2,sw/2+14f,sh/2,ch)

            // Game over
            if (over) {
                c.drawColor(Color.argb(180,0,0,0))
                msgP.color=if(win)Color.rgb(50,220,50) else Color.rgb(220,30,30)
                msg.split("\n").forEachIndexed{i2,l->c.drawText(l,sw/2,sh/2-10f+i2*50f,msgP)}
                msgP.color=Color.WHITE; msgP.textSize=24f
                c.drawText("Тап — заново", sw/2, sh/2+50f, msgP); msgP.textSize=40f
            }

            // Touch hints
            val hp2=Paint().apply{color=Color.argb(40,255,255,255);textSize=16f;textAlign=Paint.Align.CENTER}
            c.drawText("ДЖОЙСТИК", sw*0.22f, sh-4f, hp2)
            c.drawText("ОГОНЬ", sw*0.78f, sh-4f, hp2)
        } finally { holder.unlockCanvasAndPost(c) }
    }

    // ==================== TOUCH ====================
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (over) { hp=100f;san=100f;ammo=12;fl=true;bat=100f;over=false;win=false;spawn();return true }
                val i=e.actionIndex; val x=e.getX(i)
                if (x<W*0.45f) { ja=true; jpi=e.getPointerId(i); jbx=x; jby=e.getY(i) }
                else fire=true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) if (e.getPointerId(i)==jpi) { jdx=e.getX(i)-jbx; jdy=e.getY(i)-jby }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex)==jpi) { ja=false; jdx=0f; jdy=0f }
            }
        }
        return true
    }
}
