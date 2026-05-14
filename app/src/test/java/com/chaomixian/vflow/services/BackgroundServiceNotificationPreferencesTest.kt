package com.chaomixian.vflow.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString

class BackgroundServiceNotificationPreferencesTest {

    @Test
    fun `normalize uses defaults for blank custom title and text`() {
        val settings = BackgroundServiceNotificationPreferences.normalize(
            title = "   ",
            text = "",
            iconPath = null,
            defaultTitle = "vFlow 后台服务",
            defaultText = "正在监听自动化触发器..."
        )

        assertEquals("vFlow 后台服务", settings.title)
        assertEquals("正在监听自动化触发器...", settings.text)
        assertNull(settings.iconPath)
    }

    @Test
    fun `normalize trims custom notification title text and icon path`() {
        val settings = BackgroundServiceNotificationPreferences.normalize(
            title = "  自定义标题  ",
            text = "  自定义内容  ",
            iconPath = "  /tmp/custom.png  ",
            defaultTitle = "vFlow 后台服务",
            defaultText = "正在监听自动化触发器..."
        )

        assertEquals("自定义标题", settings.title)
        assertEquals("自定义内容", settings.text)
        assertEquals("/tmp/custom.png", settings.iconPath)
    }

    @Test
    fun `renderTemplate replaces bracket global variable references`() {
        val rendered = BackgroundServiceNotificationPreferences.renderTemplate(
            template = "当前模式：[[mode]]，次数：[[count]]",
            globalVariables = mapOf(
                "mode" to VString("工作"),
                "count" to VNumber(3)
            )
        )

        assertEquals("当前模式：工作，次数：3", rendered)
    }

    @Test
    fun `renderTemplate supports canonical global variable references and blanks missing values`() {
        val rendered = BackgroundServiceNotificationPreferences.renderTemplate(
            template = "温度：{{global.temperature}} / [[missing]]",
            globalVariables = mapOf("temperature" to VString("24C"))
        )

        assertEquals("温度：24C / ", rendered)
    }
}
