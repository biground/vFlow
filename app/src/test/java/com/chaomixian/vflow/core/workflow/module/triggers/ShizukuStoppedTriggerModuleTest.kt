package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.ContextWrapper
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionServices
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import java.io.File
import java.util.Stack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStoppedTriggerModuleTest {

    @Test
    fun `module exposes reason and checked at outputs`() {
        val outputs = ShizukuStoppedTriggerModule().getOutputs(null)

        assertEquals(VTypeRegistry.STRING.id, outputs.first { it.id == "reason" }.typeName)
        assertEquals(VTypeRegistry.NUMBER.id, outputs.first { it.id == "checked_at" }.typeName)
    }

    @Test
    fun `execute maps trigger data to outputs`() = runBlocking {
        val module = ShizukuStoppedTriggerModule()
        val context = createContext(
            triggerData = ShizukuStoppedTriggerData(
                reason = ShizukuStoppedTriggerData.REASON_BINDER_DEAD,
                checkedAt = 1234L
            )
        )

        val result = module.execute(context) { }

        assertTrue(result is ExecutionResult.Success)
        val outputs = (result as ExecutionResult.Success).outputs
        assertEquals("binder_dead", (outputs["reason"] as VString).raw)
        assertEquals(1234L, (outputs["checked_at"] as VNumber).raw.toLong())
    }

    @Test
    fun `execute uses safe defaults without trigger data`() = runBlocking {
        val module = ShizukuStoppedTriggerModule()

        val result = module.execute(createContext()) { }

        assertTrue(result is ExecutionResult.Success)
        val outputs = (result as ExecutionResult.Success).outputs
        assertEquals("unknown", (outputs["reason"] as VString).raw)
        assertEquals(0L, (outputs["checked_at"] as VNumber).raw.toLong())
    }

    private fun createContext(
        triggerData: ShizukuStoppedTriggerData? = null
    ): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf<String, VObject>(),
            magicVariables = mutableMapOf(),
            services = ExecutionServices(),
            allSteps = emptyList(),
            currentStepIndex = 0,
            stepOutputs = mutableMapOf(),
            loopStack = Stack(),
            triggerData = triggerData,
            namedVariables = mutableMapOf(),
            workDir = File("build/test-workdir")
        )
    }
}
