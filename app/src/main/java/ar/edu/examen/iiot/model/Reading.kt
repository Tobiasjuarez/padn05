package ar.edu.examen.iiot.model

data class Reading(
    val id: Long = 0,
    val machineId: String,
    val machineName: String,
    val temperature: Double,
    val humidity: Double,
    val vibration: Double,
    val timestamp: Long,
    val source: String
)

/**
 * Reglas de negocio de la planta. Es la ÚNICA fuente de verdad del criterio de alerta:
 * la pantalla y el ViewModel la usan en lugar de repetir el número 65.
 */
object PlantRules {
    const val DEFAULT_TEMPERATURE_LIMIT = 65.0
    const val MIN_TEMPERATURE_LIMIT = 30.0
    const val MAX_TEMPERATURE_LIMIT = 120.0
    const val HISTORY_SIZE = 10

    /** ALERTA cuando la temperatura es mayor o igual al umbral configurado. */
    fun requiresAttention(reading: Reading, limit: Double = DEFAULT_TEMPERATURE_LIMIT): Boolean =
        reading.temperature >= limit

    fun countAlerts(readings: List<Reading>, limit: Double): Int =
        readings.count { requiresAttention(it, limit) }

    /**
     * Convierte el texto ingresado en un umbral válido (30 a 120 °C, inclusive).
     * Acepta coma o punto decimal. Devuelve null si no es un número o está fuera de rango.
     */
    fun parseTemperatureLimit(text: String): Double? =
        text.trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it in MIN_TEMPERATURE_LIMIT..MAX_TEMPERATURE_LIMIT }

    fun isValidTemperatureLimit(value: Double): Boolean =
        value in MIN_TEMPERATURE_LIMIT..MAX_TEMPERATURE_LIMIT

    /** 65.0 -> "65", 72.5 -> "72.5" */
    fun formatTemperatureLimit(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
