package com.chaomixian.vflow.core.workflow.constraints

import com.chaomixian.vflow.core.workflow.model.ActionStep

enum class ConstraintCost {
    LOW,
    MEDIUM,
    HIGH
}

data class ConstraintResult(
    val allowed: Boolean,
    val reason: String? = null
) {
    companion object {
        fun allowed(): ConstraintResult = ConstraintResult(allowed = true)

        fun blocked(reason: String): ConstraintResult =
            ConstraintResult(allowed = false, reason = reason)
    }
}

data class TriggerConstraintEvaluationResult(
    val allowed: Boolean,
    val blockedConstraint: ActionStep? = null,
    val reason: String? = null
) {
    companion object {
        fun allowed(): TriggerConstraintEvaluationResult =
            TriggerConstraintEvaluationResult(allowed = true)

        fun blocked(step: ActionStep, reason: String): TriggerConstraintEvaluationResult =
            TriggerConstraintEvaluationResult(
                allowed = false,
                blockedConstraint = step,
                reason = reason
            )
    }
}
