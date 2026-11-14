package com.airpodsultimate

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

class SoundEffectsEngine {
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    val hasEqualizer: Boolean get() = equalizer != null
    val hasBassBoost: Boolean get() = bass != null
    val hasVirtualizer: Boolean get() = virtualizer != null

    fun enable(): Boolean {
        return try {
            if (equalizer == null && bass == null && virtualizer == null) {
                try { equalizer = Equalizer(0, 0) } catch (_: Exception) {}
                try { bass = BassBoost(0, 0) } catch (_: Exception) {}
                try { virtualizer = Virtualizer(0, 0) } catch (_: Exception) {}
            }
            equalizer?.enabled = true
            bass?.enabled = false
            virtualizer?.enabled = false
            hasEqualizer || hasBassBoost || hasVirtualizer
        } catch (_: Exception) {
            false
        }
    }

    fun disable() {
        try { equalizer?.enabled = false } catch (_: Exception) {}
        try { bass?.enabled = false } catch (_: Exception) {}
        try { virtualizer?.enabled = false } catch (_: Exception) {}
    }

    fun setBand(index: Int, value: Int) {
        try {
            val eq = equalizer ?: return
            if (index >= eq.numberOfBands) return
            val range = eq.bandLevelRange
            val level = (range[0].toInt() + (value / 120f * (range[1] - range[0])).toInt())
                .coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(index.toShort(), level.toShort())
        } catch (_: Exception) {}
    }

    fun setBassBoost(enabled: Boolean) {
        try {
            val effect = bass ?: return
            effect.setStrength(if (enabled) 350.toShort() else 0.toShort())
            effect.enabled = enabled
        } catch (_: Exception) {}
    }

    fun setVirtualizer(enabled: Boolean) {
        try {
            val effect = virtualizer ?: return
            effect.setStrength(if (enabled) 250.toShort() else 0.toShort())
            effect.enabled = enabled
        } catch (_: Exception) {}
    }

    fun applyPreset(name: String) {
        val values = when (name) {
            "Rock" -> listOf(35, 20, -5, 20, 35)
            "Vocal" -> listOf(-15, 0, 35, 30, 5)
            "Bass" -> listOf(45, 30, 0, -10, -10)
            else -> listOf(0, 0, 0, 0, 0)
        }
        values.forEachIndexed { index, value -> setBand(index, value) }
    }
}
