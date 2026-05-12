package com.chaomixian.vflow.core.workflow.module.system

import android.media.AudioManager
import com.chaomixian.vflow.core.module.normalizeEnumValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationModeModuleTest {

    @Test
    fun `set on switches ringer mode to vibrate`() {
        val controller = FakeVibrationModeController(AudioManager.RINGER_MODE_NORMAL)

        val result = VibrationModeModule.applyAction(VibrationModeModule.ACTION_ON, controller)

        assertEquals(AudioManager.RINGER_MODE_VIBRATE, controller.ringerMode)
        assertTrue(result.isVibrationMode)
        assertEquals(VibrationModeModule.ACTION_ON, result.resolvedAction)
    }

    @Test
    fun `set off switches ringer mode to normal`() {
        val controller = FakeVibrationModeController(AudioManager.RINGER_MODE_VIBRATE)

        val result = VibrationModeModule.applyAction(VibrationModeModule.ACTION_OFF, controller)

        assertEquals(AudioManager.RINGER_MODE_NORMAL, controller.ringerMode)
        assertFalse(result.isVibrationMode)
        assertEquals(VibrationModeModule.ACTION_OFF, result.resolvedAction)
    }

    @Test
    fun `toggle leaves vibration mode when already vibrating`() {
        val controller = FakeVibrationModeController(AudioManager.RINGER_MODE_VIBRATE)

        val result = VibrationModeModule.applyAction(VibrationModeModule.ACTION_TOGGLE, controller)

        assertEquals(AudioManager.RINGER_MODE_NORMAL, controller.ringerMode)
        assertFalse(result.isVibrationMode)
        assertEquals(VibrationModeModule.ACTION_OFF, result.resolvedAction)
    }

    @Test
    fun `toggle enters vibration mode from non vibration mode`() {
        val controller = FakeVibrationModeController(AudioManager.RINGER_MODE_SILENT)

        val result = VibrationModeModule.applyAction(VibrationModeModule.ACTION_TOGGLE, controller)

        assertEquals(AudioManager.RINGER_MODE_VIBRATE, controller.ringerMode)
        assertTrue(result.isVibrationMode)
        assertEquals(VibrationModeModule.ACTION_ON, result.resolvedAction)
    }

    @Test
    fun `legacy localized action is normalized`() {
        val action = VibrationModeModule()
            .getInputs()
            .normalizeEnumValue("action", "开启")

        assertEquals(VibrationModeModule.ACTION_ON, action)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown action is rejected`() {
        VibrationModeModule.applyAction("unknown", FakeVibrationModeController(AudioManager.RINGER_MODE_NORMAL))
    }

    private class FakeVibrationModeController(
        override var ringerMode: Int
    ) : VibrationModeController
}
