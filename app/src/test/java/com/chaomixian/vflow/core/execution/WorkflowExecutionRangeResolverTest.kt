package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.logic.EndIfModule
import com.chaomixian.vflow.core.workflow.module.logic.IF_END_ID
import com.chaomixian.vflow.core.workflow.module.logic.IF_START_ID
import com.chaomixian.vflow.core.workflow.module.logic.IfModule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WorkflowExecutionRangeResolverTest {

    @Before
    fun setUp() {
        ModuleRegistry.register(IfModule())
        ModuleRegistry.register(EndIfModule())
    }

    @Test
    fun resolveLocalRange_fromStepInsideBlock_stopsBeforeBlockEnd() {
        val steps = listOf(
            step("action.1"),
            step(IF_START_ID),
            step("action.2"),
            step("action.3"),
            step(IF_END_ID),
            step("action.4")
        )

        val range = WorkflowExecutionRangeResolver.resolveLocalRange(steps, startIndex = 2)

        assertEquals(2, range.startIndex)
        assertEquals(4, range.endExclusive)
    }

    @Test
    fun resolveLocalRange_fromBlockStart_includesWholeBlock() {
        val steps = listOf(
            step("action.1"),
            step(IF_START_ID),
            step("action.2"),
            step("action.3"),
            step(IF_END_ID),
            step("action.4")
        )

        val range = WorkflowExecutionRangeResolver.resolveLocalRange(steps, startIndex = 1)

        assertEquals(1, range.startIndex)
        assertEquals(5, range.endExclusive)
    }

    @Test
    fun resolveLocalRange_fromTopLevelStep_runsToWorkflowEnd() {
        val steps = listOf(
            step("action.1"),
            step(IF_START_ID),
            step("action.2"),
            step(IF_END_ID),
            step("action.3")
        )

        val range = WorkflowExecutionRangeResolver.resolveLocalRange(steps, startIndex = 0)

        assertEquals(0, range.startIndex)
        assertEquals(5, range.endExclusive)
    }

    @Test
    fun resolveLocalRange_fromNestedBlockStep_stopsAtInnermostScope() {
        val steps = listOf(
            step(IF_START_ID),
            step("action.outer.before"),
            step(IF_START_ID),
            step("action.inner"),
            step(IF_END_ID),
            step("action.outer.after"),
            step(IF_END_ID),
            step("action.after")
        )

        val range = WorkflowExecutionRangeResolver.resolveLocalRange(steps, startIndex = 3)

        assertEquals(3, range.startIndex)
        assertEquals(4, range.endExclusive)
    }

    private fun step(moduleId: String) = ActionStep(moduleId = moduleId, parameters = emptyMap())
}
