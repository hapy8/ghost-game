package com.ghostrun.game.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.ghostrun.game.audio.SoundManager
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle), SurfaceHolder.Callback {

    interface GameListener {
        fun onScore(score: Int, best: Int)
        fun onGameOver(score: Int)
        fun onCombo(combo: Int) {}
    }

    var state: GameState = GameState.MENU
        private set
    var listener: GameListener? = null
    var bestScore: Int = 0
    val sound = SoundManager()
    val engine = GameEngine()

    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastScoreSent = -1
    private var lastComboSent = 0
    private var lastHudTime = 0L
    private var combo = 0

    // Reused paints — never allocate in the hot loop.
    private val bgPaint = Paint()
    private val groundPaint = Paint().apply { color = Color.rgb(20, 60, 20) }
    private val groundLinePaint = Paint().apply { color = Color.rgb(46, 110, 46); strokeWidth = 4f }
    private val groundStripePaint = Paint().apply { color = Color.rgb(26, 74, 26) }
    private val hillPaint = Paint().apply { color = Color.rgb(22, 48, 68); isAntiAlias = true }
    private val hill2Paint = Paint().apply { color = Color.rgb(28, 62, 52); isAntiAlias = true }
    private val starPaint = Paint().apply { color = Color.argb(220, 255, 255, 255) }
    private val moonPaint = Paint().apply { color = Color.rgb(240, 240, 210); isAntiAlias = true }
    private val moonGlowPaint = Paint().apply { color = Color.argb(45, 240, 240, 200); isAntiAlias = true }
    private val fogPaint = Paint().apply { color = Color.argb(26, 160, 170, 220) }
    private val cloudPaint = Paint().apply { color = Color.argb(110, 255, 255, 255) }
    private val ghostPaint = Paint().apply { color = Color.rgb(220, 220, 255); isAntiAlias = true }
    private val ghostGlowPaint = Paint().apply { color = Color.argb(80, 220, 220, 255); isAntiAlias = true }
    private val trailPaint = Paint().apply { color = Color.argb(70, 220, 220, 255); isAntiAlias = true }
    private val blackPaint = Paint().apply { color = Color.BLACK; isAntiAlias = true }
    private val whitePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val textPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true; textSize = 30f; isFakeBoldText = true }
    private val goldPaint = Paint().apply { color = Color.rgb(255, 215, 0); isAntiAlias = true }
    private val goldGlowPaint = Paint().apply { color = Color.argb(100, 255, 255, 100); isAntiAlias = true }
    private val trunkPaint = Paint().apply { color = Color.rgb(80, 50, 20) }
    private val leafPaint = Paint().apply { color = Color.rgb(50, 200, 50) }
    private val leaf2Paint = Paint().apply { color = Color.rgb(40, 180, 40) }
    private val rockPaint = Paint().apply { color = Color.rgb(128, 128, 128); isAntiAlias = true }
    private val rockHiPaint = Paint().apply { color = Color.rgb(100, 100, 100); isAntiAlias = true }
    private val batPaint = Paint().apply { color = Color.rgb(160, 80, 220) }
    private val batBodyPaint = Paint().apply { color = Color.rgb(50, 20, 50) }
    private val redPaint = Paint().apply { color = Color.rgb(255, 60, 60) }
    private val particlePaint = Paint().apply { isAntiAlias = true }
    private val path = Path()

    private data class Star(val x: Float, val y: Float, val r: Float, val tw: Float)
    private val stars = ArrayList<Star>(80)
    private val rng = Random(7)

    init {
        holder.addCallback(this)
        isFocusable = true
        repeat(75) {
            stars.add(
                Star(
                    rng.nextFloat() * GameConfig.LOGICAL_WIDTH,
                    rng.nextFloat() * 420f,
                    1f + rng.nextFloat() * 2f,
                    rng.nextFloat() * 6.28f
                )
            )
        }
    }

    // ---- Public controls ----

    fun startGame() {
        engine.reset()
        state = GameState.PLAYING
        lastScoreSent = -1
        lastComboSent = 0
        combo = 0
        if (!sound.isMuted) sound.startMusic()
    }

    fun pauseGame() {
        if (state == GameState.PLAYING) state = GameState.PAUSED
    }

    fun resumeGame() {
        if (state == GameState.PAUSED) {
            state = GameState.PLAYING
            lastHudTime = 0L
        }
    }

    fun toMenu() {
        state = GameState.MENU
        sound.stopMusic()
        engine.updateMenuDrift()
    }

    // ---- Thread ----

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(::loop, "GhostRun-Loop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join(800) } catch (_: Exception) { }
        thread = null
    }

    fun shutdown() {
        running = false
        sound.release()
    }

    private fun loop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var deltaNs = now - last
            // Clamp huge gaps (backgrounding) to avoid physics explosion.
            if (deltaNs > 120_000_000L) deltaNs = 120_000_000L
            if (deltaNs < 0) deltaNs = 0
            last = now
            val deltaScale = (deltaNs.toFloat() / GameConfig.FRAME_NS.toFloat()).coerceIn(0.5f, 2.5f)
            if (holder.surface.isValid) {
                update(deltaScale)
                drawGame()
            }
            val spentNs = System.nanoTime() - now
            val sleepNs = GameConfig.FRAME_NS - spentNs
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                    break
                }
            }
            // If far behind, reset clock to avoid spiral of death.
            if (spentNs > GameConfig.FRAME_NS * 3) last = System.nanoTime()
        }
    }

    private fun update(deltaScale: Float) {
        when (state) {
            GameState.MENU -> engine.updateMenuDrift(deltaScale)
            GameState.PLAYING -> {
                val events = engine.updatePlaying(deltaScale)
                if (events.collected) {
                    sound.playCollect(engine.combo)
                    try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) { }
                }
                if (events.nearMiss) sound.playNearMiss()
                val nowMs = System.currentTimeMillis()
                if ((engine.score != lastScoreSent || engine.combo != lastComboSent) && nowMs - lastHudTime > 90) {
                    lastScoreSent = engine.score
                    lastComboSent = engine.combo
                    lastHudTime = nowMs
                    combo = engine.combo
                    post {
                        listener?.onScore(engine.score, bestScore)
                        listener?.onCombo(engine.combo)
                    }
                }
                if (events.gameOver) {
                    state = GameState.GAME_OVER
                    sound.playDeath()
                    sound.stopMusic()
                    try { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Exception) { }
                    val final = engine.score
                    post { listener?.onGameOver(final) }
                }
            }
            GameState.PAUSED, GameState.GAME_OVER -> {
                engine.particles.update()
                val it = engine.floatTexts.iterator()
                while (it.hasNext()) {
                    val t = it.next()
                    t.life -= 1f
                    if (t.life <= 0) it.remove()
                }
                if (engine.shake > 0f) engine.shake = (engine.shake - 0.9f).coerceAtLeast(0f)
            }
        }
    }

    // ---- Input: tap down to jump (buffered), release to cut jump ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == GameState.PLAYING) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (engine.ghost.pressJump()) {
                        sound.playJump()
                        engine.particles.emit(engine.ghost.x + 10, engine.ghost.y + 40, Color.WHITE, 5, 2f)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    engine.ghost.releaseJump()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // ---- Rendering: full-bleed background + centered 1280x720 playfield ----

    private fun drawGame() {
        val canvas = try { holder.lockCanvas() } catch (_: Exception) { return } ?: return
        try {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            if (w <= 0 || h <= 0) return
            val scale = minOf(w / GameConfig.LOGICAL_WIDTH, h / GameConfig.LOGICAL_HEIGHT)
            val dx = (w - GameConfig.LOGICAL_WIDTH * scale) / 2f
            val dy = (h - GameConfig.LOGICAL_HEIGHT * scale) / 2f
            // Fill entire screen first (no black bars): reuse gradient stretched.
            canvas.save()
            canvas.drawColor(Color.rgb(10, 15, 36))
            canvas.restore()

            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            // Expand background to cover letterbox bars.
            val exX = if (scale > 0) dx / scale else 0f
            val exY = if (scale > 0) dy / scale else 0f

            // Screen shake offset.
            if (engine.shake > 0.5f) {
                val s = engine.shake
                canvas.translate(
                    (rng.nextFloat() * 2 - 1) * s * 0.5f,
                    (rng.nextFloat() * 2 - 1) * s * 0.4f
                )
            }

            drawBackground(canvas, exX, exY)
            when (state) {
                GameState.MENU -> {
                    // Ambient: show drifting world behind menu overlay.
                    drawHills(canvas, exX)
                    drawGround(canvas, exX)
                }
                GameState.PLAYING, GameState.PAUSED -> {
                    drawTrail(canvas)
                    drawGhost(canvas)
                    engine.obstacles.forEach { drawObstacle(canvas, it) }
                    engine.orbs.forEach { drawOrb(canvas, it) }
                    drawParticles(canvas)
                    drawFloatTexts(canvas)
                }
                GameState.GAME_OVER -> {
                    drawGhost(canvas)
                    engine.obstacles.forEach { drawObstacle(canvas, it) }
                    drawParticles(canvas)
                    drawFloatTexts(canvas)
                }
            }
            canvas.restore()
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
        }
    }

    private fun drawBackground(canvas: Canvas, exX: Float, exY: Float) {
        if (bgPaint.shader == null) {
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, GameConfig.LOGICAL_HEIGHT,
                Color.rgb(18, 22, 55), Color.rgb(52, 62, 115),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(
            -exX, -exY,
            GameConfig.LOGICAL_WIDTH + exX, GameConfig.LOGICAL_HEIGHT + exY, bgPaint
        )
        // Stars (twinkle).
        val t = System.currentTimeMillis() / 500f
        for (s in stars) {
            val a = 140 + (sin(t + s.tw) * 70).toInt()
            starPaint.alpha = a.coerceIn(60, 230)
            var sx = (s.x + engine.backgroundX * 0.12f) % GameConfig.LOGICAL_WIDTH
            if (sx < 0) sx += GameConfig.LOGICAL_WIDTH
            canvas.drawCircle(sx, s.y, s.r, starPaint)
        }
        // Moon.
        canvas.drawCircle(1080f, 130f, 62f, moonGlowPaint)
        canvas.drawCircle(1080f, 130f, 40f, moonPaint)
        canvas.drawCircle(1066f, 118f, 8f, fogPaint)
        // Fog band.
        canvas.drawRect(-exX, 400f, GameConfig.LOGICAL_WIDTH + exX, 560f, fogPaint)
        // Clouds (2 depths).
        for (i in 0 until 5) {
            var cx = (engine.backgroundX * 0.3f + i * 420f) % (GameConfig.LOGICAL_WIDTH + 240f)
            if (cx < 0) cx += GameConfig.LOGICAL_WIDTH + 240f
            cx -= 120f
            val cy = 90f + i * 42f + sin(engine.hillX * 0.004f + i * 1.7f) * 14f
            canvas.drawOval(cx, cy, cx + 110f, cy + 42f, cloudPaint)
        }
        drawHills(canvas, exX)
        drawGround(canvas, exX)
    }

    private fun drawHills(canvas: Canvas, exX: Float) {
        // Far hills.
        path.reset()
        val baseY = GameConfig.LOGICAL_HEIGHT - GameConfig.GROUND_HEIGHT
        path.moveTo(-exX, baseY)
        var x = -exX
        while (x <= GameConfig.LOGICAL_WIDTH + exX) {
            val y = baseY - 70f - sin((x + engine.backgroundX * 0.6f) * 0.008f) * 38f
            path.lineTo(x, y)
            x += 42f
        }
        path.lineTo(GameConfig.LOGICAL_WIDTH + exX, baseY)
        path.close()
        canvas.drawPath(path, hillPaint)
        // Near hills.
        path.reset()
        path.moveTo(-exX, baseY)
        x = -exX
        while (x <= GameConfig.LOGICAL_WIDTH + exX) {
            val y = baseY - 26f - sin((x + engine.hillX * 0.7f) * 0.012f + 2f) * 30f
            path.lineTo(x, y)
            x += 36f
        }
        path.lineTo(GameConfig.LOGICAL_WIDTH + exX, baseY)
        path.close()
        canvas.drawPath(path, hill2Paint)
    }

    private fun drawGround(canvas: Canvas, exX: Float) {
        val groundTop = GameConfig.LOGICAL_HEIGHT - GameConfig.GROUND_HEIGHT
        canvas.drawRect(-exX, groundTop, GameConfig.LOGICAL_WIDTH + exX, GameConfig.LOGICAL_HEIGHT + 200f, groundPaint)
        canvas.drawLine(-exX, groundTop, GameConfig.LOGICAL_WIDTH + exX, groundTop, groundLinePaint)
        // Scrolling stripes to convey speed.
        var gx = (engine.hillX % 120f)
        var sx = -exX + gx - 120f
        while (sx < GameConfig.LOGICAL_WIDTH + exX) {
            canvas.drawRect(sx, groundTop + 22f, sx + 56f, groundTop + 30f, groundStripePaint)
            sx += 120f
        }
    }

    private fun drawTrail(canvas: Canvas) {
        val g = engine.ghost
        var i = 0
        for ((tx, ty) in g.trail) {
            if (i == 0) { i++; continue }
            val a = (70 * (1f - i / 12f)).toInt().coerceIn(8, 70)
            trailPaint.alpha = a
            canvas.drawCircle(tx + 22, ty + 22 + sin(g.floatOffset + i * 0.4f) * 2f, 16f - i, trailPaint)
            i++
            if (i > 7) break
        }
    }

    private fun drawGhost(canvas: Canvas) {
        val g = engine.ghost
        val drawY = g.y + sin(g.floatOffset) * 5
        // Squash & stretch.
        val sq = g.squash.coerceIn(0f, 1f)
        val stretch = (g.velY * -0.008f).coerceIn(-0.22f, 0.3f)
        val sx = 1f - sq * 0.14f + stretch * 0.4f
        val sy = 1f + sq * 0.16f - stretch * 0.5f
        canvas.save()
        canvas.translate(g.x + 22, drawY + 22)
        canvas.scale(sx, sy)
        canvas.translate(-22f, -22f)
        canvas.drawCircle(22f, 22f, 30f, ghostGlowPaint)
        canvas.drawCircle(22f, 22f, 22f, ghostPaint)
        path.reset()
        path.moveTo(0f, 22f)
        for (i in 0 until 5) {
            val tx = (i * 44f / 4f)
            val ty = 44f - 5f + sin(g.floatOffset * 0.5f + i) * 8f - 22f
            path.lineTo(tx, ty + 22f - 22f + 22f - 0f)
        }
        // Tail relative to centered coords.
        path.reset()
        path.moveTo(-22f, 0f)
        for (i in 0 until 5) {
            val tx = -22f + (i * 44f / 4f)
            val ty = 22f - 5f + sin(g.floatOffset * 0.5f + i) * 8f
            path.lineTo(tx, ty)
        }
        path.lineTo(22f, 0f)
        path.close()
        canvas.drawPath(path, ghostPaint)
        // Eyes look slightly forward/up with velocity.
        val look = (g.velY * -0.02f).coerceIn(-3f, 3f)
        val eyeY = -7f + look
        canvas.drawOval(-14f, eyeY, -4f, eyeY + 14f, blackPaint)
        canvas.drawOval(6f, eyeY, 16f, eyeY + 14f, blackPaint)
        canvas.drawCircle(-12f, eyeY + 4f, 3f, whitePaint)
        canvas.drawCircle(8f, eyeY + 4f, 3f, whitePaint)
        canvas.restore()
    }

    private fun drawObstacle(canvas: Canvas, o: com.ghostrun.game.game.entities.Obstacle) {
        when (o) {
            is com.ghostrun.game.game.entities.Obstacle.Tree -> {
                canvas.drawRect(o.x + 12, o.y + 40, o.x + 28, o.y + 90, trunkPaint)
                path.reset()
                path.moveTo(o.x + 20, o.y); path.lineTo(o.x, o.y + 60); path.lineTo(o.x + 40, o.y + 60); path.close()
                canvas.drawPath(path, leafPaint)
                path.reset()
                path.moveTo(o.x + 20, o.y - 20); path.lineTo(o.x + 5, o.y + 30); path.lineTo(o.x + 35, o.y + 30); path.close()
                canvas.drawPath(path, leaf2Paint)
                // Spooky eyes in foliage.
                canvas.drawCircle(o.x + 14, o.y + 22, 2.5f, redPaint)
                canvas.drawCircle(o.x + 26, o.y + 22, 2.5f, redPaint)
            }
            is com.ghostrun.game.game.entities.Obstacle.Rock -> {
                path.reset()
                path.moveTo(o.x + 4, o.y + 38); path.lineTo(o.x + 10, o.y + 12)
                path.lineTo(o.x + 30, o.y + 4); path.lineTo(o.x + 46, o.y + 18)
                path.lineTo(o.x + 42, o.y + 38); path.close()
                canvas.drawPath(path, rockPaint)
                canvas.drawCircle(o.x + 20, o.y + 16, 6f, rockHiPaint)
            }
            is com.ghostrun.game.game.entities.Obstacle.Bat -> {
                val bodyY = o.y + sin(o.wingFlap) * 8
                val wingY = bodyY - cos(o.wingFlap) * 16
                path.reset()
                path.moveTo(o.x + 20, bodyY); path.lineTo(o.x - 6, wingY); path.lineTo(o.x + 15, bodyY + 10); path.close()
                canvas.drawPath(path, batPaint)
                path.reset()
                path.moveTo(o.x + 20, bodyY); path.lineTo(o.x + 46, wingY); path.lineTo(o.x + 25, bodyY + 10); path.close()
                canvas.drawPath(path, batPaint)
                canvas.drawCircle(o.x + 20, bodyY, 10f, batBodyPaint)
                canvas.drawCircle(o.x + 17, bodyY - 2, 2.4f, redPaint)
                canvas.drawCircle(o.x + 23, bodyY - 2, 2.4f, redPaint)
            }
        }
    }

    private fun drawOrb(canvas: Canvas, orb: com.ghostrun.game.game.entities.Orb) {
        if (orb.collected) return
        val s = 1f + sin(orb.glowTimer) * 0.12f
        val size = 12f * s
        canvas.drawCircle(orb.x + 12, orb.y + 12, size + 9, goldGlowPaint)
        canvas.drawCircle(orb.x + 12, orb.y + 12, size, goldPaint)
        canvas.drawCircle(orb.x + 12, orb.y + 12, size / 2, whitePaint)
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in engine.particles.particles) {
            if (!p.alive) continue
            particlePaint.color = p.color
            particlePaint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size.coerceAtLeast(1f), particlePaint)
        }
    }

    private fun drawFloatTexts(canvas: Canvas) {
        for (t in engine.floatTexts) {
            textPaint.alpha = ((t.life / 50f) * 255).toInt().coerceIn(0, 255)
            canvas.drawText(t.text, t.x, t.y, textPaint)
        }
    }
}
