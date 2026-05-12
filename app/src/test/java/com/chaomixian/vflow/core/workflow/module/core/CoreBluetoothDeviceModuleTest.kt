package com.chaomixian.vflow.core.workflow.module.core

import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreBluetoothDeviceModuleTest {

    @Test
    fun `connected module exposes device identifier input and connection outputs`() {
        val module = CoreBluetoothDeviceConnectedModule()
        val inputs = module.getInputs().associateBy { it.id }
        val outputs = module.getOutputs(null).map { it.id }

        assertEquals("vflow.core.bluetooth_device_connected", module.id)
        assertEquals(ParameterType.STRING, inputs.getValue("device").staticType)
        assertEquals(listOf(PermissionManager.CORE), module.getRequiredPermissions(null))
        assertEquals(
            listOf("connected", "device_name", "device_address", "profiles", "error"),
            outputs
        )
    }

    @Test
    fun `switch module exposes device identifier input and switch outputs`() {
        val module = CoreBluetoothSwitchDeviceModule()
        val inputs = module.getInputs().associateBy { it.id }
        val outputs = module.getOutputs(null).map { it.id }

        assertEquals("vflow.core.bluetooth_switch_device", module.id)
        assertEquals(ParameterType.STRING, inputs.getValue("device").staticType)
        assertEquals(ParameterType.BOOLEAN, inputs.getValue("disconnect_others").staticType)
        assertEquals(true, inputs.getValue("disconnect_others").defaultValue)
        assertEquals(listOf(PermissionManager.CORE), module.getRequiredPermissions(null))
        assertEquals(
            listOf("success", "connected", "device_name", "device_address", "profiles", "error"),
            outputs
        )
    }
}
