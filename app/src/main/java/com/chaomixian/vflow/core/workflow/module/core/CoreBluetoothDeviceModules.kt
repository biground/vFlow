package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val INPUT_DEVICE = "device"
private const val INPUT_DISCONNECT_OTHERS = "disconnect_others"

private fun bluetoothDeviceInputs(): List<InputDefinition> = listOf(
    InputDefinition(
        id = INPUT_DEVICE,
        name = "设备名称或地址",
        staticType = ParameterType.STRING,
        defaultValue = "",
        acceptsMagicVariable = true,
        nameStringRes = R.string.param_vflow_core_bluetooth_device_identifier_name,
        hintStringRes = R.string.hint_vflow_core_bluetooth_device_identifier
    )
)

private fun bluetoothDeviceOutputs(includeSuccess: Boolean): List<OutputDefinition> {
    val outputs = mutableListOf<OutputDefinition>()
    if (includeSuccess) {
        outputs.add(OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_bluetooth_success_name))
    }
    outputs.add(OutputDefinition("connected", "是否已连接", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_core_bluetooth_device_connected_name))
    outputs.add(OutputDefinition("device_name", "设备名称", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_core_bluetooth_device_name_name))
    outputs.add(OutputDefinition("device_address", "设备地址", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_core_bluetooth_device_address_name))
    outputs.add(OutputDefinition("profiles", "连接配置", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_core_bluetooth_device_profiles_name))
    outputs.add(OutputDefinition("error", "错误信息", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_core_bluetooth_device_error_name))
    return outputs
}

private fun bluetoothResultOutputs(result: VFlowCoreBridge.BluetoothDeviceResult): Map<String, Any?> {
    return mapOf(
        "connected" to VBoolean(result.connected),
        "device_name" to VString(result.deviceName),
        "device_address" to VString(result.deviceAddress),
        "profiles" to VString(result.profiles.joinToString(",")),
        "error" to VString(result.error.orEmpty())
    )
}

class CoreBluetoothDeviceConnectedModule : BaseModule() {

    override val id = "vflow.core.bluetooth_device_connected"
    override val metadata = ActionMetadata(
        name = "检查蓝牙设备连接",
        nameStringRes = R.string.module_vflow_core_bluetooth_device_connected_name,
        description = "使用 vFlow Core 检查指定已配对蓝牙设备当前是否连接。",
        descriptionStringRes = R.string.module_vflow_core_bluetooth_device_connected_desc,
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "Core (Beta)",
        categoryId = "core"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.READ_ONLY,
        directToolDescription = "Check whether a paired Bluetooth device is currently connected through vFlow Core.",
        workflowStepDescription = "Check whether a paired Bluetooth device is connected through vFlow Core.",
        requiredInputIds = setOf(INPUT_DEVICE)
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> = listOf(PermissionManager.CORE)

    override fun getInputs(): List<InputDefinition> = bluetoothDeviceInputs()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = bluetoothDeviceOutputs(includeSuccess = false)

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val devicePill = PillUtil.createPillFromParam(
            step.parameters[INPUT_DEVICE],
            getInputs().first { it.id == INPUT_DEVICE }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_bluetooth_device_connected), devicePill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val device = step.parameters[INPUT_DEVICE]?.toString().orEmpty()
        return if (device.isBlank()) {
            ValidationResult(false, "设备名称或地址不能为空")
        } else {
            ValidationResult(true)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(appContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_not_connected),
                appContext.getString(R.string.error_vflow_core_service_not_running)
            )
        }

        val device = context.getVariableAsString(INPUT_DEVICE).trim()
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_bluetooth_device_checking, device)))
        val result = withContext(Dispatchers.IO) {
            VFlowCoreBridge.isBluetoothDeviceConnected(device)
        }
        return ExecutionResult.Success(bluetoothResultOutputs(result))
    }
}

class CoreBluetoothSwitchDeviceModule : BaseModule() {

    override val id = "vflow.core.bluetooth_switch_device"
    override val metadata = ActionMetadata(
        name = "切换蓝牙设备",
        nameStringRes = R.string.module_vflow_core_bluetooth_switch_device_name,
        description = "使用 vFlow Core 尝试主动连接并切换到指定已配对蓝牙设备。",
        descriptionStringRes = R.string.module_vflow_core_bluetooth_switch_device_desc,
        iconRes = R.drawable.rounded_bluetooth_24,
        category = "Core (Beta)",
        categoryId = "core"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Try to connect and switch to a paired Bluetooth device through vFlow Core.",
        workflowStepDescription = "Try to switch to a paired Bluetooth device through vFlow Core.",
        inputHints = mapOf(
            INPUT_DEVICE to "Use the paired Bluetooth device name or MAC address.",
            INPUT_DISCONNECT_OTHERS to "True disconnects other connected Bluetooth audio profiles before connecting the target."
        ),
        requiredInputIds = setOf(INPUT_DEVICE)
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> = listOf(PermissionManager.CORE)

    override fun getInputs(): List<InputDefinition> = bluetoothDeviceInputs() + InputDefinition(
        id = INPUT_DISCONNECT_OTHERS,
        name = "断开其他设备",
        staticType = ParameterType.BOOLEAN,
        defaultValue = true,
        acceptsMagicVariable = true,
        nameStringRes = R.string.param_vflow_core_bluetooth_disconnect_others_name
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = bluetoothDeviceOutputs(includeSuccess = true)

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val devicePill = PillUtil.createPillFromParam(
            step.parameters[INPUT_DEVICE],
            getInputs().first { it.id == INPUT_DEVICE }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_core_bluetooth_switch_device), devicePill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val device = step.parameters[INPUT_DEVICE]?.toString().orEmpty()
        return if (device.isBlank()) {
            ValidationResult(false, "设备名称或地址不能为空")
        } else {
            ValidationResult(true)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext
        val connected = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connect(appContext)
        }
        if (!connected) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_core_not_connected),
                appContext.getString(R.string.error_vflow_core_service_not_running)
            )
        }

        val device = context.getVariableAsString(INPUT_DEVICE).trim()
        val disconnectOthers = context.getVariableAsBoolean(INPUT_DISCONNECT_OTHERS) ?: true
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_core_bluetooth_device_switching, device)))
        val result = withContext(Dispatchers.IO) {
            VFlowCoreBridge.connectBluetoothDevice(device, disconnectOthers)
        }
        val outputs = bluetoothResultOutputs(result) + mapOf("success" to VBoolean(result.success))
        return ExecutionResult.Success(outputs)
    }
}
