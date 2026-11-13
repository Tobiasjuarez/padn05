package ar.edu.examen.iiot

import ar.edu.examen.iiot.model.PlantRules
import ar.edu.examen.iiot.model.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantRulesTest {
    private fun sample(temp: Double) = Reading(
        machineId = "TEST-01",
        machineName = "Equipo de prueba",
        temperature = temp,
        humidity = 50.0,
        vibration = 1.0,
        timestamp = 1L,
        source = "Test"
    )

    // --- Tests originales (umbral por defecto, 65 °C) ---

    @Test fun belowThresholdIsNormal() {
        assertFalse(PlantRules.requiresAttention(sample(64.9)))
    }

    @Test fun thresholdIsAlert() {
        assertTrue(PlantRules.requiresAttention(sample(65.0)))
    }

    // --- Actividad 1: umbral configurable ---

    @Test fun customThresholdIsInclusive() {
        assertTrue(PlantRules.requiresAttention(sample(80.0), limit = 80.0))
        assertFalse(PlantRules.requiresAttention(sample(79.9), limit = 80.0))
    }

    @Test fun alertCountFollowsTheConfiguredThreshold() {
        val readings = listOf(sample(47.3), sample(56.2), sample(69.5))
        assertEquals(1, PlantRules.countAlerts(readings, 65.0))
        assertEquals(2, PlantRules.countAlerts(readings, 50.0))
        assertEquals(0, PlantRules.countAlerts(readings, 70.0))
    }

    @Test fun acceptsLimitsOfTheValidRange() {
        assertEquals(30.0, PlantRules.parseTemperatureLimit("30")!!, 0.0)
        assertEquals(120.0, PlantRules.parseTemperatureLimit("120")!!, 0.0)
        assertEquals(72.5, PlantRules.parseTemperatureLimit(" 72,5 ")!!, 0.0)
        assertEquals(72.5, PlantRules.parseTemperatureLimit("72.5")!!, 0.0)
    }

    @Test fun rejectsValuesOutOfRangeOrNotNumeric() {
        assertNull(PlantRules.parseTemperatureLimit("29.9"))
        assertNull(PlantRules.parseTemperatureLimit("120.1"))
        assertNull(PlantRules.parseTemperatureLimit("abc"))
        assertNull(PlantRules.parseTemperatureLimit(""))
        assertNull(PlantRules.parseTemperatureLimit("NaN"))
        assertNull(PlantRules.parseTemperatureLimit("-50"))
    }

    @Test fun formatsLimitWithoutUnnecessaryDecimals() {
        assertEquals("65", PlantRules.formatTemperatureLimit(65.0))
        assertEquals("72.5", PlantRules.formatTemperatureLimit(72.5))
    }
}
