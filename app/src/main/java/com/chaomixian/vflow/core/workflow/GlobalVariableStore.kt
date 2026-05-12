package com.chaomixian.vflow.core.workflow

import android.content.Context
import androidx.core.content.edit
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.google.gson.Gson
import java.util.concurrent.CopyOnWriteArrayList

data class GlobalVariableChangeEvent(
    val variableName: String,
    val oldValue: VObject,
    val newValue: VObject
)

object GlobalVariableStore {
    private const val PREFS_NAME = "global_variable_store"
    private const val KEY_VARIABLES_JSON = "variables_json"

    private val gson = Gson()
    private val changeListeners = CopyOnWriteArrayList<(GlobalVariableChangeEvent) -> Unit>()

    private data class StoredVariable(
        val name: String,
        val type: String,
        val value: Any?
    )

    fun getAll(context: Context): Map<String, VObject> {
        val json = prefs(context).getString(KEY_VARIABLES_JSON, null).orEmpty()
        if (json.isBlank()) return emptyMap()

        return runCatching {
            val stored = gson.fromJson(json, Array<StoredVariable>::class.java).orEmpty()
            stored.associate { entry -> entry.name to deserialize(entry) }
        }.getOrDefault(emptyMap())
    }

    fun get(context: Context, key: String): VObject {
        return getAll(context)[key] ?: VObjectFactory.from(null)
    }

    fun put(context: Context, key: String, value: Any?) {
        val current = getAll(context).toMutableMap()
        val oldValue = current[key] ?: VNull
        val newValue = VObjectFactory.from(value)
        if (valueSignature(oldValue) == valueSignature(newValue)) {
            return
        }
        current[key] = newValue
        save(context, current)
        notifyChanged(GlobalVariableChangeEvent(key, oldValue, newValue))
    }

    fun remove(context: Context, key: String) {
        val current = getAll(context).toMutableMap()
        val oldValue = current.remove(key)
        if (oldValue != null) {
            save(context, current)
            notifyChanged(GlobalVariableChangeEvent(key, oldValue, VNull))
        }
    }

    fun replaceAll(context: Context, values: Map<String, VObject>) {
        val oldValues = getAll(context)
        save(context, values)
        val changedKeys = oldValues.keys + values.keys
        changedKeys.forEach { key ->
            val oldValue = oldValues[key] ?: VNull
            val newValue = values[key] ?: VNull
            if (valueSignature(oldValue) != valueSignature(newValue)) {
                notifyChanged(GlobalVariableChangeEvent(key, oldValue, newValue))
            }
        }
    }

    fun addChangeListener(listener: (GlobalVariableChangeEvent) -> Unit): AutoCloseable {
        changeListeners += listener
        return AutoCloseable {
            changeListeners -= listener
        }
    }

    private fun save(context: Context, values: Map<String, VObject>) {
        val stored = values.entries.map { (name, value) ->
            serialize(name, value)
        }
        prefs(context).edit(commit = true) {
            putString(KEY_VARIABLES_JSON, gson.toJson(stored))
        }
    }

    private fun serialize(name: String, value: VObject): StoredVariable {
        return when (value) {
            is VString -> StoredVariable(name, "string", value.raw)
            is VNumber -> StoredVariable(name, "number", value.raw.toString())
            is VBoolean -> StoredVariable(name, "boolean", value.raw)
            else -> StoredVariable(name, "string", value.asString())
        }
    }

    private fun deserialize(item: StoredVariable): VObject {
        return when (item.type) {
            "string" -> VString(item.value?.toString() ?: "")
            "number" -> (item.value as? Number)?.let { VNumber(it) }
                ?: item.value?.toString()?.toDoubleOrNull()?.let { VNumber(it) }
                ?: VNumber(0)
            "boolean" -> when (item.value) {
                is Boolean -> VBoolean(item.value)
                else -> VBoolean(item.value?.toString()?.toBoolean() ?: false)
            }
            else -> VString(item.value?.toString() ?: "")
        }
    }

    private fun notifyChanged(event: GlobalVariableChangeEvent) {
        changeListeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }

    private fun valueSignature(value: VObject): String {
        return "${value.type.id}:${value.asString()}"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
