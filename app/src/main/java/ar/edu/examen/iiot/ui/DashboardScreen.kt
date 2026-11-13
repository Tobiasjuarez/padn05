package ar.edu.examen.iiot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.edu.examen.iiot.model.PlantRules
import ar.edu.examen.iiot.model.Reading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: DashboardState,
    onFilterChanged: (String) -> Unit,
    onThresholdClick: () -> Unit,
    onThresholdInputChange: (String) -> Unit,
    onSaveThreshold: () -> Unit,
    onDismissThreshold: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onCloseHistory: () -> Unit,
    onDismissNotification: () -> Unit
) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Equipos monitoreados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Alertas: ${state.alertCount}",
                    color = if (state.alertCount > 0) Color(0xFFB3261E) else Color(0xFF256D41),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.filter,
                onValueChange = onFilterChanged,
                label = { Text("Buscar equipo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onThresholdClick) {
                    Text("Umbral: ${PlantRules.formatTemperatureLimit(state.threshold)} °C")
                }
                if (state.loading) {
                    Text("Sincronizando...", modifier = Modifier.padding(top = 12.dp))
                }
            }
            if (state.notification != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = Color(0xFFFFF3D9),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(state.notification, color = Color(0xFF5F4500))
                        Button(onClick = onDismissNotification) { Text("Cerrar aviso") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.readings.isEmpty()) {
                Text(
                    "No hay equipos para mostrar.",
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.readings, key = { it.machineId }) { reading ->
                    EquipmentCard(reading, state.threshold, onHistoryClick)
                }
                item {
                    Text(
                        "Datos de demostración: ThingSpeak aporta una medición " +
                            "pública al Compresor A; el resto es simulado. " +
                            "Los sensores físicos corresponden al teléfono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }

        state.thresholdEditor?.let { editor ->
            ThresholdDialog(
                editor = editor,
                onInputChange = onThresholdInputChange,
                onSave = onSaveThreshold,
                onDismiss = onDismissThreshold
            )
        }
        state.history?.let { history ->
            HistoryDialog(history = history, onClose = onCloseHistory)
        }
    }
}

@Composable
private fun ThresholdDialog(
    editor: ThresholdEditorState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Umbral de alarma") },
        text = {
            Column {
                Text(
                    "Temperatura entre ${PlantRules.formatTemperatureLimit(PlantRules.MIN_TEMPERATURE_LIMIT)} " +
                        "y ${PlantRules.formatTemperatureLimit(PlantRules.MAX_TEMPERATURE_LIMIT)} °C. " +
                        "Una lectura pasa a ALERTA si es mayor o igual al umbral.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.input,
                    onValueChange = onInputChange,
                    label = { Text("Umbral (°C)") },
                    singleLine = true,
                    isError = editor.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (editor.error != null) {
                    Text(
                        editor.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.saving) {
                Text(if (editor.saving) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun HistoryDialog(history: HistoryState, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Historial · ${history.machineName}") },
        text = {
            when {
                history.loading -> Text("Cargando historial...")
                history.error != null -> Text(history.error, color = MaterialTheme.colorScheme.error)
                history.readings.isEmpty() -> Text(
                    "Todavía no hay lecturas guardadas para este equipo. " +
                        "Usá \"Sincronizar telemetría\" para registrar nuevas."
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Últimas ${history.readings.size} lecturas (más reciente primero)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    items(history.readings, key = { it.id }) { reading ->
                        HistoryRow(reading)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Cerrar") }
        }
    )
}

@Composable
private fun HistoryRow(reading: Reading) {
    val time = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "AR"))
        .format(Date(reading.timestamp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(time, style = MaterialTheme.typography.bodySmall)
            Text(
                "${"%.1f".format(reading.temperature)} °C",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "Origen: ${reading.source}",
            color = Color(0xFF64748B),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EquipmentCard(reading: Reading, threshold: Double, onHistoryClick: (String) -> Unit) {
    val alert = PlantRules.requiresAttention(reading, threshold)
    val time = SimpleDateFormat("dd/MM HH:mm", Locale("es", "AR"))
        .format(Date(reading.timestamp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(reading.machineName, fontWeight = FontWeight.Bold)
                    Text(reading.machineId, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    if (alert) "ALERTA" else "NORMAL",
                    color = if (alert) Color(0xFFB3261E) else Color(0xFF238147),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${"%.1f".format(reading.temperature)} °C",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Humedad ${"%.0f".format(reading.humidity)} %  ·  " +
                    "Vibración ${"%.1f".format(reading.vibration)} mm/s",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "$time · ${reading.source}",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedButton(
                onClick = { onHistoryClick(reading.machineId) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Ver historial")
            }
        }
    }
}
