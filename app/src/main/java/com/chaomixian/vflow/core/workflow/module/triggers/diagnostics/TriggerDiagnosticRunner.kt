package com.chaomixian.vflow.core.workflow.module.triggers.diagnostics

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.normalizeEnumValueOrNull
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.BatteryTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.BluetoothTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.LocationTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.PowerTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.ScreenTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.WifiTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.handlers.WifiTriggerHandler
import com.chaomixian.vflow.permissions.PermissionManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TriggerDiagnosticRunner {

    fun supportsCompleteDiagnostic(moduleId: String): Boolean {
        return moduleId in completeDiagnosticModuleIds
    }

    suspend fun diagnose(
        context: Context,
        module: ActionModule,
        step: ActionStep,
        allSteps: List<ActionStep> = emptyList()
    ): TriggerDiagnosticResult {
        val validation = module.validate(step, allSteps)
        if (!validation.isValid) {
            return TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.INVALID_CONFIG,
                title = "配置无效",
                message = validation.errorMessage ?: "触发器配置不完整或不合法。"
            )
        }

        val missingPermissions = module.getRequiredPermissions(step)
            .filter { !PermissionManager.isGranted(context, it) }
        if (missingPermissions.isNotEmpty()) {
            val permissionNames = missingPermissions.map { it.getLocalizedName(context) }
            return TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.MISSING_PERMISSION,
                title = "缺少权限",
                message = "需要先授予以下权限：${permissionNames.joinToString("、")}",
                missingPermissionNames = permissionNames
            )
        }

        return when (step.moduleId) {
            LocationTriggerModule().id -> diagnoseLocationRuntime(context, step)
            WifiTriggerModule().id -> diagnoseWifiRuntime(context, step)
            BluetoothTriggerModule().id -> diagnoseBluetoothRuntime(context, step)
            BatteryTriggerModule().id -> diagnoseBatteryRuntime(context, step)
            ScreenTriggerModule().id -> diagnoseScreenRuntime(context, step)
            PowerTriggerModule().id -> diagnosePowerRuntime(context, step)
            else -> unsupportedDiagnostic()
        }
    }

    fun diagnoseWifiMatch(
        step: ActionStep,
        isWifiEnabled: Boolean,
        connectedSsid: String?
    ): TriggerDiagnosticResult {
        val inputs = WifiTriggerModule().getInputs()
        val triggerType = inputs.normalizeEnumValueOrNull(
            "trigger_type",
            step.parameters["trigger_type"] as? String
        ) ?: WifiTriggerModule.TRIGGER_TYPE_CONNECTION

        return when (triggerType) {
            WifiTriggerModule.TRIGGER_TYPE_STATE -> {
                val event = inputs.normalizeEnumValueOrNull(
                    "state_event",
                    step.parameters["state_event"] as? String
                ) ?: WifiTriggerModule.STATE_EVENT_ON
                val matched = when (event) {
                    WifiTriggerModule.STATE_EVENT_ON -> isWifiEnabled
                    WifiTriggerModule.STATE_EVENT_OFF -> !isWifiEnabled
                    else -> return invalid("Wi-Fi 状态事件无效", "Wi-Fi 状态事件必须是开启或关闭。")
                }
                stateResult(
                    matched = matched,
                    successTitle = "当前状态可命中",
                    successMessage = if (isWifiEnabled) {
                        "当前 Wi-Fi 已开启，符合触发条件。真实触发仍依赖下一次系统状态事件。"
                    } else {
                        "当前 Wi-Fi 已关闭，符合触发条件。真实触发仍依赖下一次系统状态事件。"
                    },
                    notMatchedMessage = if (isWifiEnabled) {
                        "当前 Wi-Fi 已开启，不符合关闭状态触发条件。"
                    } else {
                        "当前 Wi-Fi 已关闭，不符合开启状态触发条件。"
                    }
                )
            }
            WifiTriggerModule.TRIGGER_TYPE_CONNECTION -> {
                val event = inputs.normalizeEnumValueOrNull(
                    "connection_event",
                    step.parameters["connection_event"] as? String
                ) ?: WifiTriggerModule.CONNECTION_EVENT_CONNECTED
                val target = (step.parameters["network_target"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: WifiTriggerHandler.ANY_WIFI_TARGET
                val isAny = target == WifiTriggerHandler.ANY_WIFI_TARGET
                val connected = isWifiEnabled && !connectedSsid.isNullOrBlank()
                val targetMatches = isAny || connectedSsid.equals(target, ignoreCase = true)
                val matched = when (event) {
                    WifiTriggerModule.CONNECTION_EVENT_CONNECTED -> connected && targetMatches
                    WifiTriggerModule.CONNECTION_EVENT_DISCONNECTED -> !connected || !targetMatches
                    else -> return invalid("Wi-Fi 连接事件无效", "Wi-Fi 连接事件必须是连接或断开。")
                }
                val currentText = if (connected) {
                    "当前连接到 ${connectedSsid}。"
                } else {
                    "当前未连接 Wi-Fi。"
                }
                val targetText = if (isAny) "任意 Wi-Fi" else target
                stateResult(
                    matched = matched,
                    successTitle = "当前状态可命中",
                    successMessage = "$currentText 配置目标为 $targetText，当前状态符合触发条件。真实触发仍依赖下一次连接变化事件。",
                    notMatchedMessage = "$currentText 配置目标为 $targetText，当前状态不符合触发条件。"
                )
            }
            else -> invalid("Wi-Fi 触发类型无效", "Wi-Fi 触发类型必须是连接变化或开关状态。")
        }
    }

    fun diagnoseBluetoothMatch(
        step: ActionStep,
        isEnabled: Boolean,
        connectedDevices: List<BluetoothDeviceSnapshot>?
    ): TriggerDiagnosticResult {
        val inputs = BluetoothTriggerModule().getInputs()
        val triggerType = inputs.normalizeEnumValueOrNull(
            "trigger_type",
            step.parameters["trigger_type"] as? String
        ) ?: BluetoothTriggerModule.TRIGGER_TYPE_STATE

        return when (triggerType) {
            BluetoothTriggerModule.TRIGGER_TYPE_STATE -> {
                val event = inputs.normalizeEnumValueOrNull(
                    "state_event",
                    step.parameters["state_event"] as? String
                ) ?: BluetoothTriggerModule.STATE_EVENT_ON
                val matched = when (event) {
                    BluetoothTriggerModule.STATE_EVENT_ON -> isEnabled
                    BluetoothTriggerModule.STATE_EVENT_OFF -> !isEnabled
                    else -> return invalid("蓝牙状态事件无效", "蓝牙状态事件必须是开启或关闭。")
                }
                stateResult(
                    matched = matched,
                    successTitle = "当前状态可命中",
                    successMessage = if (isEnabled) {
                        "当前蓝牙已开启，符合触发条件。真实触发仍依赖下一次系统状态事件。"
                    } else {
                        "当前蓝牙已关闭，符合触发条件。真实触发仍依赖下一次系统状态事件。"
                    },
                    notMatchedMessage = if (isEnabled) {
                        "当前蓝牙已开启，不符合关闭状态触发条件。"
                    } else {
                        "当前蓝牙已关闭，不符合开启状态触发条件。"
                    }
                )
            }
            BluetoothTriggerModule.TRIGGER_TYPE_DEVICE -> {
                if (connectedDevices == null) {
                    return TriggerDiagnosticResult(
                        status = TriggerDiagnosticStatus.UNKNOWN,
                        title = "无法读取蓝牙连接设备",
                        message = "当前只能确认蓝牙权限和配置正常，但系统未提供可靠的已连接设备列表，无法判断设备连接条件是否命中。"
                    )
                }
                val event = inputs.normalizeEnumValueOrNull(
                    "device_event",
                    step.parameters["device_event"] as? String
                ) ?: BluetoothTriggerModule.DEVICE_EVENT_CONNECTED
                val target = (step.parameters["device_address"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: BluetoothTriggerModule.ANY_DEVICE_ADDRESS
                val isAny = target == BluetoothTriggerModule.ANY_DEVICE_ADDRESS
                val targetConnected = connectedDevices.any { device ->
                    isAny || device.address.equals(target, ignoreCase = true)
                }
                val matched = when (event) {
                    BluetoothTriggerModule.DEVICE_EVENT_CONNECTED -> targetConnected
                    BluetoothTriggerModule.DEVICE_EVENT_DISCONNECTED -> !targetConnected
                    else -> return invalid("蓝牙设备事件无效", "蓝牙设备事件必须是连接或断开。")
                }
                val deviceText = if (connectedDevices.isEmpty()) {
                    "当前没有已连接蓝牙设备。"
                } else {
                    "当前已连接 ${connectedDevices.size} 个蓝牙设备。"
                }
                stateResult(
                    matched = matched,
                    successTitle = "当前状态可命中",
                    successMessage = "$deviceText 当前状态符合触发条件。真实触发仍依赖下一次设备连接变化事件。",
                    notMatchedMessage = "$deviceText 当前状态不符合触发条件。"
                )
            }
            else -> invalid("蓝牙触发类型无效", "蓝牙触发类型必须是状态或设备连接。")
        }
    }

    fun diagnoseBatteryMatch(step: ActionStep, currentLevel: Int): TriggerDiagnosticResult {
        val condition = BatteryTriggerModule().getInputs().normalizeEnumValueOrNull(
            "above_or_below",
            step.parameters["above_or_below"] as? String
        ) ?: BatteryTriggerModule.VALUE_BELOW
        val threshold = numberParameter(step, "level")?.toInt()
            ?: return invalid("电量阈值无效", "电量阈值必须是有效数字。")
        if (threshold !in 0..100) {
            return invalid("电量阈值无效", "电量阈值必须在 0 到 100 之间。")
        }
        val matched = when (condition) {
            BatteryTriggerModule.VALUE_BELOW -> currentLevel < threshold
            BatteryTriggerModule.VALUE_ABOVE -> currentLevel > threshold
            else -> return invalid("电量条件无效", "电量条件必须是高于或低于。")
        }
        val conditionText = if (condition == BatteryTriggerModule.VALUE_BELOW) "低于" else "高于"
        return stateResult(
            matched = matched,
            successTitle = "当前状态可命中",
            successMessage = "当前电量 ${currentLevel}%，已${conditionText} ${threshold}%。真实触发通常依赖电量变化并跨过阈值。",
            notMatchedMessage = "当前电量 ${currentLevel}%，未${conditionText} ${threshold}%。"
        )
    }

    fun diagnosePowerMatch(step: ActionStep, isConnected: Boolean): TriggerDiagnosticResult {
        val desired = PowerTriggerModule().getInputs().normalizeEnumValueOrNull(
            "power_state",
            step.parameters["power_state"] as? String
        ) ?: PowerTriggerModule.VALUE_CONNECTED
        val matched = when (desired) {
            PowerTriggerModule.VALUE_CONNECTED -> isConnected
            PowerTriggerModule.VALUE_DISCONNECTED -> !isConnected
            else -> return invalid("电源状态无效", "电源状态必须是已连接或已断开。")
        }
        val currentText = if (isConnected) "当前电源已连接" else "当前电源已断开"
        return stateResult(
            matched = matched,
            successTitle = "当前状态可命中",
            successMessage = "$currentText，符合触发条件。真实触发仍依赖下一次电源连接变化事件。",
            notMatchedMessage = "$currentText，不符合触发条件。"
        )
    }

    fun diagnoseScreenMatch(
        step: ActionStep,
        isInteractive: Boolean,
        isUnlocked: Boolean
    ): TriggerDiagnosticResult {
        val event = ScreenTriggerModule().getInputs().normalizeEnumValueOrNull(
            "screen_event",
            step.parameters["screen_event"] as? String
        ) ?: ScreenTriggerModule.VALUE_SCREEN_ON
        val matched = when (event) {
            ScreenTriggerModule.VALUE_SCREEN_ON -> isInteractive
            ScreenTriggerModule.VALUE_SCREEN_OFF -> !isInteractive
            ScreenTriggerModule.VALUE_UNLOCKED -> isInteractive && isUnlocked
            else -> return invalid("屏幕事件无效", "屏幕事件必须是亮屏、熄屏或解锁。")
        }
        val currentText = when {
            isInteractive && isUnlocked -> "当前屏幕已亮起，设备已解锁"
            isInteractive -> "当前屏幕已亮起，设备仍锁定"
            else -> "当前屏幕已熄灭"
        }
        val eventHint = if (event == ScreenTriggerModule.VALUE_UNLOCKED) {
            "解锁事件需要下一次系统解锁广播。"
        } else {
            "真实触发仍依赖下一次屏幕事件。"
        }
        return stateResult(
            matched = matched,
            successTitle = "当前状态可命中",
            successMessage = "$currentText，符合触发条件。$eventHint",
            notMatchedMessage = "$currentText，不符合触发条件。"
        )
    }

    fun diagnoseLocationConfiguration(step: ActionStep): TriggerDiagnosticResult {
        val config = readLocationConfig(step)
        return config.error ?: TriggerDiagnosticResult(
            status = TriggerDiagnosticStatus.SUCCESS,
            title = "配置有效",
            message = "位置触发器参数格式正确。"
        )
    }

    fun diagnoseLocationMatch(
        step: ActionStep,
        currentLatitude: Double,
        currentLongitude: Double,
        sourceName: String = "高德定位"
    ): TriggerDiagnosticResult {
        val config = readLocationConfig(step)
        config.error?.let { return it }
        val event = config.event
        val locationLabel = if (sourceName.isBlank()) {
            "当前位置"
        } else {
            "${sourceName}返回的位置"
        }
        val distance = calculateDistanceMeters(
            currentLatitude,
            currentLongitude,
            config.latitude,
            config.longitude
        )
        val distanceText = distance.toInt()

        return when (event) {
            LocationTriggerModule.EVENT_ENTER -> {
                if (distance <= config.radius) {
                    TriggerDiagnosticResult(
                        status = TriggerDiagnosticStatus.SUCCESS,
                        title = "当前可命中",
                        message = "${locationLabel}距离围栏中心约 ${distanceText} 米，已在 ${config.radius.toInt()} 米范围内。"
                    )
                } else {
                    TriggerDiagnosticResult(
                        status = TriggerDiagnosticStatus.NOT_MATCHED,
                        title = "当前未命中",
                        message = "${locationLabel}距离围栏中心约 ${distanceText} 米，尚未进入 ${config.radius.toInt()} 米范围。"
                    )
                }
            }
            LocationTriggerModule.EVENT_EXIT -> {
                if (distance <= config.radius) {
                    TriggerDiagnosticResult(
                        status = TriggerDiagnosticStatus.NOT_MATCHED,
                        title = "尚未离开",
                        message = "${locationLabel}仍在围栏内。离开触发器需要先记录进入状态，再移动到范围外。"
                    )
                } else {
                    TriggerDiagnosticResult(
                        status = TriggerDiagnosticStatus.UNKNOWN,
                        title = "需要历史状态",
                        message = "${locationLabel}在围栏外，但离开触发依赖此前是否进入过该围栏的历史状态，无法仅凭当前位置判断。"
                    )
                }
            }
            else -> TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.INVALID_CONFIG,
                title = "事件无效",
                message = "位置触发器事件必须是进入或离开。"
            )
        }
    }

    private suspend fun diagnoseLocationRuntime(context: Context, step: ActionStep): TriggerDiagnosticResult {
        diagnoseLocationConfiguration(step).let {
            if (it.status != TriggerDiagnosticStatus.SUCCESS) return it
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.MISSING_PERMISSION,
                title = "缺少位置权限",
                message = "需要先授予精确定位权限，才能读取当前位置进行诊断。",
                missingPermissionNames = listOf("精确定位")
            )
        }

        val location = AmapDiagnosticLocationProvider.getCurrentLocation(context)
            ?: return TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.UNKNOWN,
                title = "无法获取当前位置",
                message = "高德定位暂时没有返回有效坐标。请确认定位服务和网络已开启，并检查设置里的高德地图 API Key。"
            )

        return diagnoseLocationMatch(
            step = step,
            currentLatitude = location.latitude,
            currentLongitude = location.longitude,
            sourceName = location.sourceName
        )
    }

    private fun diagnoseWifiRuntime(context: Context, step: ActionStep): TriggerDiagnosticResult {
        return runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = wifiManager.connectionInfo?.ssid?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            diagnoseWifiMatch(
                step = step,
                isWifiEnabled = wifiManager.isWifiEnabled,
                connectedSsid = ssid
            )
        }.getOrElse {
            unknown("无法读取 Wi-Fi 状态", "系统没有返回可靠的 Wi-Fi 状态，无法判断当前是否命中。")
        }
    }

    @SuppressLint("MissingPermission")
    private fun diagnoseBluetoothRuntime(context: Context, step: ActionStep): TriggerDiagnosticResult {
        return runCatching {
            val manager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            val enabled = adapter?.isEnabled == true
            diagnoseBluetoothMatch(
                step = step,
                isEnabled = enabled,
                connectedDevices = null
            )
        }.getOrElse {
            unknown("无法读取蓝牙状态", "系统没有返回可靠的蓝牙状态，无法判断当前是否命中。")
        }
    }

    private fun diagnoseBatteryRuntime(context: Context, step: ActionStep): TriggerDiagnosticResult {
        val intent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return unknown("无法读取电量", "系统没有返回当前电量，无法判断当前是否命中。")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return unknown("无法读取电量", "系统返回的电量数据无效，无法判断当前是否命中。")
        }
        return diagnoseBatteryMatch(step, (level * 100 / scale.toFloat()).toInt())
    }

    private fun diagnosePowerRuntime(context: Context, step: ActionStep): TriggerDiagnosticResult {
        val intent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return unknown("无法读取电源状态", "系统没有返回当前电源状态，无法判断当前是否命中。")
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return diagnosePowerMatch(step, isConnected = plugged != 0)
    }

    private fun diagnoseScreenRuntime(context: Context, step: ActionStep): TriggerDiagnosticResult {
        return runCatching {
            val appContext = context.applicationContext
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            diagnoseScreenMatch(
                step = step,
                isInteractive = powerManager.isInteractive,
                isUnlocked = !keyguardManager.isDeviceLocked
            )
        }.getOrElse {
            unknown("无法读取屏幕状态", "系统没有返回可靠的屏幕或锁定状态，无法判断当前是否命中。")
        }
    }

    private fun readLocationConfig(step: ActionStep): LocationConfig {
        val inputs = LocationTriggerModule().getInputs()
        val event = inputs.normalizeEnumValueOrNull("event", step.parameters["event"] as? String)
            ?: return LocationConfig(
                error = TriggerDiagnosticResult(
                    status = TriggerDiagnosticStatus.INVALID_CONFIG,
                    title = "事件无效",
                    message = "位置触发器事件必须是进入或离开。"
                )
            )
        val latitude = numberParameter(step, "latitude")
            ?: return invalidNumber("纬度")
        val longitude = numberParameter(step, "longitude")
            ?: return invalidNumber("经度")
        val radius = numberParameter(step, "radius")
            ?: return invalidNumber("半径")

        if (latitude !in -90.0..90.0) {
            return invalidLocation("纬度无效", "纬度必须在 -90 到 90 之间。")
        }
        if (longitude !in -180.0..180.0) {
            return invalidLocation("经度无效", "经度必须在 -180 到 180 之间。")
        }
        if (radius <= 0.0) {
            return invalidLocation("半径无效", "触发半径必须大于 0 米。")
        }

        return LocationConfig(
            event = event,
            latitude = latitude,
            longitude = longitude,
            radius = radius
        )
    }

    private fun numberParameter(step: ActionStep, key: String): Double? {
        return when (val value = step.parameters[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun invalidNumber(label: String): LocationConfig {
        return invalidLocation("${label}无效", "${label}必须是有效数字。")
    }

    private fun invalid(title: String, message: String): TriggerDiagnosticResult {
        return TriggerDiagnosticResult(
            status = TriggerDiagnosticStatus.INVALID_CONFIG,
            title = title,
            message = message
        )
    }

    private fun invalidLocation(title: String, message: String): LocationConfig {
        return LocationConfig(error = invalid(title, message))
    }

    private fun unknown(title: String, message: String): TriggerDiagnosticResult {
        return TriggerDiagnosticResult(
            status = TriggerDiagnosticStatus.UNKNOWN,
            title = title,
            message = message
        )
    }

    private fun unsupportedDiagnostic(): TriggerDiagnosticResult {
        return TriggerDiagnosticResult(
            status = TriggerDiagnosticStatus.UNSUPPORTED,
            title = "暂不支持完整诊断",
            message = "当前只能确认参数和权限正常；该触发器依赖系统或外部事件，无法在编辑器中可靠判断是否会命中。"
        )
    }

    private fun stateResult(
        matched: Boolean,
        successTitle: String,
        successMessage: String,
        notMatchedMessage: String
    ): TriggerDiagnosticResult {
        return if (matched) {
            TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.SUCCESS,
                title = successTitle,
                message = successMessage
            )
        } else {
            TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.NOT_MATCHED,
                title = "当前状态未命中",
                message = notMatchedMessage
            )
        }
    }

    private fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    private data class LocationConfig(
        val event: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val radius: Double = 0.0,
        val error: TriggerDiagnosticResult? = null
    )

    data class BluetoothDeviceSnapshot(
        val name: String,
        val address: String
    )

    private val completeDiagnosticModuleIds = setOf(
        LocationTriggerModule().id,
        WifiTriggerModule().id,
        BluetoothTriggerModule().id,
        BatteryTriggerModule().id,
        ScreenTriggerModule().id,
        PowerTriggerModule().id
    )
}
