package com.chaomixian.vflow.core.workflow.module.triggers.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.normalizeEnumValueOrNull
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.triggers.LocationTriggerModule
import com.chaomixian.vflow.permissions.PermissionManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TriggerDiagnosticRunner {

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

        return if (step.moduleId == LocationTriggerModule().id) {
            diagnoseLocationRuntime(context, step)
        } else {
            TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.UNSUPPORTED,
                title = "暂不支持完整诊断",
                message = "当前只能确认参数和权限正常；该触发器依赖系统或外部事件，无法在编辑器中可靠判断是否会命中。"
            )
        }
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
            return invalid("纬度无效", "纬度必须在 -90 到 90 之间。")
        }
        if (longitude !in -180.0..180.0) {
            return invalid("经度无效", "经度必须在 -180 到 180 之间。")
        }
        if (radius <= 0.0) {
            return invalid("半径无效", "触发半径必须大于 0 米。")
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
        return invalid("${label}无效", "${label}必须是有效数字。")
    }

    private fun invalid(title: String, message: String): LocationConfig {
        return LocationConfig(
            error = TriggerDiagnosticResult(
                status = TriggerDiagnosticStatus.INVALID_CONFIG,
                title = title,
                message = message
            )
        )
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
}
