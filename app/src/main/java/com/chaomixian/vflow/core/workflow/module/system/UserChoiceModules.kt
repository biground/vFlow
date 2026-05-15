package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.InputVisibility
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

object UserChoiceItems {
    private val separators = Regex("[\\n,，;；]")

    fun parse(value: VObject): List<String> {
        return when (value) {
            is VList -> value.raw.map { it.asString().trim() }.filter { it.isNotEmpty() }
            else -> value.asString()
                .split(separators)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }

    fun requireNotEmpty(value: VObject): List<String> {
        val items = parse(value)
        require(items.isNotEmpty()) { "Choice items cannot be empty" }
        return items
    }
}

class DialogAlertModule : BaseModule() {
    companion object {
        const val INPUT_ENABLE_TIMEOUT = "enableTimeout"
        const val INPUT_TIMEOUT_SECONDS = "timeoutSeconds"
        const val INPUT_TIMEOUT_DEFAULT_INDEX = "timeoutDefaultIndex"
        const val INPUT_DISMISS_ON_TOUCH_OUTSIDE = "dismissOnTouchOutside"
        private const val DEFAULT_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_TIMEOUT_DEFAULT_INDEX = 0L
    }

    override val id = "vflow.device.dialog_alert"

    override val metadata = ActionMetadata(
        name = "弹窗提醒",
        nameStringRes = R.string.module_vflow_device_dialog_alert_name,
        description = "显示带自定义按钮的弹窗，并返回用户选择的按钮。",
        descriptionStringRes = R.string.module_vflow_device_dialog_alert_desc,
        iconRes = R.drawable.rounded_preview_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Show a dialog alert with title, message, and custom buttons. Returns the selected zero-based button index.",
        workflowStepDescription = "Ask the user to choose one button in a dialog.",
        inputHints = mapOf(
            "buttons" to "Button labels as a list or as text separated by newlines, commas, or semicolons."
        ),
        requiredInputIds = setOf("title", "message", "buttons")
    )

    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("title", "标题", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_device_dialog_alert_title_name),
        InputDefinition("message", "内容", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, nameStringRes = R.string.param_vflow_device_dialog_alert_message_name),
        InputDefinition("buttons", "按钮", ParameterType.ANY, "确定", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.LIST.id), nameStringRes = R.string.param_vflow_device_dialog_alert_buttons_name),
        InputDefinition(
            INPUT_ENABLE_TIMEOUT,
            "启用超时",
            ParameterType.BOOLEAN,
            false,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false,
            inputStyle = InputStyle.SWITCH,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_dialog_alert_enable_timeout_name
        ),
        InputDefinition(
            INPUT_TIMEOUT_SECONDS,
            "超时时间(秒)",
            ParameterType.NUMBER,
            DEFAULT_TIMEOUT_SECONDS,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false,
            visibility = InputVisibility.whenTrue(INPUT_ENABLE_TIMEOUT),
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_dialog_alert_timeout_seconds_name
        ),
        InputDefinition(
            INPUT_TIMEOUT_DEFAULT_INDEX,
            "超时默认按钮索引",
            ParameterType.NUMBER,
            DEFAULT_TIMEOUT_DEFAULT_INDEX,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false,
            visibility = InputVisibility.whenTrue(INPUT_ENABLE_TIMEOUT),
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_dialog_alert_timeout_default_index_name
        ),
        InputDefinition(
            INPUT_DISMISS_ON_TOUCH_OUTSIDE,
            "点击外部可关闭",
            ParameterType.BOOLEAN,
            true,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false,
            inputStyle = InputStyle.SWITCH,
            isFolded = true,
            nameStringRes = R.string.param_vflow_device_dialog_alert_dismiss_on_touch_outside_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = choiceOutputs(
        R.string.output_vflow_device_dialog_alert_selected_index_name,
        R.string.output_vflow_device_dialog_alert_selected_text_name
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val titlePill = PillUtil.createPillFromParam(step.parameters["title"], getInputs().find { it.id == "title" })
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_dialog_alert_prefix), titlePill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val enableTimeout = step.parameters[INPUT_ENABLE_TIMEOUT] as? Boolean ?: false
        if (!enableTimeout) return ValidationResult(true)

        val timeoutSeconds = (step.parameters[INPUT_TIMEOUT_SECONDS] as? Number)?.toLong()
            ?: DEFAULT_TIMEOUT_SECONDS
        if (timeoutSeconds <= 0L) {
            return ValidationResult(
                false,
                validationText(
                    R.string.error_vflow_device_dialog_alert_timeout_invalid,
                    "超时时间必须大于 0 秒。"
                )
            )
        }

        val defaultIndex = (step.parameters[INPUT_TIMEOUT_DEFAULT_INDEX] as? Number)?.toInt()
            ?: DEFAULT_TIMEOUT_DEFAULT_INDEX.toInt()
        if (defaultIndex < 0) {
            return ValidationResult(
                false,
                timeoutIndexValidationText()
            )
        }

        val rawButtons = step.parameters["buttons"]
        if (rawButtons is String && (rawButtons.isMagicVariable() || rawButtons.isNamedVariable())) {
            return ValidationResult(true)
        }

        val buttons = UserChoiceItems.parse(VObjectFactory.from(rawButtons ?: ""))
        if (buttons.isNotEmpty() && defaultIndex !in buttons.indices) {
            return ValidationResult(
                false,
                timeoutIndexValidationText()
            )
        }

        return ValidationResult(true)
    }

    private fun timeoutIndexValidationText(): String = validationText(
        R.string.error_vflow_device_dialog_alert_timeout_index_invalid,
        "超时默认按钮索引无效。"
    )

    private fun validationText(resId: Int, fallback: String): String {
        return runCatching { appContext.getString(resId) }.getOrDefault(fallback)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_user_choice_service_missing),
                appContext.getString(R.string.error_vflow_device_user_choice_service_missing_desc)
            )
        val title = context.getVariableAsString("title", appContext.getString(R.string.module_vflow_device_dialog_alert_name))
        val message = context.getVariableAsString("message")
        val enableTimeout = context.getVariableAsBoolean(INPUT_ENABLE_TIMEOUT) ?: false
        val timeoutSeconds = context.getVariableAsLong(INPUT_TIMEOUT_SECONDS) ?: DEFAULT_TIMEOUT_SECONDS
        val timeoutDefaultIndex = (context.getVariableAsInt(INPUT_TIMEOUT_DEFAULT_INDEX)
            ?: DEFAULT_TIMEOUT_DEFAULT_INDEX.toInt())
        val dismissOnTouchOutside = context.getVariableAsBoolean(INPUT_DISMISS_ON_TOUCH_OUTSIDE) ?: true
        val buttons = try {
            UserChoiceItems.requireNotEmpty(context.getVariable("buttons"))
        } catch (e: IllegalArgumentException) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_user_choice_empty_options),
                appContext.getString(R.string.error_vflow_device_user_choice_empty_options_desc)
            )
        }

        if (enableTimeout && timeoutSeconds <= 0L) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_dialog_alert_timeout_invalid),
                appContext.getString(R.string.error_vflow_device_dialog_alert_timeout_invalid)
            )
        }

        if (enableTimeout && timeoutDefaultIndex !in buttons.indices) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_dialog_alert_timeout_index_invalid),
                appContext.getString(R.string.error_vflow_device_dialog_alert_timeout_index_invalid_desc)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_dialog_alert_waiting)))
        val selectedIndex = uiService.requestDialogAlert(
            title = title,
            message = message,
            buttons = buttons,
            timeoutSeconds = timeoutSeconds.takeIf { enableTimeout },
            timeoutDefaultIndex = timeoutDefaultIndex.takeIf { enableTimeout },
            dismissOnTouchOutside = dismissOnTouchOutside
        )
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_user_choice_cancelled),
                appContext.getString(R.string.error_vflow_device_user_choice_cancelled_desc)
            )

        return choiceSuccess(selectedIndex, buttons)
    }
}

class ListSelectionModule : BaseModule() {
    override val id = "vflow.device.list_selection"

    override val metadata = ActionMetadata(
        name = "列表选择",
        nameStringRes = R.string.module_vflow_device_list_selection_name,
        description = "显示列表让用户选择一项，并返回选中的列表项。",
        descriptionStringRes = R.string.module_vflow_device_list_selection_desc,
        iconRes = R.drawable.rounded_preview_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Show a list of items and return the selected zero-based item index.",
        workflowStepDescription = "Ask the user to choose one item from a list.",
        inputHints = mapOf(
            "items" to "Items as a list or as text separated by newlines, commas, or semicolons."
        ),
        requiredInputIds = setOf("title", "items")
    )

    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("title", "标题", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_device_list_selection_title_name),
        InputDefinition("items", "列表项", ParameterType.ANY, "", acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id, VTypeRegistry.LIST.id), nameStringRes = R.string.param_vflow_device_list_selection_items_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = choiceOutputs(
        R.string.output_vflow_device_list_selection_selected_index_name,
        R.string.output_vflow_device_list_selection_selected_text_name
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val titlePill = PillUtil.createPillFromParam(step.parameters["title"], getInputs().find { it.id == "title" })
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_list_selection_prefix), titlePill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_user_choice_service_missing),
                appContext.getString(R.string.error_vflow_device_user_choice_service_missing_desc)
            )
        val title = context.getVariableAsString("title", appContext.getString(R.string.module_vflow_device_list_selection_name))
        val items = try {
            UserChoiceItems.requireNotEmpty(context.getVariable("items"))
        } catch (e: IllegalArgumentException) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_user_choice_empty_options),
                appContext.getString(R.string.error_vflow_device_user_choice_empty_options_desc)
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_device_list_selection_waiting)))
        val selectedIndex = uiService.requestListSelection(title, items)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_user_choice_cancelled),
                appContext.getString(R.string.error_vflow_device_user_choice_cancelled_desc)
            )

        return choiceSuccess(selectedIndex, items)
    }
}

private fun choiceOutputs(indexNameRes: Int, textNameRes: Int): List<OutputDefinition> = listOf(
    OutputDefinition("selectedIndex", "选择索引", VTypeRegistry.NUMBER.id, nameStringRes = indexNameRes),
    OutputDefinition("selectedText", "选择文本", VTypeRegistry.STRING.id, nameStringRes = textNameRes)
)

private fun choiceSuccess(selectedIndex: Int, items: List<String>): ExecutionResult {
    return ExecutionResult.Success(
        mapOf(
            "selectedIndex" to VNumber(selectedIndex),
            "selectedText" to VString(items.getOrElse(selectedIndex) { "" })
        )
    )
}
