package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.media.AudioManager
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.InputStyle
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.module.normalizeEnumValue
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class VibrationModeModule(
    private val controllerFactory: (Context) -> VibrationModeController = { context ->
        AndroidVibrationModeController(context)
    }
) : BaseModule() {

    companion object {
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_ON = "on"
        const val ACTION_OFF = "off"

        private val ACTION_LEGACY_MAP = mapOf(
            "切换" to ACTION_TOGGLE,
            "开启" to ACTION_ON,
            "打开" to ACTION_ON,
            "关闭" to ACTION_OFF
        )

        fun applyAction(action: String, controller: VibrationModeController): VibrationModeResult {
            val normalizedAction = when (action) {
                ACTION_ON, ACTION_OFF, ACTION_TOGGLE -> action
                else -> throw IllegalArgumentException("Unsupported vibration mode action: $action")
            }
            val targetMode = when (normalizedAction) {
                ACTION_ON -> AudioManager.RINGER_MODE_VIBRATE
                ACTION_OFF -> AudioManager.RINGER_MODE_NORMAL
                ACTION_TOGGLE -> {
                    if (controller.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                        AudioManager.RINGER_MODE_NORMAL
                    } else {
                        AudioManager.RINGER_MODE_VIBRATE
                    }
                }
                else -> AudioManager.RINGER_MODE_VIBRATE
            }
            controller.ringerMode = targetMode
            val isVibrationMode = targetMode == AudioManager.RINGER_MODE_VIBRATE
            val resolvedAction = if (isVibrationMode) ACTION_ON else ACTION_OFF
            return VibrationModeResult(isVibrationMode, resolvedAction)
        }
    }

    override val id = "vflow.system.vibration_mode"

    override val metadata = ActionMetadata(
        name = "振动模式",
        nameStringRes = R.string.module_vflow_system_vibration_mode_name,
        description = "开启、关闭或切换系统振动模式。",
        descriptionStringRes = R.string.module_vflow_system_vibration_mode_desc,
        iconRes = R.drawable.rounded_mobile_vibrate_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Turn Android ringer vibration mode on, off, or toggle it.",
        workflowStepDescription = "Change Android ringer vibration mode.",
        inputHints = mapOf(
            "action" to "Use canonical values: toggle, on, or off."
        ),
        requiredInputIds = setOf("action")
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "action",
            name = "操作",
            nameStringRes = R.string.param_vflow_system_vibration_mode_action_name,
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_TOGGLE,
            options = listOf(ACTION_TOGGLE, ACTION_ON, ACTION_OFF),
            optionsStringRes = listOf(
                R.string.option_vflow_system_vibration_mode_toggle,
                R.string.option_vflow_system_vibration_mode_on,
                R.string.option_vflow_system_vibration_mode_off
            ),
            legacyValueMap = ACTION_LEGACY_MAP,
            inputStyle = InputStyle.CHIP_GROUP
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_vibration_mode_success_name
        ),
        OutputDefinition(
            id = "enabled",
            name = "振动模式已开启",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_vibration_mode_enabled_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val action = getInputs().normalizeEnumValue(
            "action",
            step.parameters["action"] as? String,
            ACTION_TOGGLE
        ) ?: ACTION_TOGGLE
        val actionPill = PillUtil.Pill(getActionDisplayName(context, action), "action", isModuleOption = true)
        return PillUtil.buildSpannable(context, "${metadata.getLocalizedName(context)}: ", actionPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val action = getInputs().normalizeEnumValue(
            "action",
            context.getVariable("action").asString(),
            ACTION_TOGGLE
        ) ?: ACTION_TOGGLE
        val actionName = getActionDisplayName(context.applicationContext, action)
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_vibration_mode_setting, actionName)))

        return try {
            val result = applyAction(action, controllerFactory(context.applicationContext))
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_system_vibration_mode_completed), 100))
            ExecutionResult.Success(
                mapOf(
                    "success" to VBoolean(true),
                    "enabled" to VBoolean(result.isVibrationMode)
                )
            )
        } catch (e: SecurityException) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_vibration_mode_permission_denied),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_system_vibration_mode_unknown)
            )
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_system_vibration_mode_failed),
                e.localizedMessage ?: appContext.getString(R.string.error_vflow_system_vibration_mode_unknown)
            )
        }
    }

    private fun getActionDisplayName(context: Context, action: String): String {
        return when (action) {
            ACTION_ON -> context.getString(R.string.option_vflow_system_vibration_mode_on)
            ACTION_OFF -> context.getString(R.string.option_vflow_system_vibration_mode_off)
            else -> context.getString(R.string.option_vflow_system_vibration_mode_toggle)
        }
    }
}

interface VibrationModeController {
    var ringerMode: Int
}

data class VibrationModeResult(
    val isVibrationMode: Boolean,
    val resolvedAction: String
)

private class AndroidVibrationModeController(context: Context) : VibrationModeController {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override var ringerMode: Int
        get() = audioManager.ringerMode
        set(value) {
            audioManager.ringerMode = value
        }
}
