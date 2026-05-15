package com.chaomixian.vflow.core.workflow.constraints

import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep

object TriggerConstraintEvaluator {
    suspend fun evaluate(
        context: ConstraintEvaluationContext,
        trigger: ActionStep,
        allowedCosts: Set<ConstraintCost> = ConstraintCost.entries.toSet()
    ): TriggerConstraintEvaluationResult {
        trigger.constraints.forEach { constraintStep ->
            if (constraintStep.isDisabled) return@forEach

            val module = ModuleRegistry.getModule(constraintStep.moduleId) as? ConstraintModule
                ?: return TriggerConstraintEvaluationResult.blocked(
                    constraintStep,
                    "未知约束：${constraintStep.moduleId}"
                )

            if (module.constraintCost !in allowedCosts) {
                return@forEach
            }

            val result = runCatching {
                module.evaluateConstraint(context, constraintStep)
            }.getOrElse { error ->
                ConstraintResult.blocked("约束评估失败：${error.message ?: error::class.java.simpleName}")
            }

            if (!result.allowed) {
                return TriggerConstraintEvaluationResult.blocked(
                    constraintStep,
                    result.reason ?: "约束不满足"
                )
            }
        }

        return TriggerConstraintEvaluationResult.allowed()
    }

    suspend fun evaluateLowCost(
        context: ConstraintEvaluationContext,
        trigger: ActionStep
    ): TriggerConstraintEvaluationResult {
        return evaluate(
            context = context,
            trigger = trigger,
            allowedCosts = setOf(ConstraintCost.LOW, ConstraintCost.MEDIUM)
        )
    }
}
