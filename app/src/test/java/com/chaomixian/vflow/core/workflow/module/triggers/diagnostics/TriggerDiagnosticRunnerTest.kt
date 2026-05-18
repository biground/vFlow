package com.chaomixian.vflow.core.workflow.module.triggers.diagnostics

import com.chaomixian.vflow.core.workflow.module.triggers.LocationTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.BatteryTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.BluetoothTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.PowerTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.ScreenTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.WifiTriggerModule
import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerDiagnosticRunnerTest {

    @Test
    fun `only triggers with complete diagnostics should expose test entry`() {
        assertTrue(TriggerDiagnosticRunner.supportsCompleteDiagnostic(LocationTriggerModule().id))
        assertTrue(TriggerDiagnosticRunner.supportsCompleteDiagnostic(WifiTriggerModule().id))
        assertTrue(TriggerDiagnosticRunner.supportsCompleteDiagnostic(BluetoothTriggerModule().id))
        assertTrue(TriggerDiagnosticRunner.supportsCompleteDiagnostic(BatteryTriggerModule().id))
        assertTrue(TriggerDiagnosticRunner.supportsCompleteDiagnostic(ScreenTriggerModule().id))
        assertTrue(TriggerDiagnosticRunner.supportsCompleteDiagnostic(PowerTriggerModule().id))
        assertEquals(false, TriggerDiagnosticRunner.supportsCompleteDiagnostic("vflow.trigger.sms"))
    }

    @Test
    fun `battery diagnostic matches current level and explains threshold events`() {
        val step = ActionStep(
            id = "battery-1",
            moduleId = "vflow.trigger.battery",
            parameters = mapOf(
                "above_or_below" to BatteryTriggerModule.VALUE_BELOW,
                "level" to 50
            )
        )

        val result = TriggerDiagnosticRunner.diagnoseBatteryMatch(step, currentLevel = 42)

        assertEquals(TriggerDiagnosticStatus.SUCCESS, result.status)
        assertTrue(result.message.contains("跨过阈值"))
    }

    @Test
    fun `power diagnostic matches current charging state`() {
        val step = ActionStep(
            id = "power-1",
            moduleId = "vflow.trigger.power",
            parameters = mapOf("power_state" to PowerTriggerModule.VALUE_CONNECTED)
        )

        val result = TriggerDiagnosticRunner.diagnosePowerMatch(step, isConnected = true)

        assertEquals(TriggerDiagnosticStatus.SUCCESS, result.status)
    }

    @Test
    fun `screen diagnostic matches interactive and unlocked state`() {
        val unlockedStep = ActionStep(
            id = "screen-1",
            moduleId = "vflow.trigger.screen",
            parameters = mapOf("screen_event" to ScreenTriggerModule.VALUE_UNLOCKED)
        )

        val result = TriggerDiagnosticRunner.diagnoseScreenMatch(
            step = unlockedStep,
            isInteractive = true,
            isUnlocked = true
        )

        assertEquals(TriggerDiagnosticStatus.SUCCESS, result.status)
        assertTrue(result.message.contains("解锁事件"))
    }

    @Test
    fun `wifi diagnostic matches connected target`() {
        val step = ActionStep(
            id = "wifi-1",
            moduleId = "vflow.trigger.wifi",
            parameters = mapOf(
                "trigger_type" to WifiTriggerModule.TRIGGER_TYPE_CONNECTION,
                "connection_event" to WifiTriggerModule.CONNECTION_EVENT_CONNECTED,
                "network_target" to "Office"
            )
        )

        val result = TriggerDiagnosticRunner.diagnoseWifiMatch(
            step = step,
            isWifiEnabled = true,
            connectedSsid = "Office"
        )

        assertEquals(TriggerDiagnosticStatus.SUCCESS, result.status)
    }

    @Test
    fun `bluetooth diagnostic reports unknown when device connections cannot be read`() {
        val step = ActionStep(
            id = "bluetooth-1",
            moduleId = "vflow.trigger.bluetooth",
            parameters = mapOf(
                "trigger_type" to BluetoothTriggerModule.TRIGGER_TYPE_DEVICE,
                "device_event" to BluetoothTriggerModule.DEVICE_EVENT_CONNECTED,
                "device_address" to BluetoothTriggerModule.ANY_DEVICE_ADDRESS
            )
        )

        val result = TriggerDiagnosticRunner.diagnoseBluetoothMatch(
            step = step,
            isEnabled = true,
            connectedDevices = null
        )

        assertEquals(TriggerDiagnosticStatus.UNKNOWN, result.status)
    }

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
