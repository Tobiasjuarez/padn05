package ar.edu.examen.iiot.data

import android.content.Context
import ar.edu.examen.iiot.model.PlantRules
import ar.edu.examen.iiot.model.Reading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Resultado de una sincronización: el mensaje viaja con el resultado, no en un campo mutable. */
data class SyncResult(val message: String)

/**
 * Todas las operaciones de disco corren en Dispatchers.IO: el ViewModel puede llamarlas
 * desde viewModelScope sin bloquear el hilo principal.
 */
class PlantRepository(context: Context) {
    // applicationContext: el repositorio vive dentro del ViewModel, que sobrevive a la Activity.
    private val database = PlantDatabase(context.applicationContext)
    private val api = Retrofit.Builder()
        .baseUrl("https://api.thingspeak.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ThingSpeakApi::class.java)

    suspend fun localReadings(): List<Reading> = withContext(Dispatchers.IO) {
        val rows = database.latest()
        if (rows.isNotEmpty()) {
            rows
        } else {
            sampleReadings(null, null, false).forEach(database::insert)
            database.latest()
        }
    }

    suspend fun search(query: String): List<Reading> = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.isEmpty()) database.latest() else database.findLatestByName(text)
    }

    suspend fun history(machineId: String): List<Reading> = withContext(Dispatchers.IO) {
        database.history(machineId, PlantRules.HISTORY_SIZE)
    }

    /** Umbral persistido; en una instalación nueva (o con un valor inválido) es 65 °C. */
    suspend fun temperatureLimit(): Double = withContext(Dispatchers.IO) {
        database.readConfig(KEY_TEMPERATURE_LIMIT)
            ?.toDoubleOrNull()
            ?.takeIf { PlantRules.isValidTemperatureLimit(it) }
            ?: PlantRules.DEFAULT_TEMPERATURE_LIMIT
    }

    suspend fun saveTemperatureLimit(value: Double) {
        require(PlantRules.isValidTemperatureLimit(value)) { "Umbral fuera de rango: $value" }
        withContext(Dispatchers.IO) {
            database.writeConfig(KEY_TEMPERATURE_LIMIT, value.toString())
        }
    }

    suspend fun sync(): SyncResult {
        var remoteTemperature: Double? = null
        var remoteHumidity: Double? = null

        val message = try {
            val feed = api.getLatest().feeds.lastOrNull()
            remoteTemperature = feed?.temperature?.toDoubleOrNull()
                ?.takeIf { it in -20.0..150.0 }
            remoteHumidity = feed?.humidity?.toDoubleOrNull()
                ?.takeIf { it in 0.0..100.0 }
            if (remoteTemperature != null) {
                "Sincronizado con ThingSpeak (canal público de demostración)"
            } else {
                "Sin datos remotos válidos: usando datos simulados"
            }
        } catch (exception: CancellationException) {
            throw exception // no tragarse la cancelación de la corrutina
        } catch (exception: Exception) {
            "Sin conexión a API: usando datos simulados"
        }

        val remoteOk = remoteTemperature != null
        withContext(Dispatchers.IO) {
            sampleReadings(remoteTemperature, remoteHumidity, remoteOk).forEach(database::insert)
        }
        return SyncResult(message)
    }

    private fun sampleReadings(
        remoteTemperature: Double?,
        remoteHumidity: Double?,
        remoteOk: Boolean
    ): List<Reading> {
        val now = System.currentTimeMillis()
        val variation = ((now / 60_000) % 4).toDouble()
        return listOf(
            Reading(
                machineId = "CMP-01",
                machineName = "Compresor A",
                temperature = remoteTemperature ?: 56.2 + variation,
                humidity = remoteHumidity ?: 42.0,
                vibration = 1.6,
                timestamp = now,
                source = if (remoteOk) "ThingSpeak (demo externo)" else "Simulación local"
            ),
            Reading(
                machineId = "PMP-02",
                machineName = "Bomba B",
                temperature = 47.3 + variation / 2,
                humidity = 36.0,
                vibration = 0.8,
                timestamp = now,
                source = "Simulación local"
            ),
            Reading(
                machineId = "OVN-03",
                machineName = "Horno C",
                temperature = 69.5 + variation,
                humidity = 24.0,
                vibration = 0.3,
                timestamp = now,
                source = "Simulación local"
            )
        )
    }

    private companion object {
        const val KEY_TEMPERATURE_LIMIT = "temperature_limit_celsius"
    }
}
