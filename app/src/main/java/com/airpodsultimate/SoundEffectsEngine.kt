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
        if (equalizer == null && bass == null && virtualizer == null) {
            try { equalizer = Equalizer(0, 0) } catch (_: Exception) {}
            try { bass = BassBoost(0, 0) } catch (_: Exception) {}
            try { virtualizer = Virtualizer(0, 0) } catch (_: Exception) {}
        }
        val eqReady = try { equalizer?.enabled = true; equalizer != null } catch (_: Exception) { false }
        try { bass?.enabled = false } catch (_: Exception) {}
        try { virtualizer?.enabled = false } catch (_: Exception) {}
        return eqReady || hasBassBoost || hasVirtualizer
    }

    fun disable() {
        try { equalizer?.enabled = false } catch (_: Exception) {}
        try { bass?.enabled = false } catch (_: Exception) {}
        try { virtualizer?.enabled = false } catch (_: Exception) {}
    }

    fun setBand(index: Int, value: Int) {
        try {
            val eq = equalizer ?: return
            if (index !in 0 until eq.numberOfBands.toInt()) return
            val range = eq.bandLevelRange
            val level = (value * 100).coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(index.toShort(), level.toShort())
        } catch (_: Exception) {}
    }

    fun setBassBoost(enabled: Boolean) {
        try {
            val effect = bass ?: return
            effect.setStrength(if (enabled) 150.toShort() else 0.toShort())
            effect.enabled = enabled
        } catch (_: Exception) {}
    }

    fun setVirtualizer(enabled: Boolean) {
        try {
            val effect = virtualizer ?: return
            effect.setStrength(if (enabled) 100.toShort() else 0.toShort())
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
