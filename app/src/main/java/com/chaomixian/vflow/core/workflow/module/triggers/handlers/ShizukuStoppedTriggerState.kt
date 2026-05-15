package com.chaomixian.vflow.core.workflow.module.triggers.handlers

class ShizukuStoppedTriggerState {
    private var hasSeenAvailable = false
    private var hasTriggeredForCurrentOutage = false

    fun reset() {
        hasSeenAvailable = false
        hasTriggeredForCurrentOutage = false
    }

    fun update(isAvailable: Boolean): Boolean {
        if (isAvailable) {
            hasSeenAvailable = true
            hasTriggeredForCurrentOutage = false
            return false
        }

        if (hasSeenAvailable && !hasTriggeredForCurrentOutage) {
            hasTriggeredForCurrentOutage = true
            return true
        }

        return false
    }
}
