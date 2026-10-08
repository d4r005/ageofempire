package com.d4r005.ageofempire

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Efectos de sonido: SoundPool con anti-spam por sonido. */
class SoundManager(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<String, Int>()
    private val lastPlayed = HashMap<String, Long>()
    private val minIntervalMs = mapOf(
        "chop" to 150L, "hit" to 150L, "arrow" to 120L, "unit_ready" to 200L
    )

    init {
        for (name in listOf("chop", "hit", "arrow", "build_done", "unit_ready", "raid", "win", "lose")) {
            val resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId != 0) ids[name] = pool.load(context, resId, 1)
        }
    }

    fun play(name: String, volume: Float = 0.9f) {
        val id = ids[name] ?: return
        val now = System.currentTimeMillis()
        val last = lastPlayed[name] ?: 0L
        if (now - last < (minIntervalMs[name] ?: 60L)) return
        lastPlayed[name] = now
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
