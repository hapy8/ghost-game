package com.ghostrun.game.game

import android.graphics.RectF
import com.ghostrun.game.game.entities.Ghost
import com.ghostrun.game.game.entities.Obstacle
import com.ghostrun.game.game.entities.Orb
import com.ghostrun.game.game.entities.ParticleSystem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class EngineEvents(val gameOver: Boolean, val collected: Boolean, val nearMiss: Boolean = false)
data class FloatText(var x: Float, var y: Float, var text: String, var life: Float = 50f)

class GameEngine {
    val ghost = Ghost()
    val obstacles = ArrayList<Obstacle>()
    val orbs = ArrayList<Orb>()
    val particles = ParticleSystem()
    val floatTexts = ArrayList<FloatText>()
    var score = 0
    var difficulty = 1f
    var backgroundX = 0f
    var hillX = 0f
    var obstacleTimer = 0
    var orbTimer = 0
    var distance = 0f
    var orbsCollected = 0
    var combo = 0
    var comboTimer = 0
    var shake = 0f
    var nextObstacleIn = 90
    var nextOrbIn = 200
    private val random = Random

    fun reset() {
        ghost.reset()
        obstacles.clear()
        orbs.clear()
        particles.clear()
        floatTexts.clear()
        score = 0
        difficulty = 1f
        backgroundX = 0f
        hillX = 0f
        obstacleTimer = 0
        orbTimer = 0
        distance = 0f
        orbsCollected = 0
        combo = 0
        comboTimer = 0
        shake = 0f
        nextObstacleIn = 80
        nextOrbIn = 160
    }

    fun updatePlaying(deltaScale: Float = 1f): EngineEvents {
        val dt = deltaScale.coerceIn(0.5f, 2.5f)
        ghost.update(dt)
        if (ghost.justLanded) {
            particles.emit(ghost.x + 22, ghost.y + ghost.height, 0xFF9DB89D.toInt(), 6, 2f)
        }
        difficulty = min(GameConfig.MAX_DIFFICULTY, 1f + score / 2000f)
        var collected = false
        var nearMiss = false

        val obsIter = obstacles.iterator()
        while (obsIter.hasNext()) {
            val o = obsIter.next()
            o.update(dt)
            if (o.x + o.width < -60) {
                obsIter.remove()
                score += GameConfig.SCORE_PER_DODGE
            } else if (!o.passed && o.x + o.width < ghost.x) {
                o.passed = true
                score += GameConfig.SCORE_PER_DODGE
                // Near-miss: dodged but vertically close -> bonus + juice.
                val ghostCy = ghost.y + ghost.height / 2
                val oCy = o.y + o.height / 2
                if (abs(ghostCy - oCy) < 95f) {
                    score += GameConfig.NEAR_MISS_BONUS
                    nearMiss = true
                    floatTexts.add(FloatText(ghost.x + 60, ghost.y - 10, "CLOSE! +${GameConfig.NEAR_MISS_BONUS}"))
                }
            }
        }

        val ghostCx = ghost.x + ghost.width / 2
        val ghostCy = ghost.y + ghost.height / 2
        val orbIter = orbs.iterator()
        while (orbIter.hasNext()) {
            val orb = orbIter.next()
            orb.update(ghostCx, ghostCy, true)
            if (orb.x + orb.width < -40) orbIter.remove()
        }

        obstacleTimer++
        if (obstacleTimer >= nextObstacleIn) {
            val right = Obstacle.rightmostX(obstacles)
            // +300 keeps spawns off-screen even on wide 20:9 with full-bleed bars.
            val spawnX = GameConfig.LOGICAL_WIDTH + 300f
            // Enforce fair gap so patterns are always jumpable.
            if (right < spawnX - GameConfig.MIN_OBSTACLE_GAP || obstacles.isEmpty()) {
                obstacles.add(Obstacle.spawn(spawnX, score, difficulty, random))
                obstacleTimer = 0
                val base = max(42, 102 - score / 48)
                nextObstacleIn = random.nextInt(base, base + 58)
            } else {
                obstacleTimer = nextObstacleIn - 10
            }
        }

        orbTimer++
        if (orbTimer >= nextOrbIn) {
            // Spawn orb arcs above ground, sometimes in pairs for combos.
            orbs.add(Orb(GameConfig.LOGICAL_WIDTH + 280, difficulty, random))
            if (score > 250 && random.nextFloat() < 0.35f) {
                val second = Orb(GameConfig.LOGICAL_WIDTH + 370, difficulty, random)
                second.y = (second.y - 90f).coerceAtLeast(140f)
                orbs.add(second)
            }
            orbTimer = 0
            nextOrbIn = random.nextInt(150, 280)
        }

        // Collisions: ghost vs obstacles (slightly forgiving hitbox).
        val hitInset = 7f
        val ghostHit = RectF(
            ghost.rect.left + hitInset, ghost.rect.top + hitInset,
            ghost.rect.right - hitInset, ghost.rect.bottom - 4f
        )
        for (o in obstacles) {
            if (RectF.intersects(ghostHit, o.rect)) {
                particles.emit(ghostCx, ghostCy, 0xFFDCDCFF.toInt(), 22, 5f)
                shake = 14f
                return EngineEvents(gameOver = true, collected = false, nearMiss = false)
            }
        }
        // Ghost vs orbs
        val hit = orbs.firstOrNull { !it.collected && RectF.intersects(ghostHit, it.rect) }
        if (hit != null) {
            hit.collected = true
            orbs.remove(hit)
            combo++
            comboTimer = GameConfig.COMBO_WINDOW_FRAMES
            val bonus = (combo - 1) * 10
            val gained = GameConfig.SCORE_PER_ORB + bonus
            score += gained
            orbsCollected++
            particles.emit(hit.x, hit.y, 0xFFFFD700.toInt(), 14, 4f)
            val label = if (combo > 1) "+$gained x$combo" else "+$gained"
            floatTexts.add(FloatText(hit.x - 10, hit.y - 24, label))
            collected = true
        }

        if (comboTimer > 0) {
            comboTimer -= dt.toInt().coerceAtLeast(1)
            if (comboTimer <= 0) combo = 0
        }

        backgroundX -= 2f * difficulty * dt
        if (backgroundX <= -GameConfig.LOGICAL_WIDTH) backgroundX = 0f
        hillX -= 3.4f * difficulty * dt
        if (hillX <= -GameConfig.LOGICAL_WIDTH) hillX = 0f
        distance += 4f * difficulty * dt
        if (shake > 0f) shake = (shake - 0.9f * dt).coerceAtLeast(0f)

        val ftIter = floatTexts.iterator()
        while (ftIter.hasNext()) {
            val t = ftIter.next()
            t.y -= 0.9f * dt
            t.life -= 1f * dt
            if (t.life <= 0) ftIter.remove()
        }
        particles.update()
        return EngineEvents(gameOver = false, collected = collected, nearMiss = nearMiss)
    }

    fun updateMenuDrift(deltaScale: Float = 1f) {
        val dt = deltaScale.coerceIn(0.5f, 2.5f)
        backgroundX -= 1f * dt
        if (backgroundX <= -GameConfig.LOGICAL_WIDTH) backgroundX = 0f
        hillX -= 1.6f * dt
        if (hillX <= -GameConfig.LOGICAL_WIDTH) hillX = 0f
    }

    fun updateMenuDrift() = updateMenuDrift(1f)
}
