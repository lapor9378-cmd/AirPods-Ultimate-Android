package com.airpodsultimate

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : ComponentActivity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(24, 32, 48)
    private val accent = Color.rgb(133, 160, 255)
    private val secondary = Color.rgb(167, 179, 199)
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var eqInfo: TextView
    private lateinit var trackInfo: TextView
    private lateinit var playButton: Button
    private lateinit var openAudio: ActivityResultLauncher<Array<String>>
    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var selectedPreset = "Plano"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        openAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openTrack(uri)
        }
        render()
        refreshBluetooth()
        requestBluetoothPermission()
    }

    private fun render() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            addView(content)
        }
        setContentView(scroll)

        content.addView(text("AIRPODS ULTIMATE", 12, accent, true))
        content.addView(text("Audio, bajo control.", 29, Color.WHITE, true))
        content.addView(text("Herramientas Bluetooth y sonido para Android", 14, secondary))
        content.addView(space(22))

        val diagnostic = card()
        diagnostic.addView(text("DIAGNÓSTICO", 11, accent, true))
        status = text("Preparando diagnóstico…", 14, Color.WHITE)
        diagnostic.addView(status)
        content.addView(diagnostic)

        content.addView(space(14))
        val deviceCard = card()
        deviceCard.addView(text("DISPOSITIVOS VINCULADOS", 11, accent, true))
        devices = text("Consultando Bluetooth…", 14, Color.WHITE)
        deviceCard.addView(devices)
        deviceCard.addView(button("Actualizar dispositivos") { refreshBluetooth() })
        content.addView(deviceCard)

        content.addView(space(14))
        val playerCard = card()
        playerCard.addView(text("REPRODUCTOR DE MÚSICA", 11, accent, true))
        trackInfo = text("Selecciona una canción del teléfono para comenzar.", 14, Color.WHITE)
        playerCard.addView(trackInfo)
        playerCard.addView(button("Elegir archivo de música") { openAudio.launch(arrayOf("audio/*")) })
        playButton = button("Reproducir") { togglePlayback() }
        playButton.isEnabled = false
        playerCard.addView(playButton)
        playerCard.addView(button("Detener reproducción") { stopPlayback() })
        content.addView(playerCard)

        content.addView(space(14))
        val audioCard = card()
        audioCard.addView(text("ECUALIZADOR", 11, accent, true))
        eqInfo = text("Abre una canción. El ecualizador se conectará a su sesión de audio.", 13, secondary)
        audioCard.addView(eqInfo)
        listOf("Plano", "Bajos", "Voces", "Brillante").forEach { preset ->
            audioCard.addView(button(preset) { applyPreset(preset) })
        }
        content.addView(audioCard)

        content.addView(space(14))
        val limits = card()
        limits.addView(text("COMPATIBILIDAD", 11, accent, true))
        limits.addView(text(
            "El ecualizador actúa sobre la música reproducida aquí. Android no garantiza que una app pueda modificar el sonido de Spotify, YouTube u otros reproductores externos. ANC, transparencia, audio espacial y sensores propietarios de Apple no están garantizados.",
            13, secondary
        ))
        content.addView(limits)
        content.addView(space(18))
        content.addView(text("Versión 0.2.0", 11, secondary))
    }

    private fun openTrack(uri: Uri) {
        try {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Algunos proveedores no ofrecen permisos persistentes.
            }
            releaseEqualizer()
            val audioPlayer = player ?: ExoPlayer.Builder(this).build().also { created ->
                created.addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        connectEqualizer(audioSessionId)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            connectEqualizer(created.audioSessionId)
                        }
                        updatePlaybackButton()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlaybackButton()
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        trackInfo.text = "No se pudo reproducir este archivo: ${error.localizedMessage ?: "formato no compatible"}"
                    }
                })
                player = created
            }
            audioPlayer.setMediaItem(MediaItem.fromUri(uri))
            audioPlayer.prepare()
            audioPlayer.playWhenReady = false
            val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Archivo de música"
            trackInfo.text = "Seleccionado: $displayName\nPreparando audio…"
            playButton.isEnabled = true
            eqInfo.text = "Preparando la sesión de audio real…"
            updatePlaybackButton()
        } catch (e: Exception) {
            trackInfo.text = "No se pudo abrir el archivo: ${e.localizedMessage ?: "error desconocido"}"
            playButton.isEnabled = false
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun connectEqualizer(sessionId: Int) {
        if (sessionId <= 0 || isFinishing || isDestroyed) return
        try {
            if (equalizer != null) return
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            val bands = equalizer?.numberOfBands ?: 0
            eqInfo.text = "Ecualizador conectado a la reproducción · $bands bandas. Perfil: $selectedPreset."
            applyPreset(selectedPreset)
        } catch (e: Exception) {
            releaseEqualizer()
            eqInfo.text = "Este sistema no ofrece un ecualizador compatible para esta sesión de audio."
        }
    }

    private fun applyPreset(preset: String) {
        selectedPreset = preset
        val eq = equalizer
        if (eq == null) {
            eqInfo.text = "Elige y prepara una canción primero. Después se podrá aplicar «$preset» si HiOS admite el efecto."
            return
        }
        try {
            val range = eq.bandLevelRange
            val low = range[0].toInt()
            val high = range[1].toInt()
            val values = when (preset) {
                "Bajos" -> intArrayOf(800, 600, 250, 0, -100)
                "Voces" -> intArrayOf(-200, 100, 550, 650, 250)
                "Brillante" -> intArrayOf(-200, 0, 150, 500, 750)
                else -> intArrayOf(0, 0, 0, 0, 0)
            }
            for (i in 0 until eq.numberOfBands) {
                eq.setBandLevel(i.toShort(), values.getOrElse(i) { 0 }.coerceIn(low, high).toShort())
            }
            eqInfo.text = "Perfil «$preset» aplicado · ${eq.numberOfBands} bandas activas."
        } catch (e: Exception) {
            eqInfo.text = "HiOS rechazó este perfil para la sesión actual."
        }
    }

    private fun togglePlayback() {
        val audioPlayer = player ?: return
        if (audioPlayer.isPlaying) audioPlayer.pause() else audioPlayer.play()
        updatePlaybackButton()
    }

    private fun stopPlayback() {
        player?.pause()
        player?.seekTo(0)
        updatePlaybackButton()
    }

    private fun updatePlaybackButton() {
        if (!::playButton.isInitialized) return
        val audioPlayer = player
        playButton.text = if (audioPlayer?.isPlaying == true) "Pausar" else "Reproducir"
        playButton.isEnabled = audioPlayer != null
    }

    private fun refreshBluetooth() {
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
            if (adapter == null) {
                devices.text = "El teléfono no informa de un adaptador Bluetooth."
                status.text = "Bluetooth no disponible."
                return
            }
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                devices.text = "Concede el permiso Bluetooth para consultar dispositivos vinculados."
                status.text = "Permiso Bluetooth pendiente."
                return
            }
            if (!adapter.isEnabled) {
                devices.text = "Bluetooth está apagado. Actívalo en Ajustes."
                status.text = "Adaptador detectado · Bluetooth apagado."
                return
            }
            val paired = adapter.bondedDevices.orEmpty()
            devices.text = if (paired.isEmpty()) {
                "No hay dispositivos vinculados. Empareja tus AirPods desde los ajustes Bluetooth."
            } else paired.joinToString("\n\n") { device ->
                val name = try { device.name ?: "Dispositivo Bluetooth" } catch (_: SecurityException) { "Nombre no disponible" }
                val kind = when (device.type) {
                    1 -> "Bluetooth clásico"
                    2 -> "Bluetooth LE"
                    3 -> "Bluetooth clásico + LE"
                    else -> "Tipo desconocido"
                }
                "• $name\n  $kind · Vinculado"
            }
            status.text = "Bluetooth activo · ${paired.size} dispositivo(s) vinculados."
        } catch (_: SecurityException) {
            devices.text = "Android bloqueó la consulta. Revisa el permiso Bluetooth."
            status.text = "Acceso Bluetooth denegado."
        } catch (e: Exception) {
            devices.text = "No se pudo consultar: ${e.localizedMessage ?: "error desconocido"}"
            status.text = "Diagnóstico incompleto."
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) refreshBluetooth()
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
    }

    override fun onDestroy() {
        releaseEqualizer()
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(surface)
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(48, 63, 94))
        setOnClickListener { action() }
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
