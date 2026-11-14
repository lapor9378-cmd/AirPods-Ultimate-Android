package com.airpodsultimate

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(24, 32, 48)
    private val accent = Color.rgb(133, 160, 255)
    private val secondary = Color.rgb(167, 179, 199)
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var musicInfo: TextView
    private lateinit var accessButton: Button
    private var sessionManager: MediaSessionManager? = null
    private var activeControllers: List<MediaController> = emptyList()
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = showCurrentTrack()
        override fun onPlaybackStateChanged(state: PlaybackState?) = showCurrentTrack()
        override fun onSessionDestroyed() = refreshMediaSessions()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachControllers(controllers.orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        render()
        refreshBluetooth()
        requestBluetoothPermission()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onStart() {
        super.onStart()
        refreshMediaSessions()
    }

    override fun onStop() {
        unregisterMediaSessions()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::accessButton.isInitialized) updateAccessButton()
        if (::musicInfo.isInitialized) refreshMediaSessions()
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
        val musicCard = card()
        musicCard.addView(text("REPRODUCCIÓN ACTUAL", 11, accent, true))
        musicInfo = text("Buscando música que se esté reproduciendo en el teléfono…", 15, Color.WHITE)
        musicCard.addView(musicInfo)
        accessButton = button("Activar detección de música") { openNotificationAccess() }
        musicCard.addView(accessButton)
        musicCard.addView(button("Actualizar música") { refreshMediaSessions() })
        musicCard.addView(text(
            "Detecta sesiones multimedia de aplicaciones de streaming y reproductores compatibles. No necesitas elegir ni descargar canciones.",
            12, secondary
        ))
        content.addView(musicCard)

        content.addView(space(14))
        val audioCard = card()
        audioCard.addView(text("ECUALIZADOR", 11, accent, true))
        audioCard.addView(text(
            "La detección de Spotify, Deezer y otros servicios funciona por las sesiones multimedia de Android. Aplicar efectos directamente al audio de otra aplicación depende del sistema y no está permitido por todos los teléfonos. Esta versión no fingirá que un perfil se aplicó si Android no lo permite.",
            13, secondary
        ))
        content.addView(audioCard)

        content.addView(space(14))
        val limits = card()
        limits.addView(text("COMPATIBILIDAD", 11, accent, true))
        limits.addView(text(
            "La información disponible depende de cada reproductor: título, artista, carátula y estado pueden variar. La app no graba ni escucha el audio.",
            13, secondary
        ))
        content.addView(limits)
        content.addView(space(18))
        content.addView(text("Versión 0.3.0", 11, secondary))
        updateAccessButton()
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            musicInfo.text = "Abre Ajustes y busca Acceso a notificaciones para permitir AirPods Ultimate."
        }
    }

    private fun updateAccessButton() {
        if (!::accessButton.isInitialized) return
        accessButton.text = if (hasNotificationAccess()) "Acceso de detección activado" else "Activar detección de música"
    }

    private fun refreshMediaSessions() {
        if (!::musicInfo.isInitialized) return
        updateAccessButton()
        if (!hasNotificationAccess()) {
            musicInfo.text = "Falta permitir el acceso a notificaciones para detectar la música activa."
            return
        }
        try {
            val manager = sessionManager ?: (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).also { sessionManager = it }
            manager.addOnActiveSessionsChangedListener(
                sessionsListener,
                ComponentName(this, MediaNotificationListener::class.java),
                Handler(Looper.getMainLooper())
            )
            attachControllers(manager.getActiveSessions(ComponentName(this, MediaNotificationListener::class.java)))
        } catch (_: SecurityException) {
            musicInfo.text = "Android todavía no concedió el acceso. Actívalo en Ajustes y vuelve a la app."
        } catch (e: Exception) {
            musicInfo.text = "No se pudieron consultar las sesiones multimedia: ${e.localizedMessage ?: "error"}"
        }
    }

    private fun attachControllers(controllers: List<MediaController>) {
        activeControllers.forEach { old ->
            try { old.unregisterCallback(controllerCallback) } catch (_: Exception) {}
        }
        activeControllers = controllers
        activeControllers.forEach { controller ->
            try { controller.registerCallback(controllerCallback, Handler(Looper.getMainLooper())) } catch (_: Exception) {}
        }
        showCurrentTrack()
    }

    private fun showCurrentTrack() {
        if (!::musicInfo.isInitialized) return
        val playing = activeControllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val current = playing ?: activeControllers.firstOrNull {
            val state = it.playbackState?.state
            state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
        }
        if (current == null) {
            musicInfo.text = if (activeControllers.isEmpty()) {
                "No se detecta música activa. Reproduce algo en Spotify, Deezer u otro reproductor compatible."
            } else {
                "Hay sesiones multimedia disponibles, pero ninguna informa una canción actual."
            }
            return
        }
        val metadata = current.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Título no disponible"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "Artista no disponible"
        val state = if (current.playbackState?.state == PlaybackState.STATE_PLAYING) "REPRODUCIENDO" else "EN PAUSA"
        musicInfo.text = "● $state\n$title\n$artist"
    }

    private fun unregisterMediaSessions() {
        try { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        activeControllers.forEach { controller ->
            try { controller.unregisterCallback(controllerCallback) } catch (_: Exception) {}
        }
        activeControllers = emptyList()
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
