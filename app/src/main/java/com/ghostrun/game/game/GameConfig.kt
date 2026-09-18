package com.ghostrun.game.game

/** Single source of truth for tuning. Change gameplay here, never in the loop. */
object GameConfig {
    const val LOGICAL_WIDTH = 1280f
    const val LOGICAL_HEIGHT = 720f
    const val GROUND_HEIGHT = 100f
    const val FPS = 60L
    const val FRAME_MS = 1000L / FPS
    const val FRAME_NS = 1_000_000_000L / FPS

    const val GRAVITY = 1.2f
    const val FALL_GRAVITY_MULT = 1.45f
    const val JUMP_POWER = -22f
    const val JUMP_CUT_MULT = 0.45f
    const val MAX_JUMPS = 2
    const val COYOTE_FRAMES = 7
    const val BUFFER_FRAMES = 8

    const val OBSTACLE_BASE_SPEED = 8f
    const val MAX_DIFFICULTY = 3.0f
    const val MIN_OBSTACLE_GAP = 420f
    const val ORB_BASE_SPEED = 5f
    const val MAGNET_RADIUS = 170f

    const val SCORE_PER_DODGE = 10
    const val SCORE_PER_ORB = 50
    const val NEAR_MISS_BONUS = 15
    const val COMBO_WINDOW_FRAMES = 420
}
