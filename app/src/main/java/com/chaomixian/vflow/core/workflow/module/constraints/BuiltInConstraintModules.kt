package com.chaomixian.vflow.core.workflow.module.constraints

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.workflow.constraints.ConstraintEvaluationContext
import com.chaomixian.vflow.core.workflow.constraints.ConstraintModule
import com.chaomixian.vflow.core.workflow.constraints.ConstraintResult
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.module.logic.ALL_OPERATORS
import com.chaomixian.vflow.core.workflow.module.logic.CONDITION_OPERATOR_LEGACY_MAP
import com.chaomixian.vflow.core.workflow.module.logic.CONDITION_OPERATOR_OPTION_RES_IDS
import com.chaomixian.vflow.core.workflow.module.logic.ConditionEvaluator
import com.chaomixian.vflow.core.workflow.module.logic.OP_EQUALS
import java.time.LocalTime

private const val CATEGORY_CONSTRAINT_LABEL = "约束"

abstract class BaseConstraintModule : BaseModule(), ConstraintModule {
    override fun getOutputs(step: ActionStep?) = emptyList<com.chaomixian.vflow.core.module.OutputDefinition>()
}

class TimeRangeConstraintModule : BaseConstraintModule() {
    companion object {
        const val MODE_INSIDE = "inside"
        const val MODE_OUTSIDE = "outside"
    }

    override val id = "vflow.constraint.time_range"
    override val metadata = ActionMetadata(
        name = "时间段约束",
        description = "限制触发器只在指定时间段内或时间段外生效",
        iconRes = R.drawable.rounded_avg_time_24,
        category = CATEGORY_CONSTRAINT_LABEL,
        categoryId = "constraint"
    )

    override fun getInputs() = listOf(
        InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_INSIDE,
            options = listOf(MODE_INSIDE, MODE_OUTSIDE),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "start_time",
            name = "开始时间",
            staticType = ParameterType.STRING,
            defaultValue = "08:00",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "end_time",
            name = "结束时间",
            staticType = ParameterType.STRING,
            defaultValue = "12:00",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
    )

    override suspend fun evaluateConstraint(
        context: ConstraintEvaluationContext,
        step: ActionStep
    ): ConstraintResult {
        val start = parseTime(step.parameters["start_time"]) ?: return ConstraintResult.blocked("开始时间无效")
        val end = parseTime(step.parameters["end_time"]) ?: return ConstraintResult.blocked("结束时间无效")
        val now = context.now.toLocalTime()
        val inside = isInside(now, start, end)
        val mode = step.parameters["mode"] as? String ?: MODE_INSIDE
        val allowed = if (mode == MODE_OUTSIDE) !inside else inside
        return if (allowed) ConstraintResult.allowed() else ConstraintResult.blocked("当前时间不满足约束")
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val start = step.parameters["start_time"]?.toString().orEmpty()
        val end = step.parameters["end_time"]?.toString().orEmpty()
        val mode = step.parameters["mode"] as? String ?: MODE_INSIDE
        return if (mode == MODE_OUTSIDE) {
            "不在 $start-$end"
        } else {
            "在 $start-$end"
        }
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        if (parseTime(step.parameters["start_time"]) == null) {
            return ValidationResult(false, "开始时间格式应为 HH:mm")
        }
        if (parseTime(step.parameters["end_time"]) == null) {
            return ValidationResult(false, "结束时间格式应为 HH:mm")
        }
        return ValidationResult(true)
    }

    private fun parseTime(value: Any?): LocalTime? {
        return runCatching { LocalTime.parse(value?.toString().orEmpty()) }.getOrNull()
    }

    private fun isInside(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start <= end) {
            !now.isBefore(start) && !now.isAfter(end)
        } else {
            !now.isBefore(start) || !now.isAfter(end)
        }
    }
}

class WeekdayConstraintModule : BaseConstraintModule() {
    companion object {
        const val MODE_IN = "in"
        const val MODE_NOT_IN = "not_in"
    }

    override val id = "vflow.constraint.weekday"
    override val metadata = ActionMetadata(
        name = "星期约束",
        description = "限制触发器只在指定星期生效",
        iconRes = R.drawable.rounded_avg_time_24,
        category = CATEGORY_CONSTRAINT_LABEL,
        categoryId = "constraint"
    )

    override fun getInputs() = listOf(
        InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_IN,
            options = listOf(MODE_IN, MODE_NOT_IN),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "days",
            name = "星期",
            staticType = ParameterType.STRING,
            defaultValue = "1,2,3,4,5",
            hint = "1=周一，7=周日，用逗号分隔",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
    )

    override suspend fun evaluateConstraint(
        context: ConstraintEvaluationContext,
        step: ActionStep
    ): ConstraintResult {
        val selectedDays = parseDays(step.parameters["days"])
        if (selectedDays.isEmpty()) return ConstraintResult.blocked("星期配置为空")
        val currentDay = context.now.dayOfWeek.value
        val contains = currentDay in selectedDays
        val mode = step.parameters["mode"] as? String ?: MODE_IN
        val allowed = if (mode == MODE_NOT_IN) !contains else contains
        return if (allowed) ConstraintResult.allowed() else ConstraintResult.blocked("当前星期不满足约束")
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val days = parseDays(step.parameters["days"]).joinToString(",")
        val mode = step.parameters["mode"] as? String ?: MODE_IN
        return if (mode == MODE_NOT_IN) "不在星期 $days" else "在星期 $days"
    }

    private fun parseDays(value: Any?): Set<Int> {
        return when (value) {
            is List<*> -> value.mapNotNull { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() }
            else -> value?.toString().orEmpty()
                .split(',', '，', ' ')
                .mapNotNull { it.trim().toIntOrNull() }
        }.filter { it in 1..7 }.toSet()
    }
}

class GlobalVariableConstraintModule : BaseConstraintModule() {
    override val id = "vflow.constraint.global_variable"
    override val metadata = ActionMetadata(
        name = "全局变量约束",
        description = "根据全局变量的当前值判断是否允许触发",
        iconRes = R.drawable.rounded_dataset_24,
        category = CATEGORY_CONSTRAINT_LABEL,
        categoryId = "constraint"
    )

    override fun getInputs() = listOf(
        InputDefinition(
            id = "variable_name",
            name = "变量名",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "operator",
            name = "条件",
            staticType = ParameterType.ENUM,
            defaultValue = OP_EQUALS,
            options = ALL_OPERATORS,
            optionsStringRes = CONDITION_OPERATOR_OPTION_RES_IDS,
            legacyValueMap = CONDITION_OPERATOR_LEGACY_MAP,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "value",
            name = "比较值",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "value2",
            name = "第二比较值",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false,
            acceptsNamedVariable = false,
            isFolded = true
        )
    )

    override suspend fun evaluateConstraint(
        context: ConstraintEvaluationContext,
        step: ActionStep
    ): ConstraintResult {
        val variableName = step.parameters["variable_name"]?.toString().orEmpty()
        if (variableName.isBlank()) return ConstraintResult.blocked("变量名为空")

        val hasVariable = context.globalVariables.containsKey(variableName)
        val value: VObject? = context.globalVariables[variableName]
        val operator = getInputs().normalizeOperator(step.parameters["operator"] as? String)
        val allowed = ConditionEvaluator.evaluateCondition(
            input1 = if (hasVariable) value else null,
            operator = operator,
            value1 = step.parameters["value"],
            value2 = step.parameters["value2"]
        )
        return if (allowed) ConstraintResult.allowed() else ConstraintResult.blocked("全局变量不满足约束")
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val variableName = step.parameters["variable_name"]?.toString().orEmpty()
        val operator = step.parameters["operator"]?.toString().orEmpty()
        val value = step.parameters["value"]?.toString().orEmpty()
        return "$variableName $operator $value"
    }

    private fun List<InputDefinition>.normalizeOperator(value: String?): String {
        return first { it.id == "operator" }.normalizeEnumValue(value, OP_EQUALS) ?: OP_EQUALS
    }
}

class ChargingStateConstraintModule : BooleanStateConstraintModule(
    moduleId = "vflow.constraint.charging_state",
    moduleName = "充电状态约束",
    moduleDescription = "根据设备是否正在充电判断是否允许触发",
    iconRes = R.drawable.rounded_battery_android_frame_full_24,
    stateName = "充电",
    stateReader = { it.chargingState }
)

class ScreenStateConstraintModule : BooleanStateConstraintModule(
    moduleId = "vflow.constraint.screen_state",
    moduleName = "屏幕状态约束",
    moduleDescription = "根据亮屏或息屏判断是否允许触发",
    iconRes = R.drawable.rounded_fullscreen_portrait_24,
    stateName = "亮屏",
    stateReader = { it.screenOn }
)

abstract class BooleanStateConstraintModule(
    private val moduleId: String,
    private val moduleName: String,
    private val moduleDescription: String,
    private val iconRes: Int,
    private val stateName: String,
    private val stateReader: (ConstraintEvaluationContext) -> Boolean?
) : BaseConstraintModule() {
    companion object {
        const val EXPECT_TRUE = "true"
        const val EXPECT_FALSE = "false"
    }

    override val id = moduleId
    override val metadata = ActionMetadata(
        name = moduleName,
        description = moduleDescription,
        iconRes = iconRes,
        category = CATEGORY_CONSTRAINT_LABEL,
        categoryId = "constraint"
    )

    override fun getInputs() = listOf(
        InputDefinition(
            id = "expected",
            name = "状态",
            staticType = ParameterType.ENUM,
            defaultValue = EXPECT_TRUE,
            options = listOf(EXPECT_TRUE, EXPECT_FALSE),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
    )

    override suspend fun evaluateConstraint(
        context: ConstraintEvaluationContext,
        step: ActionStep
    ): ConstraintResult {
        val actual = stateReader(context) ?: return ConstraintResult.blocked("无法读取${stateName}状态")
        val expected = (step.parameters["expected"] as? String ?: EXPECT_TRUE) == EXPECT_TRUE
        return if (actual == expected) ConstraintResult.allowed() else ConstraintResult.blocked("${stateName}状态不满足约束")
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val expected = (step.parameters["expected"] as? String ?: EXPECT_TRUE) == EXPECT_TRUE
        return if (expected) stateName else "非$stateName"
    }
}

class NetworkStateConstraintModule : BaseConstraintModule() {
    companion object {
        const val MODE_IS = "is"
        const val MODE_NOT_IS = "not_is"
    }

    override val id = "vflow.constraint.network_state"
    override val metadata = ActionMetadata(
        name = "网络状态约束",
        description = "根据当前网络连接状态判断是否允许触发",
        iconRes = R.drawable.rounded_android_wifi_3_bar_24,
        category = CATEGORY_CONSTRAINT_LABEL,
        categoryId = "constraint"
    )

    override fun getInputs() = listOf(
        InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_IS,
            options = listOf(MODE_IS, MODE_NOT_IS),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "network_state",
            name = "网络状态",
            staticType = ParameterType.ENUM,
            defaultValue = ConstraintEvaluationContext.NETWORK_WIFI,
            options = listOf(
                ConstraintEvaluationContext.NETWORK_WIFI,
                ConstraintEvaluationContext.NETWORK_MOBILE,
                ConstraintEvaluationContext.NETWORK_NONE,
                ConstraintEvaluationContext.NETWORK_OTHER
            ),
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        )
    )

    override suspend fun evaluateConstraint(
        context: ConstraintEvaluationContext,
        step: ActionStep
    ): ConstraintResult {
        val actual = context.networkState ?: ConstraintEvaluationContext.NETWORK_NONE
        val expected = step.parameters["network_state"] as? String ?: ConstraintEvaluationContext.NETWORK_WIFI
        val matches = actual == expected
        val mode = step.parameters["mode"] as? String ?: MODE_IS
        val allowed = if (mode == MODE_NOT_IS) !matches else matches
        return if (allowed) ConstraintResult.allowed() else ConstraintResult.blocked("网络状态不满足约束")
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val expected = step.parameters["network_state"]?.toString().orEmpty()
        val mode = step.parameters["mode"] as? String ?: MODE_IS
        return if (mode == MODE_NOT_IS) "网络不是 $expected" else "网络是 $expected"
    }
}
