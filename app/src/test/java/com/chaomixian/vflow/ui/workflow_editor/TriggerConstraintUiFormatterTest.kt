package com.chaomixian.vflow.ui.workflow_editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerConstraintUiFormatterTest {
    @Test
    fun countTextUsesEmptyCopyWhenNoConstraintExists() {
        val text = TriggerConstraintUiFormatter.countText(
            count = 0,
            emptyText = "未添加约束",
            countText = { "已添加 $it 个约束" }
        )

        assertEquals("未添加约束", text)
    }

    @Test
    fun appendCountToSummaryAddsConstraintCountOnNewLine() {
        val summary = TriggerConstraintUiFormatter.appendCountToSummary(
            summary = "进入公司范围",
            count = 2,
            countText = { "2 个约束" }
        )

        assertEquals("进入公司范围\n2 个约束", summary.toString())
    }
}
