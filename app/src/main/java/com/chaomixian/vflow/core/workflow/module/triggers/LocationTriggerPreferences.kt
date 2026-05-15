package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import androidx.core.content.edit

object LocationTriggerPreferences {
    const val PREFS_NAME = "vFlowPrefs"
    const val KEY_LOCATION_UPDATE_INTERVAL_MINUTES = "locationUpdateIntervalMinutes"
    const val DEFAULT_INTERVAL_MINUTES = 5
    const val MIN_INTERVAL_MINUTES = 1
    const val MAX_INTERVAL_MINUTES = 30

    fun normalizeIntervalMinutes(minutes: Int): Int {
        return minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }

    fun getIntervalMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalizeIntervalMinutes(
            prefs.getInt(KEY_LOCATION_UPDATE_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        )
    }

    fun setIntervalMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_LOCATION_UPDATE_INTERVAL_MINUTES, normalizeIntervalMinutes(minutes))
        }
    }

    fun getIntervalMillis(context: Context): Long {
        return getIntervalMinutes(context) * 60_000L
    }
}
