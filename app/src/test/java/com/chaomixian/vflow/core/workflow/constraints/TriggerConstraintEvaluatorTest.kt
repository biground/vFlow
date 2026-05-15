package com.chaomixian.vflow.core.workflow.constraints

import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerConstraintEvaluatorTest {

    @After
    fun tearDown() {
        ModuleRegistry.reset()
    }

    @Test
    fun `empty constraints allow trigger`() = runBlocking {
        val result = TriggerConstraintEvaluator.evaluate(
            context = ConstraintEvaluationContext(),
            trigger = ActionStep("vflow.trigger.location", emptyMap())
        )

        assertTrue(result.allowed)
    }

    @Test
    fun `all enabled constraints must pass`() = runBlocking {
        ModuleRegistry.register(FakeConstraintModule("test.constraint.allow", true))
        ModuleRegistry.register(FakeConstraintModule("test.constraint.block", false))
        val trigger = ActionStep("vflow.trigger.location", emptyMap()).withConstraints(
            listOf(
                ActionStep("test.constraint.allow", emptyMap()),
                ActionStep("test.constraint.block", emptyMap())
            )
        )

        val result = TriggerConstraintEvaluator.evaluate(ConstraintEvaluationContext(), trigger)

        assertFalse(result.allowed)
        assertEquals("test.constraint.block", result.blockedConstraint?.moduleId)
    }

    @Test
    fun `disabled constraints are ignored`() = runBlocking {
        ModuleRegistry.register(FakeConstraintModule("test.constraint.block", false))
        val trigger = ActionStep("vflow.trigger.location", emptyMap()).withConstraints(
            listOf(
                ActionStep(
                    moduleId = "test.constraint.block",
                    parameters = emptyMap(),
                    isDisabled = true
                )
            )
        )

        val result = TriggerConstraintEvaluator.evaluate(ConstraintEvaluationContext(), trigger)

        assertTrue(result.allowed)
    }

    @Test
    fun `unknown constraints block trigger`() = runBlocking {
        val trigger = ActionStep("vflow.trigger.location", emptyMap()).withConstraints(
            listOf(ActionStep("test.constraint.missing", emptyMap()))
        )

        val result = TriggerConstraintEvaluator.evaluate(ConstraintEvaluationContext(), trigger)

        assertFalse(result.allowed)
        assertEquals("test.constraint.missing", result.blockedConstraint?.moduleId)
        assertTrue(result.reason.orEmpty().contains("未知约束"))
    }

    @Test
    fun `cost filter skips constraints above allowed cost`() = runBlocking {
        ModuleRegistry.register(
            FakeConstraintModule(
                id = "test.constraint.high",
                allowed = false,
                constraintCost = ConstraintCost.HIGH
            )
        )
        val trigger = ActionStep("vflow.trigger.location", emptyMap()).withConstraints(
            listOf(ActionStep("test.constraint.high", emptyMap()))
        )

        val result = TriggerConstraintEvaluator.evaluateLowCost(ConstraintEvaluationContext(), trigger)

        assertTrue(result.allowed)
    }

    private class FakeConstraintModule(
        override val id: String,
        private val allowed: Boolean,
        override val constraintCost: ConstraintCost = ConstraintCost.LOW
    ) : BaseModule(), ConstraintModule {
        override val metadata = ActionMetadata(
            name = id,
            description = id,
            iconRes = 0,
            category = "约束",
            categoryId = "constraint"
        )

        override suspend fun evaluateConstraint(
            context: ConstraintEvaluationContext,
            step: ActionStep
        ): ConstraintResult {
            return if (allowed) {
                ConstraintResult.allowed()
            } else {
                ConstraintResult.blocked("blocked")
            }
        }
    }
}
