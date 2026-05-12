package com.chaomixian.vflow.core.workflow.module.system

import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Test

class UserChoiceModulesTest {

    @Test
    fun `dialog alert module exposes title message buttons and selection outputs`() {
        val module = DialogAlertModule()
        val inputs = module.getInputs().associateBy { it.id }
        val outputs = module.getOutputs(null).associateBy { it.id }

        assertEquals("vflow.device.dialog_alert", module.id)
        assertEquals(ParameterType.STRING, inputs["title"]?.staticType)
        assertEquals(ParameterType.STRING, inputs["message"]?.staticType)
        assertEquals(ParameterType.ANY, inputs["buttons"]?.staticType)
        assertEquals(VTypeRegistry.NUMBER.id, outputs["selectedIndex"]?.typeName)
        assertEquals(VTypeRegistry.STRING.id, outputs["selectedText"]?.typeName)
        assertEquals(listOf(PermissionManager.OVERLAY), module.requiredPermissions)
    }

    @Test
    fun `list selection module exposes title items and selection outputs`() {
        val module = ListSelectionModule()
        val inputs = module.getInputs().associateBy { it.id }
        val outputs = module.getOutputs(null).associateBy { it.id }

        assertEquals("vflow.device.list_selection", module.id)
        assertEquals(ParameterType.STRING, inputs["title"]?.staticType)
        assertEquals(ParameterType.ANY, inputs["items"]?.staticType)
        assertEquals(VTypeRegistry.NUMBER.id, outputs["selectedIndex"]?.typeName)
        assertEquals(VTypeRegistry.STRING.id, outputs["selectedText"]?.typeName)
        assertEquals(listOf(PermissionManager.OVERLAY), module.requiredPermissions)
    }

    @Test
    fun `choice items can be parsed from lines commas and semicolons`() {
        assertEquals(
            listOf("下班打卡", "加班", "锻炼身体"),
            UserChoiceItems.parse(VString("下班打卡\n加班, 锻炼身体"))
        )
        assertEquals(
            listOf("A", "B", "C"),
            UserChoiceItems.parse(VString("A;B；C"))
        )
    }

    @Test
    fun `choice items can be parsed from vflow list`() {
        val items = UserChoiceItems.parse(
            VList(
                listOf(
                    VString("第一个"),
                    VNumber(2),
                    VString("  ")
                )
            )
        )

        assertEquals(listOf("第一个", "2"), items)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank choice items are rejected`() {
        UserChoiceItems.requireNotEmpty(VString(" \n , ; "))
    }
}
