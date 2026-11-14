package com.airpodsultimate

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.app.AlertDialog
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
import android.widget.SeekBar
import android.widget.Switch
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
    private lateinit var advertiserStatus: TextView
    private lateinit var lidStatus: TextView
    private lateinit var earStatus: TextView
    private lateinit var linkStatus: TextView
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
        if (audioPrefs.getBoolean("master", false)) {
            val engine = soundEngine ?: SoundEffectsEngine().also { soundEngine = it }
            if (engine.enable()) {
                for (i in 0 until 5) engine.setBand(i, audioPrefs.getInt("band_$i", 120) - 120)
                engine.setVirtualizer(audioPrefs.getBoolean("virtualizer", false))
                engine.setBassBoost(audioPrefs.getBoolean("bass", false))
            }
        }
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
        advertiserStatus = text("Anuncio del AirPod: esperando datos…", 13, secondary)
        lidStatus = text("Tapa del estuche: sin datos", 13, secondary)
        statusCard.addView(advertiserStatus)
        statusCard.addView(lidStatus)
        earStatus = text("Detección de oído: esperando datos…", 13, secondary)
        linkStatus = text("Estado Bluetooth: esperando datos…", 13, secondary)
        statusCard.addView(earStatus)
        statusCard.addView(linkStatus)
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
            "Los datos se leen de anuncios BLE. La detección de oído y el estado de conexión se interpretan desde el protocolo de proximidad. El indicador dentro/fuera del estuche corresponde al audífono que emite el anuncio, no identifica por sí solo izquierda o derecha.",
            13, secondary
        ))
        updated = text("Todavía no se ha recibido una lectura.", 12, secondary)
        note.addView(updated)
        content.addView(note)
        content.addView(space(14))
        content.addView(buildSoundCard())
        val modes = card()
        modes.addView(text("AIRPODS · MODOS DE ESCUCHA", 11, accent, true))
        modes.addView(text("Cancelación de ruido, Transparencia y gestos", 16, Color.WHITE, true))
        modes.addView(text("Los controles de gestos y seguimiento usan el canal AACP clásico. La compatibilidad depende del firmware de los AirPods y del Bluetooth del teléfono; si el canal no abre, la app lo indicará sin simular que funciona.", 13, secondary))
        content.addView(modes)
        content.addView(space(14))
        content.addView(buildHeadControlsCard())
        content.addView(space(18))
        content.addView(text("Versión 0.8.0", 11, secondary))
        showBattery(null)
    }

    private var soundEngine: SoundEffectsEngine? = null
    private val audioPrefs by lazy { getSharedPreferences("audio_settings", MODE_PRIVATE) }
    private var aapClient: AapControlClient? = null
    private lateinit var aapStatus: TextView
    private lateinit var headGesturesSwitch: Switch
    private lateinit var headTrackingSwitch: Switch

    private fun buildSoundCard(): LinearLayout {
        val panel = card()
        panel.addView(text("AUDIO · ECUALIZADOR", 11, accent, true))
        panel.addView(text("Control de sonido", 20, Color.WHITE, true))
        val state = text("Comprobando efectos del sistema…", 12, secondary)
        panel.addView(state)
        val master = Switch(this).apply {
            text = "Procesamiento de sonido"
            setTextColor(Color.WHITE)
            isChecked = audioPrefs.getBoolean("master", false)
        }
        panel.addView(master)
        val surround = Switch(this).apply {
            text = "Envolvente virtual"
            setTextColor(Color.WHITE)
            isChecked = false
            isEnabled = false
        }
        panel.addView(surround)
        val bass = Switch(this).apply {
            text = "Realce de graves"
            setTextColor(Color.WHITE)
            isChecked = false
            isEnabled = false
        }
        panel.addView(bass)
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("Plano", "Rock", "Vocal", "Bass").forEach { name ->
            val b = Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 11f
                setOnClickListener { soundEngine?.applyPreset(name) }
            }
            presets.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        panel.addView(presets)
        val frequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        val bars = ArrayList<SeekBar>()
        frequencies.forEachIndexed { index, label ->
            panel.addView(text(label, 12, secondary, true))
            val seek = SeekBar(this).apply { max = 240; progress = audioPrefs.getInt("band_$index", 120).coerceIn(0, 240); isEnabled = master.isChecked }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { audioPrefs.edit().putInt("band_$index", progress).apply(); soundEngine?.setBand(index, progress - 120) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            bars.add(seek)
            panel.addView(seek)
        }
        master.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                audioPrefs.edit().putBoolean("master", true).apply()
                val engine = soundEngine ?: SoundEffectsEngine().also { soundEngine = it }
                val ok = engine.enable()
                state.text = if (ok) "Ecualizador activo. Envolvente y graves permanecen apagados hasta que los actives." else "Este firmware no permite efectos globales. No se ha aplicado ningún cambio."
                surround.isEnabled = ok && engine.hasVirtualizer
                bass.isEnabled = ok && engine.hasBassBoost
                bars.forEach { it.isEnabled = ok && engine.hasEqualizer }
                if (!ok) master.isChecked = false
            } else {
                audioPrefs.edit().putBoolean("master", false).apply()
                soundEngine?.disable()
                state.text = "Procesamiento desactivado."
                surround.isChecked = false
                bass.isChecked = false
                surround.isEnabled = false
                bass.isEnabled = false
                bars.forEach { it.isEnabled = false }
            }
        }
        surround.setOnCheckedChangeListener { _, checked -> audioPrefs.edit().putBoolean("virtualizer", checked).apply(); soundEngine?.setVirtualizer(checked) }
        bass.setOnCheckedChangeListener { _, checked -> audioPrefs.edit().putBoolean("bass", checked).apply(); soundEngine?.setBassBoost(checked) }
        return panel
    }


    private fun buildHeadControlsCard(): LinearLayout {
        val panel = card()
        panel.addView(text("AIRPODS · GESTOS Y MOVIMIENTO", 11, accent, true))
        panel.addView(text("Controles de cabeza", 20, Color.WHITE, true))
        aapStatus = text("Conecta tus AirPods para abrir el canal de control AACP.", 13, secondary)
        panel.addView(aapStatus)
        panel.addView(button("Conectar canal AirPods") { chooseAapDevice() })
        headGesturesSwitch = Switch(this).apply {
            text = "Gestos de cabeza (asentir / negar)"
            setTextColor(Color.WHITE)
            isEnabled = false
        }
        panel.addView(headGesturesSwitch)
        headTrackingSwitch = Switch(this).apply {
            text = "Seguimiento de movimiento de cabeza"
            setTextColor(Color.WHITE)
            isEnabled = false
        }
        panel.addView(headTrackingSwitch)
        headGesturesSwitch.setOnCheckedChangeListener { _, checked ->
            if (aapClient?.setHeadGestures(checked) != true) {
                headGesturesSwitch.isChecked = false
                aapStatus.text = "No se pudo enviar la configuración de gestos."
            } else getSharedPreferences("airpods_settings", MODE_PRIVATE).edit().putBoolean("head_gestures", checked).apply()
        }
        headTrackingSwitch.setOnCheckedChangeListener { _, checked ->
            if (aapClient?.setHeadTracking(checked) != true) {
                headTrackingSwitch.isChecked = false
                aapStatus.text = "No se pudo iniciar el flujo de sensores."
            } else getSharedPreferences("airpods_settings", MODE_PRIVATE).edit().putBoolean("head_tracking", checked).apply()
        }
        return panel
    }

    @Suppress("MissingPermission")
    private fun chooseAapDevice() {
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val devices = manager.adapter?.bondedDevices?.toList().orEmpty()
            if (devices.isEmpty()) {
                aapStatus.text = "No hay dispositivos emparejados. Empareja tus AirPods primero."
                return
            }
            val labels = devices.map { (it.name ?: "Dispositivo") + " · " + it.address.takeLast(5) }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Selecciona tus AirPods")
                .setItems(labels) { _, which -> connectAap(devices[which]) }
                .setNegativeButton("Cancelar", null).show()
        } catch (_: SecurityException) {
            aapStatus.text = "Falta el permiso Bluetooth de dispositivos cercanos."
        }
    }

    private fun connectAap(device: BluetoothDevice) {
        aapClient?.close()
        aapStatus.text = "Conectando al canal AACP…"
        headGesturesSwitch.isEnabled = false
        headTrackingSwitch.isEnabled = false
        val client = AapControlClient()
        aapClient = client
        client.connect(device, { message, connected ->
            runOnUiThread {
                aapStatus.text = message
                headGesturesSwitch.isEnabled = connected
                headTrackingSwitch.isEnabled = connected
                if (connected) {
                    val prefs = getSharedPreferences("airpods_settings", MODE_PRIVATE)
                    headGesturesSwitch.isChecked = prefs.getBoolean("head_gestures", false)
                    headTrackingSwitch.isChecked = prefs.getBoolean("head_tracking", false)
                }
            }
        }, { pitch, yaw, roll ->
            runOnUiThread {
                aapStatus.text = "Movimiento · Inclinación %.1f° · Giro %.1f° · Rotación %.1f°".format(pitch, yaw, roll)
            }
        })
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
            advertiserStatus.text = "Anuncio del AirPod: sin lectura"
            lidStatus.text = "Tapa del estuche: sin datos"
            earStatus.text = "Detección de oído: sin datos"
            linkStatus.text = "Estado Bluetooth: sin datos"
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
        advertiserStatus.text = when (data.advertiserInCase) {
            true -> "Anuncio del AirPod: dentro del estuche"
            false -> "Anuncio del AirPod: fuera del estuche"
            null -> "Anuncio del AirPod: estado desconocido"
        }
        lidStatus.text = when (data.lidOpen) {
            true -> "Tapa del estuche: abierta"
            false -> "Tapa del estuche: cerrada"
            null -> "Tapa del estuche: sin datos"
        }
        earStatus.text = "Detección de oído · Izq: " + (if (data.leftInEar) "puesto" else "fuera") + " · Der: " + (if (data.rightInEar) "puesto" else "fuera")
        linkStatus.text = "Estado Bluetooth: " + data.connectionState + if (data.paired) " · emparejado" else " · no emparejado"
        caseStatus.text = chargeText(data.case)
        scanStatus.text = "Anuncio AirPods Pro 3 detectado · " + data.connectionState + " · señal " + data.rssi + " dBm"
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
