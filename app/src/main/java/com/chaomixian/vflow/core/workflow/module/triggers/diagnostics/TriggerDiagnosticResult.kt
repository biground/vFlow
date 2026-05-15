package com.chaomixian.vflow.core.workflow.module.triggers.diagnostics

enum class TriggerDiagnosticStatus {
    SUCCESS,
    NOT_MATCHED,
    MISSING_PERMISSION,
    INVALID_CONFIG,
    UNSUPPORTED,
    UNKNOWN
}

data class TriggerDiagnosticResult(
    val status: TriggerDiagnosticStatus,
    val title: String,
    val message: String,
    val missingPermissionNames: List<String> = emptyList()
)
