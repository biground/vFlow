package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStoppedTriggerStateTest {

    @Test
    fun `does not trigger when initial state is unavailable`() {
        val state = ShizukuStoppedTriggerState()

        assertFalse(state.update(isAvailable = false))
    }

    @Test
    fun `triggers once when shizuku changes from available to unavailable`() {
        val state = ShizukuStoppedTriggerState()

        assertFalse(state.update(isAvailable = true))
        assertTrue(state.update(isAvailable = false))
        assertFalse(state.update(isAvailable = false))
    }

    @Test
    fun `arms again after shizuku becomes available`() {
        val state = ShizukuStoppedTriggerState()

        state.update(isAvailable = true)
        assertTrue(state.update(isAvailable = false))
        assertFalse(state.update(isAvailable = false))
        assertFalse(state.update(isAvailable = true))
        assertTrue(state.update(isAvailable = false))
    }

    @Test
    fun `reset forgets previous available state`() {
        val state = ShizukuStoppedTriggerState()

        state.update(isAvailable = true)
        state.reset()

        assertFalse(state.update(isAvailable = false))
    }
}
