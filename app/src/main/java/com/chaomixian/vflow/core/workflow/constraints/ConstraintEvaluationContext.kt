package com.chaomixian.vflow.core.workflow.constraints

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.workflow.GlobalVariableStore
import java.time.LocalDateTime

data class ConstraintEvaluationContext(
    val applicationContext: Context? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val globalVariables: Map<String, VObject> = emptyMap(),
    val chargingState: Boolean? = null,
    val screenOn: Boolean? = null,
    val networkState: String? = null
) {
    companion object {
        const val NETWORK_WIFI = "wifi"
        const val NETWORK_MOBILE = "mobile"
        const val NETWORK_NONE = "none"
        const val NETWORK_OTHER = "other"

        fun from(context: Context): ConstraintEvaluationContext {
            val appContext = context.applicationContext
            return ConstraintEvaluationContext(
                applicationContext = appContext,
                globalVariables = GlobalVariableStore.getAll(appContext),
                chargingState = readChargingState(appContext),
                screenOn = readScreenState(appContext),
                networkState = readNetworkState(appContext)
            )
        }

        private fun readChargingState(context: Context): Boolean? {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }

        private fun readScreenState(context: Context): Boolean? {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return null
            return powerManager.isInteractive
        }

        private fun readNetworkState(context: Context): String {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NETWORK_NONE
            val network = connectivityManager.activeNetwork ?: return NETWORK_NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NETWORK_NONE
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NETWORK_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NETWORK_MOBILE
                else -> NETWORK_OTHER
            }
        }
    }
}
