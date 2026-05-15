package com.chaomixian.vflow.core.workflow.module.triggers.diagnostics

import com.chaomixian.vflow.core.workflow.module.triggers.LocationTriggerModule
import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerDiagnosticRunnerTest {

    @Test
    fun `location config rejects out of range coordinates and radius`() {
        val badLatitude = locationStep(latitude = 91.0)
        val badLongitude = locationStep(longitude = 181.0)
        val badRadius = locationStep(radius = 0.0)

        assertEquals(
            TriggerDiagnosticStatus.INVALID_CONFIG,
            TriggerDiagnosticRunner.diagnoseLocationConfiguration(badLatitude).status
        )
        assertEquals(
            TriggerDiagnosticStatus.INVALID_CONFIG,
            TriggerDiagnosticRunner.diagnoseLocationConfiguration(badLongitude).status
        )
        assertEquals(
            TriggerDiagnosticStatus.INVALID_CONFIG,
            TriggerDiagnosticRunner.diagnoseLocationConfiguration(badRadius).status
        )
    }

    @Test
    fun `enter location matches when current position is inside radius`() {
        val step = locationStep(latitude = 39.9042, longitude = 116.4074, radius = 100.0)

        val result = TriggerDiagnosticRunner.diagnoseLocationMatch(
            step = step,
            currentLatitude = 39.9043,
            currentLongitude = 116.4075,
            sourceName = "高德定位"
        )

        assertEquals(TriggerDiagnosticStatus.SUCCESS, result.status)
        assertTrue(result.message.contains("高德定位"))
    }

    @Test
    fun `exit location reports history limitation instead of treating outside as trigger`() {
        val step = locationStep(
            event = LocationTriggerModule.EVENT_EXIT,
            latitude = 39.9042,
            longitude = 116.4074,
            radius = 100.0
        )

        val result = TriggerDiagnosticRunner.diagnoseLocationMatch(
            step = step,
            currentLatitude = 40.0,
            currentLongitude = 116.5
        )

        assertEquals(TriggerDiagnosticStatus.UNKNOWN, result.status)
        assertTrue(result.message.contains("历史"))
    }

    private fun locationStep(
        event: String = LocationTriggerModule.EVENT_ENTER,
        latitude: Double = 39.9042,
        longitude: Double = 116.4074,
        radius: Double = 500.0
    ): ActionStep {
        return ActionStep(
            id = "trigger-1",
            moduleId = "vflow.trigger.location",
            parameters = mapOf(
                "event" to event,
                "latitude" to latitude,
                "longitude" to longitude,
                "radius" to radius
            )
        )
    }
}
