package com.chaomixian.vflow.core.workflow.module.triggers

import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.types.VTypeRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalVariableTriggerModuleTest {

    private val module = GlobalVariableTriggerModule()

    @Test
    fun `module exposes variable name input`() {
        val input = module.getInputs().single { it.id == "variable_name" }

        assertEquals("vflow.trigger.global_variable", module.id)
        assertEquals(ParameterType.STRING, input.staticType)
        assertEquals("", input.defaultValue)
        assertEquals(false, input.acceptsMagicVariable)
    }

    @Test
    fun `module exposes variable change outputs`() {
        val outputs = module.getOutputs().associateBy { it.id }

        assertEquals(VTypeRegistry.STRING.id, outputs["variable_name"]?.typeName)
        assertEquals(VTypeRegistry.STRING.id, outputs["old_value"]?.typeName)
        assertEquals(VTypeRegistry.STRING.id, outputs["new_value"]?.typeName)
    }
}
