package com.chaomixian.vflow.server.wrappers.shell

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.chaomixian.vflow.server.common.FakeContext
import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import com.chaomixian.vflow.server.common.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class IBluetoothManagerWrapper : ServiceWrapper("bluetooth_manager", "android.bluetooth.IBluetoothManager\$Stub") {

    private var enableMethod: Method? = null
    private var disableMethod: Method? = null
    private var isEnabledMethod: Method? = null
    private var getStateMethod: Method? = null

    private data class BluetoothDeviceLookup(
        val device: BluetoothDevice?,
        val error: String? = null
    )

    private data class ProfileSpec(
        val name: String,
        val id: Int
    )

    private data class ProfileProxy(
        val spec: ProfileSpec,
        val proxy: BluetoothProfile
    )

    private data class ConnectionSnapshot(
        val connected: Boolean,
        val profiles: List<String>
    )

    private data class DeviceConnectResult(
        val success: Boolean,
        val connected: Boolean,
        val device: BluetoothDevice?,
        val profiles: List<String>,
        val error: String? = null
    )

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        enableMethod = ReflectionUtils.findMethodLoose(clazz, "enable")
        disableMethod = ReflectionUtils.findMethodLoose(clazz, "disable")
        isEnabledMethod = ReflectionUtils.findMethodLoose(clazz, "isEnabled")
        // 尝试查找 getState 方法作为备选
        getStateMethod = ReflectionUtils.findMethodLoose(clazz, "getState")

        Logger.debug("BluetoothManager", "=== Bluetooth Manager Methods ===")
        Logger.debug("BluetoothManager", "enable: ${enableMethod != null}")
        Logger.debug("BluetoothManager", "disable: ${disableMethod != null}")
        Logger.debug("BluetoothManager", "isEnabled: ${isEnabledMethod != null}")
        if (isEnabledMethod != null) {
            Logger.debug("BluetoothManager", "isEnabled params: ${isEnabledMethod!!.parameterTypes.toList()}")
        }
        Logger.debug("BluetoothManager", "getState: ${getStateMethod != null}")
        if (getStateMethod != null) {
            Logger.debug("BluetoothManager", "getState params: ${getStateMethod!!.parameterTypes.toList()}")
        }

        // 列出所有方法
        Logger.debug("BluetoothManager", "=== All Available Methods ===")
        clazz.declaredMethods.forEach { method ->
            if (method.name.contains("enable") || method.name.contains("Enable") ||
                method.name.contains("state") || method.name.contains("State")) {
                Logger.debug("BluetoothManager", "${method.name}: ${method.parameterTypes.toList()}")
            }
        }
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        // 检查服务是否可用
        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "Bluetooth service is not available or no permission")
            return result
        }

        when (method) {
            "setBluetoothEnabled" -> {
                val success = setBluetoothEnabled(params.getBoolean("enabled"))
                result.put("success", success)
            }
            "isEnabled" -> {
                val enabled = isEnabled()
                result.put("success", true)
                result.put("enabled", enabled)
            }
            "toggle" -> {
                val currentState = isEnabled()
                val newState = !currentState
                val success = setBluetoothEnabled(newState)
                result.put("success", success)
                result.put("enabled", newState) // 返回新状态
            }
            "isDeviceConnected" -> {
                val deviceId = params.optString("device", "")
                val lookup = findBondedDevice(deviceId)
                val device = lookup.device
                if (device == null) {
                    result.put("success", false)
                    result.put("connected", false)
                    result.put("error", lookup.error ?: "Bluetooth device not found")
                } else {
                    val snapshot = getConnectionSnapshot(device)
                    result.put("success", true)
                    result.put("connected", snapshot.connected)
                    result.put("deviceName", device.name ?: "")
                    result.put("deviceAddress", device.address ?: "")
                    result.put("profiles", JSONArray(snapshot.profiles))
                }
            }
            "connectDevice" -> {
                val deviceId = params.optString("device", "")
                val disconnectOthers = params.optBoolean("disconnectOthers", true)
                val connectResult = connectDevice(deviceId, disconnectOthers)
                result.put("success", connectResult.success)
                result.put("connected", connectResult.connected)
                result.put("deviceName", connectResult.device?.name ?: "")
                result.put("deviceAddress", connectResult.device?.address ?: "")
                result.put("profiles", JSONArray(connectResult.profiles))
                connectResult.error?.let { result.put("error", it) }
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    private fun setBluetoothEnabled(enable: Boolean): Boolean {
        if (serviceInterface == null) return false
        return try {
            val method = if (enable) enableMethod else disableMethod
            if (method == null) return false

            // 打印方法签名
            // System.err.println("=== Bluetooth ${if (enable) "enable" else "disable"} Method ===")
            // System.err.println("Method: ${method.name}")
            // System.err.println("Parameter count: ${method.parameterTypes.size}")
            // for (i in method.parameterTypes.indices) {
            //     System.err.println("  Param[$i]: ${method.parameterTypes[i].name}")
            // }

            val args = arrayOfNulls<Any>(method.parameterTypes.size)

            // 预加载 AttributionSource 类（如果可用）
            val attributionSourceClass = try {
                Class.forName("android.content.AttributionSource")
            } catch (e: ClassNotFoundException) {
                null
            }

            // System.err.println("AttributionSource available: ${attributionSourceClass != null}")

            // 根据参数类型填充默认值
            for (i in args.indices) {
                val paramType = method.parameterTypes[i]
                args[i] = when {
                    // AttributionSource 类型 (Android 12+)
                    attributionSourceClass != null && paramType == attributionSourceClass -> {
                        // 创建 AttributionSource
                        createAttributionSource()
                    }
                    // String 类型 (旧版本 Android 11-)
                    paramType == String::class.java -> "com.android.shell"
                    // int 类型
                    paramType == java.lang.Integer.TYPE || paramType == Int::class.javaPrimitiveType -> Integer.valueOf(0)
                    // boolean 类型
                    paramType == java.lang.Boolean.TYPE || paramType == Boolean::class.javaPrimitiveType -> java.lang.Boolean.FALSE
                    // 其他
                    else -> null
                }
                // System.err.println("  args[$i] = ${args[i]} (${args[i]?.javaClass?.name})")
            }

            val result = method.invoke(serviceInterface, *args) as? Boolean ?: false
            // System.err.println("Result: $result")
            result
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 创建 AttributionSource 对象 (Android 12+)
     */
    private fun createAttributionSource(): Any {
        return try {
            // 使用 AttributionSource.Builder (Android 12+)
            val builderClass = Class.forName("android.content.AttributionSource\$Builder")

            // AttributionSource.Builder 需要 uid 参数
            // 使用 Process.myUid() 获取当前进程 uid
            val processClass = Class.forName("android.os.Process")
            val myUidMethod = processClass.getDeclaredMethod("myUid")
            val uid = myUidMethod.invoke(null) as Int

            // System.err.println("Creating AttributionSource with uid=$uid, packageName=com.android.shell")

            // 使用 Builder(int uid) 构造函数
            val constructor = builderClass.getConstructor(Int::class.javaPrimitiveType)
            val builder = constructor.newInstance(uid)

            // 尝试设置 packageName
            try {
                val setPackageNameMethod = builderClass.getDeclaredMethod("setPackageName", String::class.java)
                setPackageNameMethod.invoke(builder, "com.android.shell")
                // System.err.println("Set packageName to com.android.shell")
            } catch (e: NoSuchMethodException) {
                // System.err.println("setPackageName method not found, trying setAttributionTag")
            }

            // 尝试设置 attributionTag
            try {
                val setAttributionTagMethod = builderClass.getDeclaredMethod("setAttributionTag", String::class.java)
                setAttributionTagMethod.invoke(builder, null)
                // System.err.println("Set attributionTag to null")
            } catch (e: NoSuchMethodException) {
                // System.err.println("setAttributionTag method not found")
            }

            // 尝试设置 permission 字段
            try {
                val setPermissionMethod = builderClass.getDeclaredMethod("setPermission", String::class.java)
                setPermissionMethod.invoke(builder, null)
                // System.err.println("Set permission to null")
            } catch (e: NoSuchMethodException) {
                // System.err.println("setPermission method not found")
            }

            val buildMethod = builderClass.getDeclaredMethod("build")
            val attributionSource = buildMethod.invoke(builder)

            // System.err.println("AttributionSource created: $attributionSource")
            // System.err.println("AttributionSource details: uid=${getAttributionSourceUid(attributionSource)}, packageName=${getAttributionSourcePackageName(attributionSource)}")

            attributionSource
        } catch (e: Exception) {
            // 如果创建失败，抛出异常
            System.err.println("Failed to create AttributionSource: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun getAttributionSourceUid(attributionSource: Any): Int {
        return try {
            val method = attributionSource.javaClass.getDeclaredMethod("getUid")
            method.invoke(attributionSource) as Int
        } catch (e: Exception) {
            -1
        }
    }

    private fun getAttributionSourcePackageName(attributionSource: Any): String? {
        return try {
            val method = attributionSource.javaClass.getDeclaredMethod("getPackageName")
            method.invoke(attributionSource) as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        return try {
            val manager = FakeContext.get().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            Logger.error("BluetoothManager", "Failed to get BluetoothAdapter: ${e.message}", e)
            null
        }
    }

    private fun isBluetoothCurrentlyEnabled(): Boolean {
        return try {
            getBluetoothAdapter()?.isEnabled == true || isEnabled()
        } catch (_: Exception) {
            isEnabled()
        }
    }

    private fun findBondedDevice(identifier: String): BluetoothDeviceLookup {
        val normalizedIdentifier = identifier.trim()
        if (normalizedIdentifier.isBlank()) {
            return BluetoothDeviceLookup(null, "Device name or address is empty")
        }

        val adapter = getBluetoothAdapter()
            ?: return BluetoothDeviceLookup(null, "Bluetooth adapter is not available")

        if (!isBluetoothCurrentlyEnabled()) {
            return BluetoothDeviceLookup(null, "Bluetooth is disabled")
        }

        val bondedDevices = try {
            adapter.bondedDevices.orEmpty()
        } catch (e: Exception) {
            Logger.error("BluetoothManager", "Failed to read bonded devices: ${e.message}", e)
            return BluetoothDeviceLookup(null, e.message ?: "Failed to read bonded devices")
        }

        if (bondedDevices.isEmpty()) {
            return BluetoothDeviceLookup(null, "No paired Bluetooth devices")
        }

        val byAddress = bondedDevices.firstOrNull {
            it.address.equals(normalizedIdentifier, ignoreCase = true)
        }
        if (byAddress != null) return BluetoothDeviceLookup(byAddress)

        val byExactName = bondedDevices.firstOrNull {
            it.name.equals(normalizedIdentifier, ignoreCase = true)
        }
        if (byExactName != null) return BluetoothDeviceLookup(byExactName)

        val byPartialName = bondedDevices.firstOrNull {
            it.name?.contains(normalizedIdentifier, ignoreCase = true) == true
        }
        if (byPartialName != null) return BluetoothDeviceLookup(byPartialName)

        return BluetoothDeviceLookup(null, "Paired Bluetooth device not found: $normalizedIdentifier")
    }

    private fun connectDevice(identifier: String, disconnectOthers: Boolean): DeviceConnectResult {
        if (!isBluetoothCurrentlyEnabled()) {
            val enabled = setBluetoothEnabled(true)
            if (!enabled) {
                return DeviceConnectResult(
                    success = false,
                    connected = false,
                    device = null,
                    profiles = emptyList(),
                    error = "Failed to enable Bluetooth"
                )
            }
            waitForBluetoothEnabled()
        }

        val lookup = findBondedDevice(identifier)
        val device = lookup.device ?: return DeviceConnectResult(
            success = false,
            connected = false,
            device = null,
            profiles = emptyList(),
            error = lookup.error ?: "Bluetooth device not found"
        )

        val before = getConnectionSnapshot(device)
        if (before.connected) {
            setActiveDevice(device)
            return DeviceConnectResult(
                success = true,
                connected = true,
                device = device,
                profiles = before.profiles
            )
        }

        val proxies = acquireProfileProxies()
        if (proxies.isEmpty()) {
            return DeviceConnectResult(
                success = false,
                connected = false,
                device = device,
                profiles = emptyList(),
                error = "No Bluetooth profile proxy is available"
            )
        }

        var invokedConnect = false
        var supportedProfile = false
        var lastError: String? = null

        try {
            proxies.forEach { profileProxy ->
                if (disconnectOthers) {
                    disconnectOtherDevices(profileProxy, device)
                }

                val connectMethod = findDeviceMethod(profileProxy.proxy, "connect")
                if (connectMethod == null) {
                    Logger.debug("BluetoothManager", "${profileProxy.spec.name} has no connect(BluetoothDevice) method")
                    return@forEach
                }

                supportedProfile = true
                try {
                    val rawResult = connectMethod.invoke(profileProxy.proxy, device)
                    invokedConnect = when (rawResult) {
                        is Boolean -> invokedConnect || rawResult
                        else -> true
                    }
                    Logger.info("BluetoothManager", "Invoked ${profileProxy.spec.name}.connect for ${device.address}, result=$rawResult")
                } catch (e: Exception) {
                    lastError = e.message
                    Logger.error("BluetoothManager", "${profileProxy.spec.name}.connect failed: ${e.message}", e)
                }
            }
        } finally {
            proxies.forEach { closeProfileProxy(it) }
        }

        repeat(8) {
            Thread.sleep(750)
            val snapshot = getConnectionSnapshot(device)
            if (snapshot.connected) {
                setActiveDevice(device)
                return DeviceConnectResult(
                    success = true,
                    connected = true,
                    device = device,
                    profiles = snapshot.profiles
                )
            }
        }

        val after = getConnectionSnapshot(device)
        return DeviceConnectResult(
            success = invokedConnect && after.connected,
            connected = after.connected,
            device = device,
            profiles = after.profiles,
            error = when {
                after.connected -> null
                !supportedProfile -> "No connectable Bluetooth profile is available for this device"
                lastError != null -> lastError
                else -> "Bluetooth connection request was sent but the device did not connect"
            }
        )
    }

    private fun waitForBluetoothEnabled() {
        repeat(10) {
            if (isBluetoothCurrentlyEnabled()) return
            Thread.sleep(500)
        }
    }

    private fun getConnectionSnapshot(device: BluetoothDevice): ConnectionSnapshot {
        val profiles = mutableListOf<String>()

        if (isDeviceConnectedByHiddenApi(device)) {
            profiles.add("device")
        }

        val proxies = acquireProfileProxies()
        try {
            proxies.forEach { profileProxy ->
                if (getConnectedDevices(profileProxy.proxy).any { it.address == device.address }) {
                    profiles.add(profileProxy.spec.name)
                }
            }
        } finally {
            proxies.forEach { closeProfileProxy(it) }
        }

        return ConnectionSnapshot(
            connected = profiles.isNotEmpty(),
            profiles = profiles.distinct()
        )
    }

    private fun isDeviceConnectedByHiddenApi(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun acquireProfileProxies(timeoutMs: Long = 1500L): List<ProfileProxy> {
        val adapter = getBluetoothAdapter() ?: return emptyList()
        val context = FakeContext.get()
        val specs = bluetoothProfileSpecs()
        val proxies = mutableListOf<ProfileProxy>()

        specs.forEach { spec ->
            val latch = CountDownLatch(1)
            var proxy: BluetoothProfile? = null
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, serviceProxy: BluetoothProfile) {
                    proxy = serviceProxy
                    latch.countDown()
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == spec.id) proxy = null
                }
            }

            try {
                val requested = adapter.getProfileProxy(context, listener, spec.id)
                if (requested && latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    proxy?.let { proxies.add(ProfileProxy(spec, it)) }
                }
            } catch (e: Exception) {
                Logger.error("BluetoothManager", "Failed to acquire ${spec.name} proxy: ${e.message}", e)
            }
        }

        return proxies
    }

    private fun bluetoothProfileSpecs(): List<ProfileSpec> {
        val specs = mutableListOf(
            ProfileSpec("headset", BluetoothProfile.HEADSET),
            ProfileSpec("a2dp", BluetoothProfile.A2DP)
        )

        runCatching {
            val hearingAid = BluetoothProfile::class.java.getField("HEARING_AID").getInt(null)
            specs.add(ProfileSpec("hearing_aid", hearingAid))
        }
        runCatching {
            val leAudio = BluetoothProfile::class.java.getField("LE_AUDIO").getInt(null)
            specs.add(ProfileSpec("le_audio", leAudio))
        }

        return specs
    }

    private fun closeProfileProxy(profileProxy: ProfileProxy) {
        try {
            getBluetoothAdapter()?.closeProfileProxy(profileProxy.spec.id, profileProxy.proxy)
        } catch (e: Exception) {
            Logger.error("BluetoothManager", "Failed to close ${profileProxy.spec.name} proxy: ${e.message}", e)
        }
    }

    private fun getConnectedDevices(proxy: BluetoothProfile): List<BluetoothDevice> {
        return try {
            proxy.connectedDevices.orEmpty()
        } catch (e: Exception) {
            Logger.error("BluetoothManager", "Failed to read connected devices: ${e.message}", e)
            emptyList()
        }
    }

    private fun disconnectOtherDevices(profileProxy: ProfileProxy, target: BluetoothDevice) {
        val disconnectMethod = findDeviceMethod(profileProxy.proxy, "disconnect") ?: return
        getConnectedDevices(profileProxy.proxy)
            .filter { it.address != target.address }
            .forEach { device ->
                runCatching {
                    disconnectMethod.invoke(profileProxy.proxy, device)
                    Logger.info("BluetoothManager", "Disconnected ${device.address} from ${profileProxy.spec.name}")
                }.onFailure {
                    Logger.error("BluetoothManager", "Failed to disconnect ${device.address}: ${it.message}", it)
                }
            }
    }

    private fun setActiveDevice(device: BluetoothDevice) {
        val proxies = acquireProfileProxies()
        try {
            proxies.forEach { profileProxy ->
                val method = findDeviceMethod(profileProxy.proxy, "setActiveDevice") ?: return@forEach
                runCatching {
                    method.invoke(profileProxy.proxy, device)
                    Logger.info("BluetoothManager", "Set active device ${device.address} for ${profileProxy.spec.name}")
                }.onFailure {
                    Logger.error("BluetoothManager", "Failed to set active device for ${profileProxy.spec.name}: ${it.message}", it)
                }
            }
        } finally {
            proxies.forEach { closeProfileProxy(it) }
        }
    }

    private fun findDeviceMethod(target: Any, name: String): Method? {
        val methods = buildList {
            addAll(target.javaClass.methods)
            var clazz: Class<*>? = target.javaClass
            while (clazz != null) {
                addAll(clazz.declaredMethods)
                clazz = clazz.superclass
            }
        }
        return methods.firstOrNull { method ->
            method.name == name &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == BluetoothDevice::class.java
        }?.apply { isAccessible = true }
    }

    /**
     * 获取蓝牙当前状态
     */
    private fun isEnabled(): Boolean {
        if (serviceInterface == null) return false

        // 首先尝试使用 isEnabled 方法
        if (isEnabledMethod != null) {
            return try {
                Logger.debug("BluetoothManager", "Using isEnabled method...")
                val paramCount = isEnabledMethod!!.parameterTypes.size
                Logger.debug("BluetoothManager", "isEnabled has $paramCount parameters")

                val result = if (paramCount == 0) {
                    // 无参数版本
                    isEnabledMethod!!.invoke(serviceInterface) as? Boolean ?: false
                } else {
                    // 有参数版本，尝试传入默认值
                    val args = arrayOfNulls<Any>(paramCount)
                    for (i in args.indices) {
                        val paramType = isEnabledMethod!!.parameterTypes[i]
                        args[i] = when {
                            paramType == String::class.java -> "com.android.shell"
                            paramType == Int::class.javaPrimitiveType || paramType == Int::class.javaObjectType -> 0
                            else -> null
                        }
                    }
                    isEnabledMethod!!.invoke(serviceInterface, *args) as? Boolean ?: false
                }
                Logger.debug("BluetoothManager", "isEnabled result: $result")
                result
            } catch (e: Exception) {
                Logger.error("BluetoothManager", "isEnabled failed: ${e.message}", e)
                // 继续尝试其他方法
                false
            }
        }

        // 如果 isEnabled 不存在或失败，尝试使用 getState 方法
        if (getStateMethod != null) {
            return try {
                Logger.debug("BluetoothManager", "Using getState method...")
                val state = getStateMethod!!.invoke(serviceInterface) as? Int ?: -1
                Logger.debug("BluetoothManager", "getState result: $state")
                // BluetoothAdapter 状态常量:
                // STATE_OFF = 10, STATE_TURNING_ON = 11, STATE_ON = 12, STATE_TURNING_OFF = 13
                val isEnabled = state == 12 // STATE_ON
                Logger.info("BluetoothManager", "Bluetooth enabled: $isEnabled (state=$state)")
                isEnabled
            } catch (e: Exception) {
                Logger.error("BluetoothManager", "getState failed: ${e.message}", e)
                false
            }
        }

        // 最后的备选方案：使用 dumpsys 命令
        Logger.warn("BluetoothManager", "Trying dumpsys bluetooth_manager as fallback...")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys bluetooth_manager | grep '^  enabled:' | awk '{print $2}'"))
            val output = process.inputStream.bufferedReader().readText().trim()
            Logger.debug("BluetoothManager", "dumpsys output: '$output'")
            output.toBoolean() || output == "true"
        } catch (e: Exception) {
            Logger.error("BluetoothManager", "dumpsys failed: ${e.message}", e)
            false
        }
    }
}
