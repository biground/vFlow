package com.chaomixian.vflow.core.workflow.module.triggers

import org.junit.Assert.assertEquals
import org.junit.Test

class AmapApiKeyPreferencesTest {

    @Test
    fun `normalize trims user provided api key`() {
        assertEquals("abc123", AmapApiKeyPreferences.normalizeApiKey("  abc123  "))
        assertEquals("", AmapApiKeyPreferences.normalizeApiKey("   "))
    }
}
