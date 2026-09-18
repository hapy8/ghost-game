package com.ghostrun.game.game.entities

import android.graphics.RectF
import com.ghostrun.game.game.GameConfig

class Ghost {
    var x = 150f
    var y = GameConfig.LOGICAL_HEIGHT / 2f
    val width = 44f
    val height = 44f
    var velY = 0f
    var onGround = false
    var floatOffset = 0f
    private val floatSpeed = 0.15f
    val rect = RectF()

    // Feel helpers
    private var coyote = 0
    private var buffer = 0
    private var jumpsUsed = 0
    var squash = 0f
    var justLanded = false
    val trail = ArrayList<Pair<Float, Float>>(14)

    fun reset() {
        y = GameConfig.LOGICAL_HEIGHT / 2f
        velY = 0f
        onGround = false
        floatOffset = 0f
        coyote = 0
        buffer = 0
        jumpsUsed = 0
        squash = 0f
        justLanded = false
        trail.clear()
        syncRect()
    }

    fun update(deltaScale: Float = 1f) {
        justLanded = false
        val wasAirborne = !onGround
        // Gravity: heavier when falling for snappier arc.
        val g = if (velY < 0) GameConfig.GRAVITY else GameConfig.GRAVITY * GameConfig.FALL_GRAVITY_MULT
        if (!onGround) {
            velY += g * deltaScale
            velY = velY.coerceAtMost(26f)
        }
        y += velY * deltaScale
        val groundLevel = GameConfig.LOGICAL_HEIGHT - GameConfig.GROUND_HEIGHT
        if (y + height >= groundLevel) {
            y = groundLevel - height
            if (wasAirborne && velY > 8f) justLanded = true
            velY = 0f
            if (!onGround) {
                onGround = true
                jumpsUsed = 0
                squash = 1f
            }
        } else {
            if (onGround) {
                // Walked off (shouldn't happen) -> start coyote.
                coyote = GameConfig.COYOTE_FRAMES
            }
            onGround = false
        }
        if (onGround) coyote = GameConfig.COYOTE_FRAMES
        else if (coyote > 0) coyote--

        if (buffer > 0) {
            buffer--
            tryConsumeJump()
        }
        if (squash > 0f) squash = (squash - 0.12f * deltaScale).coerceAtLeast(0f)

        floatOffset += floatSpeed * deltaScale
        trail.add(0, Pair(x, y))
        if (trail.size > 12) trail.removeAt(trail.size - 1)
        syncRect()
    }

    /** Called on tap down. Buffers when airborne. Returns true if jumped now. */
    fun pressJump(): Boolean {
        buffer = GameConfig.BUFFER_FRAMES
        return tryConsumeJump()
    }

    fun releaseJump() {
        if (velY < -6f) velY *= GameConfig.JUMP_CUT_MULT + 0.35f
        else if (velY < 0f) velY *= GameConfig.JUMP_CUT_MULT
    }

    private fun tryConsumeJump(): Boolean {
        val canGroundJump = onGround || coyote > 0
        val canAirJump = !onGround && jumpsUsed < GameConfig.MAX_JUMPS
        if (canGroundJump) {
            velY = GameConfig.JUMP_POWER
            onGround = false
            coyote = 0
            buffer = 0
            jumpsUsed = 1
            squash = 1f
            syncRect()
            return true
        }
        if (canAirJump) {
            // Double jump is slightly weaker for control.
            velY = GameConfig.JUMP_POWER * 0.88f
            jumpsUsed++
            buffer = 0
            squash = 1f
            syncRect()
            return true
        }
        return false
    }

    /** Returns true if a jump was performed. */
    fun jump(): Boolean = pressJump()

    private fun syncRect() {
        rect.set(x, y, x + width, y + height)
    }
}
