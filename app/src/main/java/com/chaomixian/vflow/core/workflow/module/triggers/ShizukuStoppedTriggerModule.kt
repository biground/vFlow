package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep

class ShizukuStoppedTriggerModule : BaseModule() {
    override val id = "vflow.trigger.shizuku_stopped"

    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_trigger_shizuku_stopped_name,
        descriptionStringRes = R.string.module_vflow_trigger_shizuku_stopped_desc,
        name = "Shizuku 已停止",
        description = "当 Shizuku 曾经可用后变为不可用时触发工作流",
        iconRes = R.drawable.shizuku,
        category = "触发器",
        categoryId = "trigger"
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "reason",
            name = "不可用原因",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_trigger_shizuku_stopped_reason_name
        ),
        OutputDefinition(
            id = "checked_at",
            name = "检测时间",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_trigger_shizuku_stopped_checked_at_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_trigger_shizuku_stopped)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate("Shizuku 不可用触发器已执行"))
        val triggerData = context.triggerData as? ShizukuStoppedTriggerData

        return ExecutionResult.Success(
            mapOf(
                "reason" to VString(triggerData?.reason ?: ShizukuStoppedTriggerData.REASON_UNKNOWN),
                "checked_at" to VNumber((triggerData?.checkedAt ?: 0L).toDouble())
            )
        )
    }
}
