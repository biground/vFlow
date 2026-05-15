package com.chaomixian.vflow.ui.workflow_editor

internal object TriggerConstraintUiFormatter {
    fun countText(
        count: Int,
        emptyText: String,
        countText: (Int) -> String
    ): String {
        return if (count == 0) emptyText else countText(count)
    }

    fun appendCountToSummary(
        summary: CharSequence?,
        count: Int,
        countText: (Int) -> String
    ): CharSequence? {
        if (count <= 0) return summary
        val suffix = countText(count)
        return if (summary.isNullOrBlank()) {
            suffix
        } else {
            "${summary}\n$suffix"
        }
    }
}
