package com.airpodsultimate

data class BatteryPart(val percent: Int?, val charging: Boolean)

data class AirPodsBattery(
    val modelId: Int,
    val left: BatteryPart,
    val right: BatteryPart,
    val case: BatteryPart,
    val leftInCase: Boolean?,
    val rightInCase: Boolean?,
    val advertiserInCase: Boolean?,
    val lidOpen: Boolean?,
    val paired: Boolean,
    val connectionState: String,
    val leftInEar: Boolean,
    val rightInEar: Boolean,
    val rssi: Int = 0,
    val seenAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val APPLE_COMPANY_ID = 0x004C
        const val PROXIMITY_TYPE = 0x07
        const val AIRPODS_PRO_3_MODEL = 0x2720
        private const val PAYLOAD_LENGTH = 27

        fun decode(payload: ByteArray): AirPodsBattery? {
            if (payload.size != PAYLOAD_LENGTH || (payload[0].toInt() and 0xFF) != PROXIMITY_TYPE) return null
            val modelId = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
            val status = payload[5].toInt() and 0xFF
            val podsBattery = payload[6].toInt() and 0xFF
            val flagsCase = payload[7].toInt() and 0xFF
            val lid = payload[8].toInt() and 0xFF
            val paired = payload[2].toInt() == 1
            val connectionState = when (payload[10].toInt() and 0xFF) {
                0x00 -> "Desconectado"
                0x04 -> "En espera"
                0x05 -> "Reproduciendo música"
                0x06 -> "En llamada"
                0x07 -> "Llamando"
                0x09 -> "Finalizando llamada"
                else -> "Desconocido"
            }
            val primaryLeft = ((status shr 5) and 1) == 1
            val thisInCase = ((status shr 6) and 1) == 1
            val isFlipped = !primaryLeft
            val xorFactor = primaryLeft xor thisInCase
            val leftInEar = if (xorFactor) (status and 0x08) != 0 else (status and 0x02) != 0
            val rightInEar = if (xorFactor) (status and 0x02) != 0 else (status and 0x08) != 0
            val leftNibble = if (isFlipped) (podsBattery shr 4) and 0x0F else podsBattery and 0x0F
            val rightNibble = if (isFlipped) podsBattery and 0x0F else (podsBattery shr 4) and 0x0F
            val caseNibble = flagsCase and 0x0F
            val flags = (flagsCase shr 4) and 0x0F
            val leftCharging = if (isFlipped) (flags and 0x02) != 0 else (flags and 0x01) != 0
            val rightCharging = if (isFlipped) (flags and 0x01) != 0 else (flags and 0x02) != 0
            fun level(n: Int): Int? = when (n) {
                in 0x0..0x9 -> n * 10
                in 0xA..0xE -> 100
                else -> null
            }
            return AirPodsBattery(
                modelId,
                BatteryPart(level(leftNibble), leftCharging),
                BatteryPart(level(rightNibble), rightCharging),
                BatteryPart(level(caseNibble), (flags and 0x04) != 0),
                null,
                null,
                thisInCase,
                ((lid shr 3) and 1) == 0,
                paired,
                connectionState,
                leftInEar,
                rightInEar
            )
        }
    }
}
