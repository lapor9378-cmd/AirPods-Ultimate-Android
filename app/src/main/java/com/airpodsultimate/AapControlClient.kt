// SPDX-License-Identifier: GPL-3.0-or-later
// AAP/L2CAP protocol approach adapted from Androidpods (GPL-3.0-or-later): https://github.com/TimStebner/Androidpods
package com.airpodsultimate

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.atomic.AtomicBoolean

class AapControlClient {
    @Volatile private var socket: BluetoothSocket? = null
    private val running = AtomicBoolean(false)

    fun connect(device: BluetoothDevice, status: (String, Boolean) -> Unit, motion: (Float, Float, Float) -> Unit) {
        Thread {
            try {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
                val channel = HiddenApiBypass.invoke(
                    BluetoothDevice::class.java, device, "createInsecureL2capSocket", 0x1001
                ) as BluetoothSocket
                channel.connect()
                socket = channel
                running.set(true)
                send(byteArrayOf(0x00,0x00,0x04,0x00,0x01,0x00,0x02,0x00,0,0,0,0,0,0,0,0))
                Thread.sleep(200)
                send(byteArrayOf(0x04,0x00,0x04,0x00,0x4D,0x00,0xD7.toByte(),0,0,0,0,0,0,0))
                Thread.sleep(200)
                send(byteArrayOf(0x04,0x00,0x04,0x00,0x0F,0x00,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte()))
                status("Canal AACP conectado. Configuración de gestos disponible.", true)
                val buffer = ByteArray(1024)
                while (running.get()) {
                    val count = channel.inputStream.read(buffer)
                    if (count <= 0) break
                    val packet = buffer.copyOf(count)
                    if (packet.size >= 50 && packet[0] == 0x04.toByte() &&
                        packet[1] == 0.toByte() && packet[2] == 0x04.toByte() &&
                        packet[3] == 0.toByte() && packet[4] == 0x17.toByte()) {
                        val a = readShort(packet, 43)
                        val b = readShort(packet, 45)
                        val c = readShort(packet, 47)
                        val pitch = (((b + c) / 2f) / 32000f * 180f).coerceIn(-90f, 90f)
                        val yaw = (((b - c) / 2f) / 32000f * 180f).coerceIn(-70f, 70f)
                        val roll = (a / 32000f * 180f).coerceIn(-180f, 180f)
                        motion(pitch, yaw, roll)
                    }
                }
                status("El canal AACP se desconectó.", false)
            } catch (e: Exception) {
                status("No se pudo abrir AACP: " + (e.message ?: e.javaClass.simpleName), false)
            } finally {
                running.set(false)
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }.start()
    }

    fun setHeadGestures(enabled: Boolean): Boolean = send(byteArrayOf(
        0x04,0x00,0x04,0x00,0x09,0x00,0x3E,
        (if (enabled) 0x02 else 0x00).toByte(),0x00,0x00,0x00
    ))

    fun setHeadTracking(enabled: Boolean): Boolean {
        val first = send(motionPacket(0x10, enabled))
        try { Thread.sleep(50) } catch (_: Exception) {}
        val second = send(motionPacket(0x12, enabled))
        return first && second
    }

    private fun motionPacket(service: Int, enabled: Boolean): ByteArray {
        val rate1 = if (enabled) 0x40.toByte() else 0.toByte()
        val rate2 = if (enabled) 0x9C.toByte() else 0.toByte()
        return byteArrayOf(
            0x04,0x00,0x04,0x00,0x17,0x00,0x00,0x00,
            0x10,0x00,0x10,0x00,0x08,0xA1.toByte(),0x02,0x42,
            0x0B,0x08,service.toByte(),0x10,0x02,0x1A,0x05,0x01,
            rate1,rate2,0,0
        )
    }

    private fun send(packet: ByteArray): Boolean = try {
        val active = socket ?: return false
        synchronized(active) {
            active.outputStream.write(packet)
            active.outputStream.flush()
        }
        true
    } catch (_: Exception) { false }

    private fun readShort(bytes: ByteArray, offset: Int): Float {
        val raw = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        return raw.toShort().toFloat()
    }

    fun close() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
