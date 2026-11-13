package ar.edu.examen.iiot

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ar.edu.examen.iiot.data.PlantRepository
import ar.edu.examen.iiot.databinding.ActivityMainBinding
import ar.edu.examen.iiot.ui.DashboardScreen
import ar.edu.examen.iiot.ui.PlantViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var luminosity: Float? = null
    private var movement: Float? = null

    private val viewModel: PlantViewModel by viewModels {
        // applicationContext: el ViewModel sobrevive a la Activity; pasarle "this" la retenía (leak).
        PlantViewModel.Factory(PlantRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        updatePhysicalSensorLabel()

        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.composePanel.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composePanel.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            DashboardScreen(
                state = state,
                onFilterChanged = viewModel::filter,
                onThresholdClick = viewModel::configureThreshold,
                onThresholdInputChange = viewModel::onThresholdInputChange,
                onSaveThreshold = viewModel::saveThreshold,
                onDismissThreshold = viewModel::dismissThresholdEditor,
                onHistoryClick = viewModel::showHistory,
                onCloseHistory = viewModel::closeHistory,
                onDismissNotification = viewModel::dismissNotification
            )
        }
        // La parte XML solo escucha mientras la pantalla está visible (antes seguía en segundo plano).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    binding.txtConnection.text = state.syncMessage
                    binding.btnRefresh.isEnabled = !state.loading
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> luminosity = event.values[0]
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                movement = sqrt(x * x + y * y + z * z)
            }
        }
        updatePhysicalSensorLabel()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updatePhysicalSensorLabel() {
        val lightText = luminosity?.let { "${"%.0f".format(it)} lux" } ?: "no disponible"
        val moveText = movement?.let { "${"%.1f".format(it)} m/s²" } ?: "no disponible"
        binding.txtPhysicalSensors.text =
            "Sensores del teléfono · Luz: $lightText  |  Aceleración: $moveText"
    }
}
