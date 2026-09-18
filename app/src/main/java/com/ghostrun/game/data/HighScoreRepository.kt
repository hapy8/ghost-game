package com.ghostrun.game.data

import android.content.Context

class HighScoreRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ghost_run", Context.MODE_PRIVATE)

    var highScore: Int = prefs.getInt(KEY, 0)
        private set

    /** Saves if [score] beats best. Returns true when it is a new best. */
    fun saveIfBest(score: Int): Boolean {
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt(KEY, score).apply()
            return true
        }
        return false
    }

    companion object {
        private const val KEY = "highscore"
    }
}
