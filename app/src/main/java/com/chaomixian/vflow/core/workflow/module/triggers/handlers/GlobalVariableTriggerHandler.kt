package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.GlobalVariableChangeEvent
import com.chaomixian.vflow.core.workflow.GlobalVariableStore
import com.chaomixian.vflow.core.workflow.model.TriggerSpec

class GlobalVariableTriggerHandler : ListeningTriggerHandler() {

    private var listenerRegistration: AutoCloseable? = null

    companion object {
        private const val TAG = "GlobalVariableTriggerHandler"

        fun matches(trigger: TriggerSpec, event: GlobalVariableChangeEvent): Boolean {
            val configuredName = (trigger.parameters["variable_name"] as? String).orEmpty().trim()
            return configuredName.isNotEmpty() && configuredName == event.variableName
        }
    }

    override fun startListening(context: Context) {
        listenerRegistration = GlobalVariableStore.addChangeListener { event ->
            handleVariableChanged(context.applicationContext, event)
        }
        DebugLogger.d(TAG, "全局变量变更监听已启动")
    }

    override fun stopListening(context: Context) {
        listenerRegistration?.close()
        listenerRegistration = null
        DebugLogger.d(TAG, "全局变量变更监听已停止")
    }

    private fun handleVariableChanged(context: Context, event: GlobalVariableChangeEvent) {
        val outputs = VDictionary(
            mapOf(
                "variable_name" to VString(event.variableName),
                "old_value" to VString(event.oldValue.asString()),
                "new_value" to VString(event.newValue.asString())
            )
        )
        listeningTriggers
            .filter { matches(it, event) }
            .forEach { trigger ->
                DebugLogger.i(TAG, "触发工作流 '${trigger.workflowName}'，全局变量变更: ${event.variableName}")
                executeTrigger(context, trigger, outputs)
            }
    }
}
