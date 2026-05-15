package com.chaomixian.vflow.core.workflow.constraints

import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.workflow.model.ActionStep

interface ConstraintModule : ActionModule {
    val constraintCost: ConstraintCost
        get() = ConstraintCost.LOW

    suspend fun evaluateConstraint(
        context: ConstraintEvaluationContext,
        step: ActionStep
    ): ConstraintResult

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        return ExecutionResult.Failure(
            errorTitle = "约束不能作为动作执行",
            errorMessage = "约束模块只能挂载在触发器上，用于判断是否允许触发。"
        )
    }
}
