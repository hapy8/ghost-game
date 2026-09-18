package com.ghostrun.game.game.entities

import kotlin.math.max
import kotlin.random.Random

data class Particle(
    var x: Float = 0f, var y: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f,
    var size: Float = 0f, var life: Float = 0f,
    var maxLife: Float = 1f, var color: Int = 0
) {
    fun init(px: Float, py: Float, pvx: Float, pvy: Float, psize: Float, plife: Float, pcolor: Int) {
        x = px; y = py; vx = pvx; vy = pvy
        size = psize; life = plife; maxLife = plife; color = pcolor
    }

    fun update() {
        x += vx; y += vy
        vy += 0.12f
        vx *= 0.99f
        life -= 1f
        size = max(0f, size * 0.95f)
    }

    val alive get() = life > 0
    val alpha get() = ((life / maxLife) * 255).toInt().coerceIn(0, 255)
}

/** Pooled particle system. Zero allocation after warmup. */
class ParticleSystem {
    val particles = ArrayList<Particle>(256)
    private val pool = ArrayList<Particle>(400)
    private val random = Random

    init {
        repeat(120) { pool.add(Particle()) }
    }

    fun emit(x: Float, y: Float, color: Int, count: Int = 10, speed: Float = 2f) {
        repeat(count) {
            val p = if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else {
                if (particles.size >= 400) {
                    // Recycle oldest live particle instead of allocating.
                    particles.removeAt(0)
                }
                Particle()
            }
            p.init(
                x, y,
                (random.nextFloat() * 2 - 1) * speed,
                (random.nextFloat() * 2 - 1) * speed - 1f,
                4f + random.nextFloat() * 3f, 26f + random.nextFloat() * 10f, color
            )
            particles.add(p)
        }
        // Hard cap without subList allocation churn.
        while (particles.size > 400) {
            pool.add(particles.removeAt(0))
        }
    }

    fun update() {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.update()
            if (!p.alive) {
                particles.removeAt(i)
                if (pool.size < 400) pool.add(p)
            } else {
                i++
            }
        }
    }

    fun clear() {
        if (pool.size < 400) {
            for (p in particles) if (pool.size < 400) pool.add(p)
        }
        particles.clear()
    }
}
