package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.GlobalVariableChangeEvent
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.TriggerSpec
import com.chaomixian.vflow.core.workflow.model.Workflow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalVariableTriggerHandlerTest {

    @Test
    fun `matches configured global variable name exactly`() {
        val trigger = triggerFor("模式")
        val event = GlobalVariableChangeEvent("模式", VString("通常"), VString("工作"))

        assertTrue(GlobalVariableTriggerHandler.matches(trigger, event))
    }

    @Test
    fun `ignores changes for other global variables`() {
        val trigger = triggerFor("模式")
        val event = GlobalVariableChangeEvent("今日已上班", VString("false"), VString("true"))

        assertFalse(GlobalVariableTriggerHandler.matches(trigger, event))
    }

    private fun triggerFor(variableName: String): TriggerSpec {
        return TriggerSpec(
            workflow = Workflow(id = "workflow-1", name = "变量触发测试"),
            step = ActionStep(
                id = "trigger-1",
                moduleId = "vflow.trigger.global_variable",
                parameters = mapOf("variable_name" to variableName)
            )
        )
    }
}
