package com.ghostrun.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.sin

/**
 * Procedural, asset-free sound. Fail-safe: audio failures never crash the game.
 * Perf-fixed: pre-rendered buffers + pooled AudioTracks, no per-play allocation
 * or AudioTrack.Builder in the hot path.
 */
class SoundManager {
    var isMuted = false
    @Volatile private var musicPlaying = false
    private var musicThread: Thread? = null

    private val lock = Any()
    private val tracks = ArrayList<AudioTrack?>(4)
    private var sfxIndex = 1
    private val sfxPool = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "GhostRun-SFX").apply { isDaemon = true }
    }

    private val rate = 22050
    private var jumpBuf: ShortArray? = null
    private var collectBuf: ShortArray? = null
    private var nearBuf: ShortArray? = null
    private var deathBuf: ShortArray? = null
    private val bassBufs = HashMap<Int, ShortArray>()

    private fun ensureBuffers() {
        if (jumpBuf == null) {
            jumpBuf = makeSweep(300, 700, 140)
            collectBuf = makeComboTone()
            nearBuf = makeTone(1250, 90, 0.4f)
            deathBuf = makeSweep(520, 110, 320, 0.6f)
        }
    }

    private fun bassBuf(freq: Int): ShortArray {
        return bassBufs.getOrPut(freq) { makeTone(freq, 200, 0.5f) }
    }

    private fun ensureTracks(): Boolean {
        synchronized(lock) {
            if (tracks.size == 4) return tracks[0] != null
            try {
                val maxFrames = (rate * 400 / 1000).coerceAtLeast(2048)
                repeat(4) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                    val t = AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(maxFrames * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                        .build()
                    tracks.add(t)
                }
                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    fun playJump() {
        if (isMuted) return
        ensureBuffers()
        val buf = jumpBuf ?: return
        playPooled(buf, 0.5f, musicTrack = false)
    }

    fun playCollect(combo: Int = 1) {
        if (isMuted) return
        ensureBuffers()
        val buf = collectBuf ?: return
        // Pitch up slightly with combo by playback-rate scaling (resample-free: volume + speed via rate).
        playPooled(buf, 0.5f, musicTrack = false, rateScale = 1f + (combo.coerceAtMost(8) - 1) * 0.04f)
    }

    fun playNearMiss() {
        if (isMuted) return
        ensureBuffers()
        val buf = nearBuf ?: return
        playPooled(buf, 0.35f, musicTrack = false)
    }

    fun playDeath() {
        if (isMuted) return
        ensureBuffers()
        val buf = deathBuf ?: return
        playPooled(buf, 0.6f, musicTrack = false)
    }

    fun startMusic() {
        if (musicPlaying) return
        musicPlaying = true
        ensureBuffers()
        musicThread = Thread({
            val bass = intArrayOf(110, 130, 98, 146, 110, 130, 164, 146)
            var i = 0
            while (musicPlaying) {
                try {
                    if (!isMuted) {
                        val b = bassBuf(bass[i % bass.size])
                        playBlockingMusic(b)
                    } else {
                        Thread.sleep(210)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    try { Thread.sleep(210) } catch (_: InterruptedException) { break }
                }
                i++
            }
        }, "GhostRun-Music").apply { isDaemon = true; start() }
    }

    fun stopMusic() {
        musicPlaying = false
        try { musicThread?.interrupt() } catch (_: Exception) { }
        musicThread = null
    }

    fun release() {
        stopMusic()
        try { sfxPool.shutdownNow() } catch (_: Exception) { }
        synchronized(lock) {
            for (t in tracks) {
                try { t?.pause(); t?.flush(); t?.release() } catch (_: Exception) { }
            }
            tracks.clear()
        }
    }

    private fun playPooled(data: ShortArray, volume: Float, musicTrack: Boolean, rateScale: Float = 1f) {
        try {
            sfxPool.execute {
                try {
                    if (!ensureTracks()) return@execute
                    val track: AudioTrack?
                    synchronized(lock) {
                        if (musicTrack) {
                            track = tracks.getOrNull(0)
                        } else {
                            sfxIndex = 1 + (sfxIndex % 3)
                            track = tracks.getOrNull(sfxIndex)
                        }
                    }
                    if (track == null) return@execute
                    synchronized(track) {
                        try { track.pause(); track.flush() } catch (_: Exception) { }
                        try { track.setVolume(volume) } catch (_: Exception) { }
                        try {
                            if (rateScale != 1f) track.playbackRate = (rate * rateScale).toInt()
                            else track.playbackRate = rate
                        } catch (_: Exception) { }
                        try {
                            track.write(data, 0, data.size)
                            track.play()
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    private fun playBlockingMusic(data: ShortArray) {
        try {
            if (!ensureTracks()) {
                Thread.sleep(210); return
            }
            val track = synchronized(lock) { tracks.getOrNull(0) } ?: run {
                Thread.sleep(210); return
            }
            synchronized(track) {
                try { track.pause(); track.flush() } catch (_: Exception) { }
                try { track.setVolume(0.14f) } catch (_: Exception) { }
                try { track.playbackRate = rate } catch (_: Exception) { }
                try {
                    track.write(data, 0, data.size)
                    track.play()
                } catch (_: Exception) { }
            }
            Thread.sleep(210)
        } catch (_: InterruptedException) {
            throw _SQLException()
        } catch (_: Exception) {
            try { Thread.sleep(210) } catch (_: InterruptedException) { throw _SQLException() }
        }
    }

    private class _SQLException : InterruptedException()

    private fun makeTone(freqHz: Int, durationMs: Int, volume: Float = 0.5f): ShortArray {
        val frames = (rate * durationMs / 1000).coerceAtLeast(1)
        val data = ShortArray(frames)
        for (n in data.indices) {
            val env = 1f - n.toFloat() / frames
            data[n] = (sin(2.0 * Math.PI * freqHz * n / rate) * Short.MAX_VALUE * volume * env).toInt().toShort()
        }
        return data
    }

    private fun makeComboTone(): ShortArray {
        val a = makeTone(880, 80, 0.5f)
        val b = makeTone(1320, 110, 0.5f)
        val out = ShortArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }

    private fun makeSweep(fromHz: Int, toHz: Int, durationMs: Int, volume: Float = 0.5f): ShortArray {
        val frames = (rate * durationMs / 1000).coerceAtLeast(1)
        val data = ShortArray(frames)
        for (n in data.indices) {
            val t = n.toFloat() / frames
            val f = fromHz + (toHz - fromHz) * t
            data[n] = (sin(2.0 * Math.PI * f * n / rate) * Short.MAX_VALUE * volume * (1f - t)).toInt().toShort()
        }
        return data
    }
}
