package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.content.Context
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.module.triggers.ShizukuStoppedTriggerData
import com.chaomixian.vflow.services.ShellManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class ShizukuStoppedTriggerHandler : ListeningTriggerHandler() {
    private val state = ShizukuStoppedTriggerState()
    private val stateLock = Any()
    private var pollingJob: Job? = null

    @Volatile
    private var appContext: Context? = null

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        DebugLogger.w(TAG, "Shizuku Binder 已断开。")
        handleAvailabilityChange(
            context = appContext ?: return@OnBinderDeadListener,
            isAvailable = false,
            reason = ShizukuStoppedTriggerData.REASON_BINDER_DEAD
        )
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        DebugLogger.d(TAG, "Shizuku Binder 已恢复。")
        val context = appContext ?: return@OnBinderReceivedListener
        handleAvailabilityChange(
            context = context,
            isAvailable = ShellManager.isShizukuActive(context),
            reason = ShizukuStoppedTriggerData.REASON_UNAVAILABLE
        )
    }

    override fun startListening(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        registerShizukuListeners()
        handleAvailabilityChange(
            context = applicationContext,
            isAvailable = ShellManager.isShizukuActive(applicationContext),
            reason = ShizukuStoppedTriggerData.REASON_UNAVAILABLE
        )
        startPolling(applicationContext)
    }

    override fun stopListening(context: Context) {
        pollingJob?.cancel()
        pollingJob = null
        unregisterShizukuListeners()
        synchronized(stateLock) {
            state.reset()
        }
        appContext = null
    }

    private fun registerShizukuListeners() {
        runCatching {
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        }.onFailure { error ->
            DebugLogger.w(TAG, "注册 Shizuku 状态监听失败，将依赖轮询兜底。", error)
        }
    }

    private fun unregisterShizukuListeners() {
        runCatching {
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
        }.onFailure { error ->
            DebugLogger.w(TAG, "移除 Shizuku 状态监听失败。", error)
        }
    }

    private fun startPolling(context: Context) {
        if (pollingJob?.isActive == true) return

        pollingJob = triggerScope.launch {
            while (isActive) {
                handleAvailabilityChange(
                    context = context,
                    isAvailable = ShellManager.isShizukuActive(context),
                    reason = ShizukuStoppedTriggerData.REASON_UNAVAILABLE
                )
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun handleAvailabilityChange(
        context: Context,
        isAvailable: Boolean,
        reason: String
    ) {
        val shouldTrigger = synchronized(stateLock) {
            state.update(isAvailable)
        }

        if (!shouldTrigger) return

        val triggerData = ShizukuStoppedTriggerData(
            reason = reason,
            checkedAt = System.currentTimeMillis()
        )
        listeningTriggers.forEach { trigger ->
            DebugLogger.i(TAG, "Shizuku 不可用，触发工作流: ${trigger.workflowName}")
            executeTrigger(context, trigger, triggerData)
        }
    }

    companion object {
        private const val TAG = "ShizukuStoppedTrigger"
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
