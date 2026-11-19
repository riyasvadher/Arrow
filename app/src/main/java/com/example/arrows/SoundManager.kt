package com.example.arrows

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundManager(context: Context) {

    enum class Sfx { SLIDE, COLLISION, GAME_OVER, WIN }

    var enabled = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = mapOf(
        Sfx.SLIDE to pool.load(context, R.raw.sfx_slide, 1),
        Sfx.COLLISION to pool.load(context, R.raw.sfx_collision, 1),
        Sfx.GAME_OVER to pool.load(context, R.raw.sfx_game_over, 1),
        Sfx.WIN to pool.load(context, R.raw.sfx_win, 1)
    )

    fun play(sfx: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!enabled) return
        ids[sfx]?.let { pool.play(it, volume, volume, 1, 0, rate) }
    }

    fun release() = pool.release()
}
