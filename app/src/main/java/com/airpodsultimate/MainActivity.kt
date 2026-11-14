package com.airpodsultimate

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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

    private lateinit var scanStatus: TextView
    private lateinit var leftValue: TextView
    private lateinit var rightValue: TextView
    private lateinit var caseValue: TextView
    private lateinit var leftStatus: TextView
    private lateinit var rightStatus: TextView
    private lateinit var leftPosition: TextView
    private lateinit var rightPosition: TextView
    private lateinit var caseStatus: TextView
    private lateinit var updated: TextView
    private lateinit var scanButton: Button

    private var latest: AirPodsBattery? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val refreshClock = object : Runnable {
        override fun run() {
            updateFreshness()
            if (scanning) handler.postDelayed(this, 1000)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val payload = result.scanRecord?.getManufacturerSpecificData(AirPodsBattery.APPLE_COMPANY_ID) ?: return
            val reading = AirPodsBattery.decode(payload) ?: return
            if (reading.modelId != AirPodsBattery.AIRPODS_PRO_3_MODEL) return
            runOnUiThread {
                latest = reading.copy(rssi = result.rssi, seenAt = System.currentTimeMillis())
                showBattery(latest!!)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                scanning = false
                scanStatus.text = "Android no pudo iniciar el escaneo Bluetooth (código " + errorCode + ")."
                scanButton.text = "Volver a intentar"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        render()
    }

    override fun onResume() {
        super.onResume()
        beginScanningIfAllowed()
    }

    override fun onPause() {
        stopScanning()
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
        content.addView(text("Batería", 32, Color.WHITE, true))
        content.addView(text("Niveles de carga de tus AirPods Pro 3", 14, secondary))
        content.addView(space(22))

        val statusCard = card()
        statusCard.addView(text("ESTADO DE CONEXIÓN", 11, accent, true))
        scanStatus = text("Preparando Bluetooth…", 14, Color.WHITE)
        statusCard.addView(scanStatus)
        scanButton = button("Buscar AirPods") { requestScanPermissions() }
        statusCard.addView(scanButton)
        content.addView(statusCard)

        content.addView(space(14))
        content.addView(batteryCard("AUDÍFONO IZQUIERDO", true, 0))
        content.addView(space(10))
        content.addView(batteryCard("AUDÍFONO DERECHO", true, 1))
        content.addView(space(10))
        content.addView(batteryCard("ESTUCHE DE CARGA", false, 2))

        content.addView(space(14))
        val note = card()
        note.addView(text("LECTURAS REALES", 11, accent, true))
        note.addView(text(
            "Los porcentajes se leen de los anuncios Bluetooth de los AirPods. La lectura puede venir en pasos de 10%. Al cerrar el estuche, su batería puede dejar de anunciarse y se conserva la última lectura recibida.",
            13, secondary
        ))
        updated = text("Todavía no se ha recibido una lectura.", 12, secondary)
        note.addView(updated)
        content.addView(note)
        content.addView(space(18))
        content.addView(text("Versión 0.4.0", 11, secondary))
        showBattery(null)
    }

    private fun batteryCard(title: String, earbud: Boolean, index: Int): LinearLayout {
        val panel = card()
        panel.addView(text(title, 11, accent, true))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val value = text("--%", 34, Color.WHITE, true)
        row.addView(value, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text(if (earbud) "◉" else "▣", 25, secondary, true))
        panel.addView(row)
        val state = text("Esperando señal…", 12, secondary)
        panel.addView(state)
        if (earbud) {
            val position = text("● Estado no detectado", 13, secondary, true)
            panel.addView(position)
            if (index == 0) leftPosition = position else rightPosition = position
        }
        when (index) {
            0 -> { leftValue = value; leftStatus = state }
            1 -> { rightValue = value; rightStatus = state }
            else -> { caseValue = value; caseStatus = state }
        }
        return panel
    }

    private fun showBattery(data: AirPodsBattery?) {
        if (!::leftValue.isInitialized) return
        if (data == null) {
            leftValue.text = "--%"
            rightValue.text = "--%"
            caseValue.text = "--%"
            leftStatus.text = "Sin lectura"
            rightStatus.text = "Sin lectura"
            setPosition(leftPosition, null)
            setPosition(rightPosition, null)
            caseStatus.text = "Sin lectura"
            return
        }
        leftValue.text = data.left.percent?.let { it.toString() + "%" } ?: "--%"
        rightValue.text = data.right.percent?.let { it.toString() + "%" } ?: "--%"
        caseValue.text = data.case.percent?.let { it.toString() + "%" } ?: "--%"
        leftStatus.text = chargeText(data.left)
        rightStatus.text = chargeText(data.right)
        setPosition(leftPosition, data.leftInCase)
        setPosition(rightPosition, data.rightInCase)
        caseStatus.text = chargeText(data.case)
        scanStatus.text = "AirPods Pro 3 detectados · señal Bluetooth " + data.rssi + " dBm"
        scanButton.text = "Buscando automáticamente"
        updated.text = "Última lectura: ahora. Los valores se actualizan al recibir otra señal."
    }

    private fun setPosition(view: TextView, inCase: Boolean?) {
        when (inCase) {
            true -> { view.text = "● En caja"; view.setTextColor(Color.rgb(255, 183, 77)) }
            false -> { view.text = "● Fuera del estuche"; view.setTextColor(Color.rgb(76, 220, 140)) }
            null -> { view.text = "● Estado no detectado"; view.setTextColor(secondary) }
        }
    }

    private fun chargeText(part: BatteryPart): String = when {
        part.percent == null -> "Nivel no anunciado"
        part.charging -> "Cargando"
        else -> "No está cargando"
    }

    private fun updateFreshness() {
        val data = latest ?: return
        val seconds = ((System.currentTimeMillis() - data.seenAt) / 1000).coerceAtLeast(0)
        updated.text = if (seconds < 3) {
            "Última lectura: ahora."
        } else {
            "Última lectura hace " + seconds + " s. El estuche puede dejar de emitir al cerrar la tapa."
        }
    }

    private fun requestScanPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 42)
        } else {
            startScanning()
        }
    }

    private fun beginScanningIfAllowed() {
        if (!::scanStatus.isInitialized) return
        val needed = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startScanning()
        } else {
            scanStatus.text = "Concede el permiso Bluetooth para leer la batería de los AirPods."
            scanButton.text = "Conceder permiso Bluetooth"
        }
    }

    @Suppress("MissingPermission")
    private fun startScanning() {
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
            if (adapter == null || !adapter.isEnabled) {
                scanStatus.text = "Activa Bluetooth para buscar tus AirPods."
                scanButton.text = "Revisar Bluetooth"
                return
            }
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                scanStatus.text = "Este teléfono no ofrece escaneo Bluetooth LE."
                return
            }
            if (scanning) return
            val filter = ScanFilter.Builder()
                .setManufacturerData(
                    AirPodsBattery.APPLE_COMPANY_ID,
                    byteArrayOf(AirPodsBattery.PROXIMITY_TYPE.toByte()),
                    byteArrayOf(0xFF.toByte())
                )
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(listOf(filter), settings, scanCallback)
            scanning = true
            scanStatus.text = "Buscando tus AirPods Pro 3… Abre la tapa del estuche para actualizar sus niveles."
            scanButton.text = "Escaneo activo"
            handler.removeCallbacks(refreshClock)
            handler.post(refreshClock)
        } catch (_: SecurityException) {
            scanStatus.text = "Android bloqueó el escaneo. Revisa los permisos Bluetooth."
            scanButton.text = "Conceder permiso Bluetooth"
        } catch (e: Exception) {
            scanStatus.text = "No se pudo iniciar el escaneo: " + (e.localizedMessage ?: "error")
        }
    }

    @Suppress("MissingPermission")
    private fun stopScanning() {
        if (!scanning) return
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        scanning = false
        handler.removeCallbacks(refreshClock)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScanning()
            } else {
                scanStatus.text = "Permiso denegado. Sin él Android no permite leer los anuncios Bluetooth."
            }
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
