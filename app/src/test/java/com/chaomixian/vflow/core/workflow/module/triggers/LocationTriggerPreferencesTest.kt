package com.chaomixian.vflow.core.workflow.module.triggers

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationTriggerPreferencesTest {

    @Test
    fun `default interval is five minutes`() {
        assertEquals(5, LocationTriggerPreferences.DEFAULT_INTERVAL_MINUTES)
    }

    @Test
    fun `normalize clamps interval to supported range`() {
        assertEquals(1, LocationTriggerPreferences.normalizeIntervalMinutes(0))
        assertEquals(1, LocationTriggerPreferences.normalizeIntervalMinutes(-10))
        assertEquals(12, LocationTriggerPreferences.normalizeIntervalMinutes(12))
        assertEquals(30, LocationTriggerPreferences.normalizeIntervalMinutes(99))
    }
}
