package com.example.arrows

import android.content.Context
import kotlin.math.max

object LevelConfig {

    class Cfg(val shapeName: String, val inside: Array<BooleanArray>, val minLen: Int, val maxLen: Int)

    /**
     * Level N uses silhouette N (100 different shapes). The shape grows and the arrows get longer as
     * the level goes up; detailed shapes (horse, giraffe, spider ...) are never made smaller than
     * the size at which they stay recognisable.
     */
    fun forLevel(context: Context, level: Int): Cfg {
        val shapes = ShapeLibrary.all(context)
        val shape = shapes[(level - 1).mod(shapes.size)]
        val base = (16 + level * 0.22f).toInt().coerceAtMost(38)
        val n = max(base, shape.minN).coerceAtMost(40)
        return Cfg(
            shapeName = shape.name,
            inside = ShapeLibrary.sample(shape, n),
            minLen = if (level < 4) 2 else 3,
            maxLen = (4 + level / 2).coerceAtMost(18)
        )
    }

    fun difficulty(level: Int): String = when {
        level <= 10 -> "Easy"
        level <= 30 -> "Medium"
        else -> "Hard"
    }
}
