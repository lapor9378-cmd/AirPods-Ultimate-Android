package com.airpodsultimate

data class BatteryPart(
    val percent: Int?,
    val charging: Boolean
)

data class AirPodsBattery(
    val modelId: Int,
    val left: BatteryPart,
    val right: BatteryPart,
    val case: BatteryPart,
    val leftInCase: Boolean?,
    val rightInCase: Boolean?,
    val rssi: Int = 0,
    val seenAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val APPLE_COMPANY_ID = 0x004C
        const val PROXIMITY_TYPE = 0x07
        const val AIRPODS_PRO_3_MODEL = 0x2720
        private const val PAYLOAD_LENGTH = 27

        fun decode(payload: ByteArray): AirPodsBattery? {
            if (payload.size != PAYLOAD_LENGTH) return null
            if ((payload[0].toInt() and 0xFF) != PROXIMITY_TYPE) return null

            val modelId = ((payload[3].toInt() and 0xFF) shl 8) or
                (payload[4].toInt() and 0xFF)
            val status = payload[5].toInt() and 0xFF
            val buds = payload[6].toInt() and 0xFF
            val caseByte = payload[7].toInt() and 0xFF

            val primaryIsLeft = (status and 0x20) != 0
            val primary = buds ushr 4
            val secondary = buds and 0x0F
            val leftNibble = if (primaryIsLeft) primary else secondary
            val rightNibble = if (primaryIsLeft) secondary else primary

            val chargingFlags = caseByte ushr 4
            val caseNibble = caseByte and 0x0F
            val knownStatuses = setOf(0x01, 0x02, 0x03, 0x0B, 0x11, 0x13, 0x21, 0x22, 0x23, 0x2B, 0x31, 0x33, 0x51, 0x53, 0x55, 0x71, 0x73, 0x75)
            val bothInCase = status == 0x55 || status == 0x75
            val leftInCase = when {
                status !in knownStatuses -> null
                bothInCase -> true
                status in setOf(0x02, 0x11, 0x13, 0x71, 0x73) -> true
                else -> false
            }
            val rightInCase = when {
                status !in knownStatuses -> null
                bothInCase -> true
                status in setOf(0x22, 0x31, 0x33, 0x51, 0x53) -> true
                else -> false
            }

            fun level(nibble: Int): Int? =
                if (nibble == 0x0F) null else nibble.coerceAtMost(10) * 10

            return AirPodsBattery(
                modelId = modelId,
                left = BatteryPart(level(leftNibble), (chargingFlags and 0x02) != 0),
                right = BatteryPart(level(rightNibble), (chargingFlags and 0x01) != 0),
                case = BatteryPart(level(caseNibble), (chargingFlags and 0x04) != 0),
                leftInCase = leftInCase,
                rightInCase = rightInCase
            )
        }
    }
}
