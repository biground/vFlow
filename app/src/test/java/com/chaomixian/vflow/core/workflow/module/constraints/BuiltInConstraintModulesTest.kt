package com.chaomixian.vflow.core.workflow.module.constraints

import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.workflow.constraints.ConstraintEvaluationContext
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BuiltInConstraintModulesTest {

    @Test
    fun `time range constraint allows time inside normal range`() = runBlocking {
        val module = TimeRangeConstraintModule()
        val step = ActionStep(
            moduleId = module.id,
            parameters = mapOf(
                "mode" to TimeRangeConstraintModule.MODE_INSIDE,
                "start_time" to "08:00",
                "end_time" to "12:00"
            )
        )
        val context = ConstraintEvaluationContext(
            now = LocalDateTime.of(LocalDate.of(2026, 5, 15), LocalTime.of(9, 30))
        )

        assertTrue(module.evaluateConstraint(context, step).allowed)
    }

    @Test
    fun `time range constraint supports ranges crossing midnight`() = runBlocking {
        val module = TimeRangeConstraintModule()
        val step = ActionStep(
            moduleId = module.id,
            parameters = mapOf(
                "mode" to TimeRangeConstraintModule.MODE_INSIDE,
                "start_time" to "22:00",
                "end_time" to "06:00"
            )
        )
        val context = ConstraintEvaluationContext(
            now = LocalDateTime.of(LocalDate.of(2026, 5, 15), LocalTime.of(23, 0))
        )

        assertTrue(module.evaluateConstraint(context, step).allowed)
    }

    @Test
    fun `weekday constraint blocks unselected day`() = runBlocking {
        val module = WeekdayConstraintModule()
        val step = ActionStep(
            moduleId = module.id,
            parameters = mapOf(
                "mode" to WeekdayConstraintModule.MODE_IN,
                "days" to listOf(1, 2, 3)
            )
        )
        val context = ConstraintEvaluationContext(
            now = LocalDateTime.of(LocalDate.of(2026, 5, 15), LocalTime.NOON)
        )

        assertFalse(module.evaluateConstraint(context, step).allowed)
        assertTrue(context.now.dayOfWeek == DayOfWeek.FRIDAY)
    }

    @Test
    fun `global variable constraint uses condition evaluator operators`() = runBlocking {
        val module = GlobalVariableConstraintModule()
        val step = ActionStep(
            moduleId = module.id,
            parameters = mapOf(
                "variable_name" to "mode",
                "operator" to "not_equals",
                "value" to "工作"
            )
        )
        val context = ConstraintEvaluationContext(
            globalVariables = mapOf("mode" to VObjectFactory.from("休息"))
        )

        assertTrue(module.evaluateConstraint(context, step).allowed)
    }
}
