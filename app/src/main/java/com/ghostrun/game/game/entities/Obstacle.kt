package com.ghostrun.game.game.entities

import android.graphics.RectF
import com.ghostrun.game.game.GameConfig
import kotlin.random.Random

sealed class Obstacle(
    var x: Float,
    val width: Float,
    val height: Float,
    var y: Float,
    var speed: Float
) {
    var passed = false
    var wingFlap = 0f
    val rect = RectF()

    fun update(deltaScale: Float = 1f) {
        x -= speed * deltaScale
        wingFlap += 0.4f * deltaScale
        syncRect()
    }

    fun update() = update(1f)

    fun syncRect() {
        rect.set(x, y, x + width, y + height)
    }

    class Tree(x: Float, speedMult: Float) : Obstacle(
        x, 40f, 90f,
        GameConfig.LOGICAL_HEIGHT - GameConfig.GROUND_HEIGHT - 90f,
        (GameConfig.OBSTACLE_BASE_SPEED * speedMult).coerceAtMost(24f)
    )

    class Rock(x: Float, speedMult: Float) : Obstacle(
        x, 50f, 40f,
        GameConfig.LOGICAL_HEIGHT - GameConfig.GROUND_HEIGHT - 40f,
        (GameConfig.OBSTACLE_BASE_SPEED * speedMult).coerceAtMost(24f)
    )

    class Bat(x: Float, speedMult: Float, random: Random = Random) : Obstacle(
        x, 40f, 30f,
        // Vary height so bats force different jump timings, but stay above ground dash.
        GameConfig.LOGICAL_HEIGHT - GameConfig.GROUND_HEIGHT - random.nextInt(130, 231).toFloat(),
        (GameConfig.OBSTACLE_BASE_SPEED * 1.15f * speedMult).coerceAtMost(26f)
    )

    companion object {
        fun spawn(x: Float, score: Int, speedMult: Float, random: Random = Random): Obstacle {
            val weights = if (score > 500) intArrayOf(30, 30, 40) else intArrayOf(40, 40, 20)
            val roll = random.nextInt(100)
            return when {
                roll < weights[0] -> Tree(x, speedMult)
                roll < weights[0] + weights[1] -> Rock(x, speedMult)
                else -> Bat(x, speedMult, random)
            }.also { it.syncRect() }
        }

        /** Rightmost obstacle edge, for fair-gap enforcement. */
        fun rightmostX(list: List<Obstacle>): Float {
            var m = -1f
            for (o in list) m = maxOf(m, o.x + o.width)
            return m
        }
    }
}
