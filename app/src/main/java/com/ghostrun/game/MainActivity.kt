package com.ghostrun.game

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ghostrun.game.data.HighScoreRepository
import com.ghostrun.game.databinding.ActivityMainBinding
import com.ghostrun.game.game.GameState
import com.ghostrun.game.game.GameView

class MainActivity : AppCompatActivity(), GameView.GameListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var highScores: HighScoreRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()
        applyEdgeToEdgeInsets()

        highScores = HighScoreRepository(this)
        binding.gameView.bestScore = highScores.highScore
        binding.gameView.listener = this
        updateHud(0, highScores.highScore)
        updateMenuBest()

        binding.startButton.setOnClickListener { startPlaying() }
        binding.pauseButton.setOnClickListener { pausePlaying() }
        binding.resumeButton.setOnClickListener { resumePlaying() }
        binding.restartButton.setOnClickListener { startPlaying() }
        binding.pauseMenuButton.setOnClickListener { toMenu() }
        binding.gameOverMenuButton.setOnClickListener { toMenu() }
        binding.muteButton.setOnClickListener { toggleMute() }
        binding.licensesButton.setOnClickListener { showLicenses() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (binding.gameView.state) {
                    GameState.PLAYING -> pausePlaying()
                    GameState.PAUSED -> resumePlaying()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // HUD respects notch + status bar; overlays keep at least cutout padding.
            binding.hud.setPadding(
                12 + bars.left, 12 + bars.top, 12 + bars.right, 12
            )
            binding.menuScroll.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            binding.pauseOverlay.setPadding(24 + bars.left, 24 + bars.top, 24 + bars.right, 24 + bars.bottom)
            insets
        }
    }

    private fun startPlaying() {
        binding.gameView.startGame()
        binding.menuScroll.visibility = View.GONE
        binding.pauseOverlay.visibility = View.GONE
        binding.gameOverOverlay.visibility = View.GONE
        binding.hud.visibility = View.VISIBLE
        binding.comboText.visibility = View.GONE
        hideSystemBars()
    }

    private fun pausePlaying() {
        if (binding.gameView.state != GameState.PLAYING) return
        binding.gameView.pauseGame()
        binding.pauseOverlay.visibility = View.VISIBLE
    }

    private fun resumePlaying() {
        binding.gameView.resumeGame()
        binding.pauseOverlay.visibility = View.GONE
        hideSystemBars()
    }

    private fun toMenu() {
        binding.gameView.toMenu()
        updateMenuBest()
        binding.menuScroll.visibility = View.VISIBLE
        binding.pauseOverlay.visibility = View.GONE
        binding.gameOverOverlay.visibility = View.GONE
        binding.hud.visibility = View.GONE
    }

    private fun toggleMute() {
        val sound = binding.gameView.sound
        sound.isMuted = !sound.isMuted
        binding.muteButton.text = if (sound.isMuted) "MUTE" else "VOL"
    }

    private fun showLicenses() {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_licenses)
            .setMessage(R.string.licenses_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateHud(score: Int, best: Int) {
        binding.scoreText.text = getString(R.string.label_score, score)
        binding.bestText.text = getString(R.string.label_best, best)
    }

    private fun updateMenuBest() {
        binding.menuBestText.text = getString(R.string.label_best, highScores.highScore)
    }

    // ---- GameView.GameListener (already on UI thread via post) ----

    override fun onScore(score: Int, best: Int) {
        updateHud(score, maxOf(best, highScores.highScore))
    }

    override fun onCombo(combo: Int) {
        if (combo > 1) {
            binding.comboText.visibility = View.VISIBLE
            binding.comboText.text = getString(R.string.label_combo, combo)
        } else {
            binding.comboText.visibility = View.GONE
        }
    }

    override fun onGameOver(score: Int) {
        val isBest = highScores.saveIfBest(score)
        binding.gameView.bestScore = highScores.highScore
        binding.finalScoreText.text = getString(R.string.label_final_score, score)
        binding.newBestText.visibility = if (isBest && score > 0) View.VISIBLE else View.GONE
        val eng = binding.gameView.engine
        val distM = (eng.distance / 50f).toInt()
        binding.gameOverStats.text = getString(R.string.label_stats, eng.orbsCollected, distM)
        binding.gameOverOverlay.visibility = View.VISIBLE
        binding.hud.visibility = View.GONE
        updateHud(score, highScores.highScore)
        updateMenuBest()
    }

    override fun onPause() {
        super.onPause()
        if (binding.gameView.state == GameState.PLAYING) pausePlaying()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        binding.gameView.shutdown()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
