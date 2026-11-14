package com.airpodsultimate

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(24, 32, 48)
    private val accent = Color.rgb(133, 160, 255)
    private val secondary = Color.rgb(167, 179, 199)
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private lateinit var routeStatus: TextView
    private lateinit var volumeStatus: TextView
    private lateinit var formatStatus: TextView
    private lateinit var testStatus: TextView
    private lateinit var testButton: Button
    private val testRunning = AtomicBoolean(false)
    private var testTrack: AudioTrack? = null
    private var testThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            refreshDiagnostics()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        render()
        refreshDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        stopStereoTest()
        super.onPause()
    }

    private fun render() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(30))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            addView(content)
        }
        setContentView(scroll)

        content.addView(text("AIRPODS ULTIMATE", 12, accent, true))
        content.addView(text("Centro de audio", 30, Color.WHITE, true))
        content.addView(text("Herramientas prácticas para tus AirPods en Android", 14, secondary))
        content.addView(space(18))

        val connection = card()
        connection.addView(text("SALIDA DE AUDIO", 11, accent, true))
        routeStatus = text("Detectando salida…", 16, Color.WHITE, true)
        connection.addView(routeStatus)
        connection.addView(text("La app identifica la salida que Android está usando. No intenta abrir canales privados ni modificar la conexión Bluetooth.", 13, secondary))
        connection.addView(button("Abrir ajustes Bluetooth") { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) })
        connection.addView(button("Abrir ajustes de sonido") { openSettings(Settings.ACTION_SOUND_SETTINGS) })
        content.addView(connection)

        content.addView(space(12))
        val diagnostics = card()
        diagnostics.addView(text("DIAGNÓSTICO", 11, accent, true))
        volumeStatus = text("Volumen multimedia: --", 16, Color.WHITE, true)
        formatStatus = text("Formato de audio: --", 13, secondary)
        diagnostics.addView(volumeStatus)
        diagnostics.addView(formatStatus)
        diagnostics.addView(text("Solo lectura: esta app no sube, baja ni limita el volumen del teléfono.", 13, secondary))
        diagnostics.addView(button("Abrir opciones de desarrollador") {
            openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        })
        content.addView(diagnostics)

        content.addView(space(12))
        val stereo = card()
        stereo.addView(text("PRUEBA ESTÉREO", 11, accent, true))
        stereo.addView(text("Canal izquierdo y derecho", 19, Color.WHITE, true))
        stereo.addView(text("Reproduce un tono breve y suave primero por el lado izquierdo y después por el derecho. Mantén el volumen a un nivel cómodo.", 13, secondary))
        testStatus = text("Listo para probar.", 13, secondary)
        stereo.addView(testStatus)
        testButton = button("Iniciar prueba estéreo") { startStereoTest() }
        stereo.addView(testButton)
        stereo.addView(button("Detener prueba") { stopStereoTest() })
        content.addView(stereo)

        content.addView(space(12))
        val help = card()
        help.addView(text("VOLUMEN QUE BAJA SOLO", 11, accent, true))
        help.addView(text("Revisa «Desactivar volumen absoluto»", 17, Color.WHITE, true))
        help.addView(text("En algunos Android, el volumen Bluetooth del teléfono y el de los audífonos quedan sincronizados. En Opciones de desarrollador puedes probar «Desactivar volumen absoluto» y después desconectar y volver a conectar los AirPods. El nombre puede variar según HiOS.", 13, secondary))
        help.addView(text("Esta app no puede cambiar esa opción protegida por Android. Tampoco fuerza el volumen para evitar conflictos con el sistema.", 13, secondary))
        content.addView(help)

        content.addView(space(18))
        content.addView(text("Versión 0.9.0 · Sin efectos de audio en segundo plano", 11, secondary))
    }

    private fun refreshDiagnostics() {
        val outputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        } catch (_: Exception) {
            emptyList()
        }
        val bluetooth = outputs.filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (BuildCompat.isBluetoothType(it.type))
        }
        val route = when {
            bluetooth.isNotEmpty() -> bluetooth.joinToString(" · ") { it.productName.toString() }
            outputs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> "Audífonos con cable"
            outputs.any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET } -> "Audífonos USB"
            outputs.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } -> "Altavoz del teléfono"
            else -> outputs.firstOrNull()?.productName?.toString() ?: "Salida no identificada"
        }
        routeStatus.text = route
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeStatus.text = "Volumen multimedia: $current / $maximum"
        val rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "No disponible"
        val frames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "No disponible"
        formatStatus.text = "Frecuencia del sistema: $rate Hz · Búfer: $frames frames"
    }

    private fun startStereoTest() {
        if (!testRunning.compareAndSet(false, true)) return
        testStatus.text = "Reproduciendo izquierda y derecha…"
        testButton.isEnabled = false
        testThread = Thread {
            var track: AudioTrack? = null
            try {
                val sampleRate = 44100
                val minBuffer = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuffer <= 0) throw IllegalStateException("El sistema no admite la prueba de audio.")
                val bufferSize = maxOf(minBuffer, 8192)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                testTrack = track
                track.setVolume(0.12f)
                track.play()
                playChannel(track, sampleRate, true)
                if (testRunning.get()) Thread.sleep(250)
                if (testRunning.get()) playChannel(track, sampleRate, false)
                runOnUiThread { testStatus.text = "Prueba terminada. ¿Se escucharon ambos lados?" }
            } catch (e: Exception) {
                runOnUiThread { testStatus.text = "No se pudo completar la prueba: ${e.message ?: "error"}" }
            } finally {
                try { track?.pause() } catch (_: Exception) {}
                try { track?.flush() } catch (_: Exception) {}
                try { track?.release() } catch (_: Exception) {}
                testTrack = null
                testRunning.set(false)
                runOnUiThread { testButton.isEnabled = true }
            }
        }.apply { name = "AirPodsStereoTest"; start() }
    }

    private fun playChannel(track: AudioTrack, sampleRate: Int, left: Boolean) {
        val chunkFrames = 1024
        val totalFrames = sampleRate
        val samples = ShortArray(chunkFrames * 2)
        var writtenFrames = 0
        while (writtenFrames < totalFrames && testRunning.get()) {
            val frames = minOf(chunkFrames, totalFrames - writtenFrames)
            for (i in 0 until frames) {
                val t = (writtenFrames + i).toDouble() / sampleRate
                val sample = (sin(2.0 * PI * 440.0 * t) * 1800.0).toInt().toShort()
                samples[i * 2] = if (left) sample else 0
                samples[i * 2 + 1] = if (left) 0 else sample
            }
            val result = track.write(samples, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
            if (result < 0) throw IllegalStateException("Error de salida de audio ($result).")
            writtenFrames += frames
        }
    }

    private fun stopStereoTest() {
        testRunning.set(false)
        try { testTrack?.pause() } catch (_: Exception) {}
        try { testTrack?.flush() } catch (_: Exception) {}
        testStatusSafe("Prueba detenida.")
    }

    private fun testStatusSafe(value: String) {
        if (::testStatus.isInitialized) runOnUiThread { testStatus.text = value }
        if (::testButton.isInitialized) runOnUiThread { testButton.isEnabled = true }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        setBackgroundColor(surface)
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(3), 0, dp(7))
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

private object BuildCompat {
    fun isBluetoothType(type: Int): Boolean =
        type == 26 || type == 27
}
