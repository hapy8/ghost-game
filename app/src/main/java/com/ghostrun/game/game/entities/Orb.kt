package com.ghostrun.game.game.entities

import android.graphics.RectF
import com.ghostrun.game.game.GameConfig
import kotlin.random.Random

class Orb(x: Float, speedMult: Float, random: Random = Random) {
    var x = x
    var y = random.nextInt(220, (GameConfig.LOGICAL_HEIGHT - 200).toInt()).toFloat()
    val width = 25f
    val height = 25f
    var speed = (GameConfig.ORB_BASE_SPEED * speedMult).coerceAtMost(16f)
    var collected = false
    var glowTimer = 0f
    val rect = RectF()

    fun update() {
        update(0f, 0f, false)
    }

    fun update(ghostCx: Float, ghostCy: Float, magnet: Boolean) {
        x -= speed
        if (magnet) {
            val cx = x + width / 2
            val cy = y + height / 2
            val dx = ghostCx - cx
            val dy = ghostCy - cy
            val dist2 = dx * dx + dy * dy
            val r = GameConfig.MAGNET_RADIUS
            if (dist2 < r * r && dist2 > 1f) {
                val dist = kotlin.math.sqrt(dist2)
                val pull = (1f - dist / r) * 9f + 2f
                x += (dx / dist) * pull
                y += (dy / dist) * pull
            }
        }
        glowTimer += 0.1f
        rect.set(x, y, x + width, y + height)
    }
}
