package com.chaomixian.vflow.core.workflow.module.triggers.diagnostics

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.chaomixian.vflow.core.workflow.module.triggers.AmapApiKeyPreferences
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal object AmapDiagnosticLocationProvider {
    private const val SOURCE_NAME = "高德定位"
    private const val REQUEST_TIMEOUT_MS = 12_000L
    private const val HTTP_TIMEOUT_MS = 8_000L

    suspend fun getCurrentLocation(context: Context): DiagnosticLocation? {
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val appContext = context.applicationContext
                val mainHandler = Handler(Looper.getMainLooper())
                var client: AMapLocationClient? = null
                var listener: AMapLocationListener? = null

                fun runOnMain(block: () -> Unit) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        block()
                    } else {
                        mainHandler.post(block)
                    }
                }

                fun cleanup() {
                    val locationClient = client
                    val locationListener = listener
                    listener = null
                    client = null
                    if (locationClient != null && locationListener != null) {
                        runCatching { locationClient.unRegisterLocationListener(locationListener) }
                    }
                    runCatching { locationClient?.stopLocation() }
                    runCatching { locationClient?.onDestroy() }
                }

                fun resumeWith(location: DiagnosticLocation?) {
                    runOnMain {
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    runOnMain { cleanup() }
                }

                runOnMain {
                    try {
                        AMapLocationClient.updatePrivacyShow(appContext, true, true)
                        AMapLocationClient.updatePrivacyAgree(appContext, true)
                        AmapApiKeyPreferences.getApiKey(appContext)
                            .takeIf { it.isNotBlank() }
                            ?.let { AMapLocationClient.setApiKey(it) }

                        val locationClient = AMapLocationClient(appContext)
                        val locationListener = AMapLocationListener { location ->
                            resumeWith(location?.toDiagnosticLocation())
                        }
                        val option = AMapLocationClientOption()
                            .setLocationMode(
                                AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                            )
                            .setOnceLocation(true)
                            .setOnceLocationLatest(true)
                            .setNeedAddress(false)
                            .setLocationCacheEnable(false)
                            .setOffset(true)
                            .setHttpTimeOut(HTTP_TIMEOUT_MS)

                        client = locationClient
                        listener = locationListener
                        locationClient.setLocationOption(option)
                        locationClient.setLocationListener(locationListener)
                        locationClient.startLocation()
                    } catch (_: Exception) {
                        resumeWith(null)
                    }
                }
            }
        }
    }

    private fun AMapLocation.toDiagnosticLocation(): DiagnosticLocation? {
        if (errorCode != AMapLocation.LOCATION_SUCCESS) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return DiagnosticLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            sourceName = SOURCE_NAME
        )
    }
}

internal data class DiagnosticLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val sourceName: String
)
