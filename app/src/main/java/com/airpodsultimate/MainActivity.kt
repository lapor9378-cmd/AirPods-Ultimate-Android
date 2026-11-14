package com.airpodsultimate

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(24, 32, 48)
    private val accent = Color.rgb(133, 160, 255)
    private val secondary = Color.rgb(167, 179, 199)
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var eqInfo: TextView
    private var equalizer: Equalizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        render()
        refreshBluetooth()
        requestBluetoothPermission()
        setupEqualizer()
    }

    private fun render() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(background)
            addView(content)
        }
        setContentView(scroll)

        content.addView(text("AIRPODS ULTIMATE", 12, accent, true))
        content.addView(text("Audio, bajo control.", 29, Color.WHITE, true))
        content.addView(text("Herramientas Bluetooth para Android", 14, secondary))
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
        val audioCard = card()
        audioCard.addView(text("PERFILES DE SONIDO", 11, accent, true))
        eqInfo = text("Comprobando compatibilidad del ecualizador…", 13, secondary)
        audioCard.addView(eqInfo)
        listOf("Plano", "Bajos", "Voces", "Brillante").forEach { preset ->
            audioCard.addView(button(preset) { applyPreset(preset) })
        }
        content.addView(audioCard)

        content.addView(space(14))
        val limits = card()
        limits.addView(text("FUNCIONES Y COMPATIBILIDAD", 11, accent, true))
        limits.addView(text(
            "La batería individual, ANC, transparencia, audio espacial de Apple y sensores propietarios no están garantizados por Android. La app solo mostrará datos que el teléfono realmente exponga.",
            13, secondary
        ))
        content.addView(limits)
        content.addView(space(18))
        content.addView(text("Versión 0.1.0 · Construcción inicial", 11, secondary))
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) refreshBluetooth()
    }

    private fun setupEqualizer() {
        try {
            equalizer = Equalizer(0, 0).apply { enabled = true }
            val bands = equalizer?.numberOfBands ?: 0
            eqInfo.text = "Ecualizador del sistema disponible · $bands bandas. El resultado depende del fabricante y de la app de música."
        } catch (e: Exception) {
            equalizer = null
            eqInfo.text = "El sistema no permite activar un ecualizador global. No se aplicó ningún cambio."
        }
    }

    private fun applyPreset(preset: String) {
        val eq = equalizer
        if (eq == null) {
            eqInfo.text = "No compatible: Android no permitió crear el ecualizador. Prueba el ecualizador del teléfono."
            return
        }
        try {
            val limits = eq.bandLevelRange
            val low = limits[0].toInt()
            val high = limits[1].toInt()
            val values = when (preset) {
                "Bajos" -> intArrayOf(800, 600, 250, 0, -100)
                "Voces" -> intArrayOf(-200, 100, 550, 650, 250)
                "Brillante" -> intArrayOf(-200, 0, 150, 500, 750)
                else -> intArrayOf(0, 0, 0, 0, 0)
            }
            for (i in 0 until eq.numberOfBands) {
                eq.setBandLevel(i.toShort(), values.getOrElse(i) { 0 }.coerceIn(low, high).toShort())
            }
            eqInfo.text = "Perfil «$preset» aplicado a la sesión de audio disponible. Puede variar según el teléfono y el reproductor."
        } catch (e: Exception) {
            eqInfo.text = "No se pudo aplicar el perfil: ${e.localizedMessage ?: "efecto no compatible"}."
        }
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

    private fun space(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
