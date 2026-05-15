package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import androidx.core.content.edit

object AmapApiKeyPreferences {
    const val PREFS_NAME = "vFlowPrefs"
    const val KEY_AMAP_API_KEY = "amapApiKey"

    fun normalizeApiKey(apiKey: String?): String {
        return apiKey.orEmpty().trim()
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalizeApiKey(prefs.getString(KEY_AMAP_API_KEY, ""))
    }

    fun setApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_AMAP_API_KEY, normalizeApiKey(apiKey))
        }
    }
}
