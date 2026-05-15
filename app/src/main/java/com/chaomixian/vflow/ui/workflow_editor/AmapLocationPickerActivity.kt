package com.chaomixian.vflow.ui.workflow_editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.module.triggers.AmapApiKeyPreferences
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AmapLocationPickerActivity : BaseActivity() {
    private var mapView: MapView? = null
    private var amap: AMap? = null
    private var marker: Marker? = null
    private var circle: Circle? = null
    private lateinit var selectedText: TextView
    private var selectedLatLng: LatLng? = null
    private var selectedName: String = ""
    private var radiusMeters: Double = 500.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasAmapKey()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.amap_picker_missing_key_title)
                .setMessage(R.string.amap_picker_missing_key_message)
                .setPositiveButton(R.string.common_ok) { _, _ -> finish() }
                .setOnDismissListener { finish() }
                .show()
            return
        }

        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        runtimeAmapKey().takeIf { it.isNotBlank() }?.let {
            MapsInitializer.setApiKey(it)
        }

        radiusMeters = intent.getDoubleExtra(EXTRA_RADIUS, 500.0)
        selectedName = intent.getStringExtra(EXTRA_LOCATION_NAME).orEmpty()
        val initialLat = intent.getDoubleExtra(EXTRA_LATITUDE, 39.9042)
        val initialLon = intent.getDoubleExtra(EXTRA_LONGITUDE, 116.4074)
        val initialPoint = LatLng(initialLat, initialLon)

        val root = FrameLayout(this)
        val map = MapView(this)
        mapView = map
        map.onCreate(savedInstanceState)
        root.addView(
            map,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(createBottomPanel())
        setContentView(root)

        amap = map.map.apply {
            uiSettings.isMyLocationButtonEnabled = true
            uiSettings.isZoomControlsEnabled = true
            moveCamera(CameraUpdateFactory.newLatLngZoom(initialPoint, 16f))
            setOnMapClickListener { point ->
                updateSelection(point, getString(R.string.amap_picker_default_name))
            }
        }
        updateSelection(initialPoint, selectedName.ifBlank { getString(R.string.amap_picker_default_name) })
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView?.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    private fun createBottomPanel(): LinearLayout {
        val density = resources.displayMetrics.density
        selectedText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, (12 * density).toInt(), 0)
        }
        val confirmButton = MaterialButton(this).apply {
            text = getString(R.string.amap_picker_confirm)
            setOnClickListener { finishWithSelection() }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
            setBackgroundColor(Color.argb(220, 20, 24, 31))
            addView(
                selectedText,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(confirmButton)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
    }

    private fun updateSelection(point: LatLng, name: String) {
        selectedLatLng = point
        selectedName = name
        selectedText.text = getString(
            R.string.amap_picker_selected_location,
            point.latitude,
            point.longitude,
            radiusMeters.toInt()
        )
        marker?.remove()
        circle?.remove()
        marker = amap?.addMarker(MarkerOptions().position(point).title(name))
        circle = amap?.addCircle(
            CircleOptions()
                .center(point)
                .radius(radiusMeters)
                .strokeColor(Color.argb(220, 25, 103, 255))
                .fillColor(Color.argb(45, 25, 103, 255))
                .strokeWidth(3f)
        )
    }

    private fun finishWithSelection() {
        val point = selectedLatLng ?: return
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_LATITUDE, point.latitude)
                putExtra(EXTRA_LONGITUDE, point.longitude)
                putExtra(EXTRA_LOCATION_NAME, selectedName)
            }
        )
        finish()
    }

    private fun hasAmapKey(): Boolean {
        if (runtimeAmapKey().isNotBlank()) {
            return true
        }
        val appInfo = packageManager.getApplicationInfo(
            packageName,
            PackageManager.GET_META_DATA
        )
        return !appInfo.metaData?.getString("com.amap.api.v2.apikey").isNullOrBlank()
    }

    private fun runtimeAmapKey(): String {
        return AmapApiKeyPreferences.getApiKey(this)
    }

    companion object {
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_RADIUS = "extra_radius"
        const val EXTRA_LOCATION_NAME = "extra_location_name"

        fun createIntent(
            context: Context,
            latitude: Double,
            longitude: Double,
            radius: Double,
            locationName: String
        ): Intent {
            return Intent(context, AmapLocationPickerActivity::class.java).apply {
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
                putExtra(EXTRA_RADIUS, radius)
                putExtra(EXTRA_LOCATION_NAME, locationName)
            }
        }
    }
}
