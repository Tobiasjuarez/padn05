package ar.edu.examen.iiot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ar.edu.examen.iiot.data.PlantRepository
import ar.edu.examen.iiot.model.PlantRules
import ar.edu.examen.iiot.model.Reading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Diálogo de edición del umbral (null = cerrado). */
data class ThresholdEditorState(
    val input: String,
    val error: String? = null,
    val saving: Boolean = false
)

/** Historial de un equipo (null = cerrado). */
data class HistoryState(
    val machineId: String,
    val machineName: String,
    val readings: List<Reading> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

data class DashboardState(
    val readings: List<Reading> = emptyList(),
    val filter: String = "",
    val loading: Boolean = false,
    val syncMessage: String = "Lecturas locales",
    val notification: String? = null,
    val threshold: Double = PlantRules.DEFAULT_TEMPERATURE_LIMIT,
    val alertCount: Int = 0,
    val thresholdEditor: ThresholdEditorState? = null,
    val history: HistoryState? = null
)

class PlantViewModel(private val repository: PlantRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Antes se leía la base en el hilo principal dentro del init.
        viewModelScope.launch {
            try {
                val limit = repository.temperatureLimit()
                _state.update { it.copy(threshold = limit) }
                setReadings(repository.localReadings())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.update { it.copy(syncMessage = "No se pudieron leer las lecturas locales") }
            }
        }
    }

    /** El contador de alertas siempre se calcula con el umbral vigente (fuente única). */
    private fun setReadings(items: List<Reading>) {
        _state.update { current ->
            current.copy(
                readings = items,
                alertCount = PlantRules.countAlerts(items, current.threshold)
            )
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, notification = null) }
            try {
                val result = repository.sync()
                setReadings(repository.search(_state.value.filter))
                _state.update { it.copy(syncMessage = result.message) }
                _state.value.history?.let { reloadHistory(it.machineId) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.update { it.copy(syncMessage = "No se pudo actualizar la telemetría") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun filter(text: String) {
        _state.update { it.copy(filter = text) }
        // Se cancela la búsqueda anterior para que un resultado viejo no pise uno nuevo.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                setReadings(repository.search(text))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.update { it.copy(notification = "La búsqueda no se pudo completar") }
            }
        }
    }

    // ---------- Actividad 1: umbral configurable ----------

    fun configureThreshold() {
        _state.update {
            it.copy(
                thresholdEditor = ThresholdEditorState(
                    input = PlantRules.formatTemperatureLimit(it.threshold)
                )
            )
        }
    }

    fun onThresholdInputChange(text: String) {
        _state.update { current ->
            current.copy(thresholdEditor = current.thresholdEditor?.copy(input = text, error = null))
        }
    }

    fun saveThreshold() {
        val editor = _state.value.thresholdEditor ?: return
        if (editor.saving) return
        val value = PlantRules.parseTemperatureLimit(editor.input)
        if (value == null) {
            _state.update {
                it.copy(
                    thresholdEditor = editor.copy(
                        error = "Ingresá un número entre " +
                            "${PlantRules.formatTemperatureLimit(PlantRules.MIN_TEMPERATURE_LIMIT)} y " +
                            "${PlantRules.formatTemperatureLimit(PlantRules.MAX_TEMPERATURE_LIMIT)} °C"
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(thresholdEditor = editor.copy(saving = true, error = null)) }
            try {
                repository.saveTemperatureLimit(value)
                // Recién después de persistir se actualiza la pantalla: estados y contador al instante.
                _state.update { current ->
                    current.copy(
                        threshold = value,
                        alertCount = PlantRules.countAlerts(current.readings, value),
                        thresholdEditor = null,
                        notification = "Umbral actualizado a " +
                            "${PlantRules.formatTemperatureLimit(value)} °C"
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        thresholdEditor = it.thresholdEditor?.copy(
                            saving = false,
                            error = "No se pudo guardar el umbral. Intentá de nuevo."
                        )
                    )
                }
            }
        }
    }

    fun dismissThresholdEditor() {
        _state.update { it.copy(thresholdEditor = null) }
    }

    // ---------- Actividad 2: historial por equipo ----------

    fun showHistory(machineId: String) {
        val name = _state.value.readings.firstOrNull { it.machineId == machineId }?.machineName
            ?: machineId
        // El filtro de búsqueda no se toca: al cerrar, el panel queda igual que antes.
        _state.update { it.copy(history = HistoryState(machineId = machineId, machineName = name)) }
        viewModelScope.launch { reloadHistory(machineId) }
    }

    private suspend fun reloadHistory(machineId: String) {
        try {
            val items = repository.history(machineId)
            _state.update { current ->
                val history = current.history
                if (history != null && history.machineId == machineId) {
                    current.copy(history = history.copy(readings = items, loading = false, error = null))
                } else {
                    current
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _state.update { current ->
                val history = current.history
                if (history != null && history.machineId == machineId) {
                    current.copy(
                        history = history.copy(loading = false, error = "No se pudo leer el historial")
                    )
                } else {
                    current
                }
            }
        }
    }

    fun closeHistory() {
        _state.update { it.copy(history = null) }
    }

    fun dismissNotification() {
        _state.update { it.copy(notification = null) }
    }

    class Factory(private val repository: PlantRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PlantViewModel::class.java))
            return PlantViewModel(repository) as T
        }
    }
}
