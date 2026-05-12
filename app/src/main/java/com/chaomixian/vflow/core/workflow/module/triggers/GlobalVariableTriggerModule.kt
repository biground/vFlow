package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class GlobalVariableTriggerModule : BaseModule() {

    override val id = "vflow.trigger.global_variable"

    override val metadata = ActionMetadata(
        name = "全局变量变更",
        nameStringRes = R.string.module_vflow_trigger_global_variable_name,
        description = "当指定全局变量发生变化时触发工作流。",
        descriptionStringRes = R.string.module_vflow_trigger_global_variable_desc,
        iconRes = R.drawable.rounded_variable_insert_24,
        category = "触发器",
        categoryId = "trigger"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "variable_name",
            name = "全局变量名",
            nameStringRes = R.string.param_vflow_trigger_global_variable_variable_name_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "variable_name",
            name = "变量名",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_trigger_global_variable_variable_name_name
        ),
        OutputDefinition(
            id = "old_value",
            name = "旧值",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_trigger_global_variable_old_value_name
        ),
        OutputDefinition(
            id = "new_value",
            name = "新值",
            typeName = VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_trigger_global_variable_new_value_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val variableName = (step.parameters["variable_name"] as? String).orEmpty()
        val namePill = PillUtil.Pill(variableName.ifBlank { "-" }, "variable_name")
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_trigger_global_variable_prefix),
            " ",
            namePill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_trigger_global_variable_triggered)))
        val triggerData = context.triggerData as? VDictionary
        return ExecutionResult.Success(
            mapOf(
                "variable_name" to ((triggerData?.raw?.get("variable_name") as? VString) ?: VString("")),
                "old_value" to ((triggerData?.raw?.get("old_value") as? VString) ?: VString("")),
                "new_value" to ((triggerData?.raw?.get("new_value") as? VString) ?: VString(""))
            )
        )
    }
}
