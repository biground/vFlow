// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.module.triggers.diagnostics.TriggerDiagnosticRunner
import com.chaomixian.vflow.core.workflow.model.ActionStepExecutionSettings
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.common.ModuleDetailDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

/**
 * 模块参数编辑器底部表单。
 * UI 由模块定义驱动，支持通用输入类型和模块自定义UI。
 * 支持配置异常处理策略。
 */
class ActionEditorSheet : BottomSheetDialogFragment() {
    private lateinit var module: ActionModule
    private var existingStep: ActionStep? = null
    private var focusedInputId: String? = null
    private var allSteps: ArrayList<ActionStep>? = null
    var onSave: ((ActionStep) -> Unit)? = null
    var onMagicVariableRequested: ((inputId: String, currentParameters: Map<String, Any?>) -> Unit)? = null
    var onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)? = null
    private val inputViews = mutableMapOf<String, View>()
    private var customEditorHolder: CustomEditorViewHolder? = null
    private val currentParameters = ActionEditorSessionState()
    private var onPickerRequested: ((inputDef: InputDefinition) -> Unit)? = null
    private val constraintSteps = mutableListOf<ActionStep>()

    // 引用容器视图
    private var customUiCard: MaterialCardView? = null
    private var customUiContainer: LinearLayout? = null
    private var genericInputsCard: MaterialCardView? = null
    private var genericInputsContainer: LinearLayout? = null
    private var editorActionsCard: MaterialCardView? = null
    private var editorActionsContainer: LinearLayout? = null
    private var triggerConstraintsCard: MaterialCardView? = null
    private var triggerConstraintsContainer: LinearLayout? = null

    // 异常处理 UI 组件
    private var errorSettingsContent: LinearLayout? = null
    private var errorPolicyGroup: RadioGroup? = null
    private var retryCountSlider: Slider? = null
    private var retryIntervalSlider: Slider? = null

    companion object {
        // 单次编辑会话的展开状态缓存
        private val expandedSections = mutableMapOf<String, Boolean>()

        /** 创建 ActionEditorSheet 实例。 */
        fun newInstance(
            module: ActionModule,
            existingStep: ActionStep?,
            focusedInputId: String?,
            allSteps: List<ActionStep>? = null
        ): ActionEditorSheet {
            return ActionEditorSheet().apply {
                arguments = Bundle().apply {
                    putString("moduleId", module.id)
                    putParcelable("existingStep", existingStep)
                    putString("focusedInputId", focusedInputId)
                    allSteps?.let { putParcelableArrayList("allSteps", ArrayList(it)) }
                }
            }
        }
    }

    /** 初始化核心数据。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleId = arguments?.getString("moduleId")
        module = moduleId?.let { ModuleRegistry.getModule(it) } ?: return dismiss()
        existingStep = arguments?.getParcelableCompat("existingStep")
        focusedInputId = arguments?.getString("focusedInputId")
        allSteps = arguments?.getParcelableArrayListCompat("allSteps")
        if (isTriggerModule()) {
            constraintSteps.addAll(existingStep?.constraints.orEmpty())
        }

        // 初始化参数，首先使用模块定义的默认值
        currentParameters.initializeDefaults(module.getInputs())
        // 然后用步骤已有的参数覆盖默认值
        existingStep?.parameters?.let { currentParameters.putAll(it) }
    }

    fun setOnPickerRequestedListener(listener: (InputDefinition) -> Unit) {
        onPickerRequested = listener
    }

    private fun dispatchPickerRequest(inputDefinition: InputDefinition) {
        onPickerRequested?.invoke(inputDefinition)
    }

    /** 创建视图并构建UI。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_action_editor, container, false)
        val titleTextView = view.findViewById<TextView>(R.id.text_view_bottom_sheet_title)
        val infoButton = view.findViewById<ImageButton>(R.id.button_module_info)
        val saveButton = view.findViewById<Button>(R.id.button_save)
        val testTriggerButton = view.findViewById<Button>(R.id.button_test_trigger)

        // 绑定视图容器
        customUiCard = view.findViewById(R.id.card_custom_ui)
        customUiContainer = view.findViewById(R.id.container_custom_ui)
        genericInputsCard = view.findViewById(R.id.card_generic_inputs)
        genericInputsContainer = view.findViewById(R.id.container_generic_inputs)
        editorActionsCard = view.findViewById(R.id.card_editor_actions)
        editorActionsContainer = view.findViewById(R.id.container_editor_actions)
        triggerConstraintsCard = view.findViewById(R.id.card_trigger_constraints)
        triggerConstraintsContainer = view.findViewById(R.id.container_trigger_constraints)

        // 绑定错误处理容器
        errorSettingsContent = view.findViewById(R.id.container_execution_settings_content)
        val errorHeader = view.findViewById<View>(R.id.header_execution_settings)
        val errorArrow = view.findViewById<View>(R.id.arrow_execution_settings)

        // 错误处理区域的折叠/展开逻辑
        errorHeader.setOnClickListener {
            val isVisible = errorSettingsContent?.isVisible == true
            errorSettingsContent?.isVisible = !isVisible
            errorArrow.animate().rotation(if (!isVisible) 180f else 0f).setDuration(200).start()
        }

        // 设置标题
        val focusedInputDef = module.getInputs().find { it.id == focusedInputId }
        titleTextView.text = if (focusedInputId != null && focusedInputDef != null) {
            getString(R.string.title_edit_target, focusedInputDef.name)
        } else {
            val localizedName = module.metadata.getLocalizedName(requireContext())
            getString(R.string.title_edit_target, localizedName)
        }
        infoButton.setOnClickListener {
            ModuleDetailDialog.show(requireContext(), module)
        }

        buildUi()
        renderTriggerConstraintsSection()
        buildErrorHandlingUi() // 构建错误处理 UI
        testTriggerButton.isVisible = TriggerDiagnosticRunner.supportsCompleteDiagnostic(module.id)
        testTriggerButton.setOnClickListener {
            diagnoseCurrentTrigger()
        }

        saveButton.setOnClickListener {
            readParametersFromUi()
            syncExecutionSettingsFromUi()

            val finalParams = existingStep?.parameters?.toMutableMap() ?: mutableMapOf()
            finalParams.putAll(currentParameters.snapshot())
            val stepForValidation = ActionStep(moduleId = module.id, parameters = finalParams, id = existingStep?.id ?: "")
                .withConstraints(currentConstraintStepsForSave())
            // 调用 validate 方法进行验证
            val validationResult = module.validate(stepForValidation, allSteps ?: emptyList())
            if (validationResult.isValid) {
                onSave?.invoke(
                    currentParameters.toActionStep(module.id, existingStep?.id ?: "")
                        .withConstraints(currentConstraintStepsForSave())
                )
                dismiss()
            } else {
                Toast.makeText(context, validationResult.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        return view
    }

    private fun isTriggerModule(): Boolean {
        return module.metadata.getResolvedCategoryId() == ModuleCategories.TRIGGER ||
            module.id.startsWith("vflow.trigger.")
    }

    private fun currentConstraintStepsForSave(): List<ActionStep> {
        return if (isTriggerModule()) constraintSteps.toList() else emptyList()
    }

    private fun renderTriggerConstraintsSection() {
        val card = triggerConstraintsCard ?: return
        val container = triggerConstraintsContainer ?: return
        card.isVisible = isTriggerModule()
        if (!isTriggerModule()) return

        val context = requireContext()
        val density = resources.displayMetrics.density
        container.removeAllViews()

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(context).apply {
            text = getString(R.string.trigger_constraints_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        textColumn.addView(TextView(context).apply {
            text = TriggerConstraintUiFormatter.countText(
                count = constraintSteps.size,
                emptyText = getString(R.string.trigger_constraints_empty),
                countText = { getString(R.string.trigger_constraints_count, it) }
            )
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        header.addView(textColumn)
        header.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.trigger_constraints_add)
            icon = ContextCompat.getDrawable(context, R.drawable.rounded_add_24)
            setOnClickListener { showConstraintPicker() }
        })
        container.addView(header)

        constraintSteps.forEachIndexed { index, step ->
            container.addView(createConstraintRow(index, step), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            })
        }
    }

    private fun createConstraintRow(index: Int, step: ActionStep): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val module = ModuleRegistry.getModule(step.moduleId)
        val moduleName = module?.metadata?.getLocalizedName(context) ?: step.moduleId
        val moduleSummary = module?.getSummary(context, step)?.takeIf { it.isNotBlank() }
        val stateSuffix = if (step.isDisabled) " · ${getString(R.string.trigger_constraints_disabled_suffix)}" else ""

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use {
                it.getDrawable(0)
            }
            setPadding(
                (12 * density).toInt(),
                (10 * density).toInt(),
                (4 * density).toInt(),
                (10 * density).toInt()
            )

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textColumn.addView(TextView(context).apply {
                text = moduleName
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            textColumn.addView(TextView(context).apply {
                text = (moduleSummary ?: moduleName).toString() + stateSuffix
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            val enabledSwitch = MaterialSwitch(context).apply {
                isChecked = !step.isDisabled
                setOnCheckedChangeListener { _, isChecked ->
                    constraintSteps[index] = constraintSteps[index].copy(isDisabled = !isChecked)
                    renderTriggerConstraintsSection()
                }
            }
            val deleteButton = ImageButton(context).apply {
                setImageResource(R.drawable.rounded_delete_24)
                background = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).use {
                    it.getDrawable(0)
                }
                contentDescription = getString(R.string.common_delete)
                setOnClickListener { confirmDeleteConstraint(index, moduleName) }
            }

            addView(textColumn)
            addView(enabledSwitch)
            addView(deleteButton, LinearLayout.LayoutParams(
                (40 * density).toInt(),
                (40 * density).toInt()
            ))
            setOnClickListener { editConstraint(index, module, step) }
        }
    }

    private fun showConstraintPicker() {
        readParametersFromUi()
        val picker = ActionPickerSheet().apply {
            arguments = Bundle().apply {
                putString(ActionPickerSheet.ARG_PICKER_MODE, ActionPickerSheet.MODE_CONSTRAINT)
            }
            onActionSelected = { selectedModule ->
                editConstraint(index = -1, module = selectedModule, step = null)
            }
        }
        picker.show(childFragmentManager, "ConstraintPicker")
    }

    private fun editConstraint(index: Int, module: ActionModule?, step: ActionStep?) {
        val targetModule = module ?: ModuleRegistry.getModule(step?.moduleId ?: return) ?: return
        val editor = newInstance(targetModule, step, null, allSteps).apply {
            onSave = { savedStep ->
                val normalizedStep = savedStep.withConstraints(emptyList())
                if (index >= 0) {
                    constraintSteps[index] = normalizedStep
                } else {
                    constraintSteps.add(normalizedStep)
                }
                renderTriggerConstraintsSection()
            }
            onMagicVariableRequested = { _, _ -> }
            onStartActivityForResult = this@ActionEditorSheet.onStartActivityForResult
            setOnPickerRequestedListener { inputDef ->
                this@ActionEditorSheet.dispatchPickerRequest(inputDef)
            }
        }
        editor.show(childFragmentManager, "ConstraintEditor")
    }

    private fun confirmDeleteConstraint(index: Int, moduleName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.trigger_constraints_delete_title)
            .setMessage(getString(R.string.trigger_constraints_delete_message, moduleName))
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                if (index in constraintSteps.indices) {
                    constraintSteps.removeAt(index)
                    renderTriggerConstraintsSection()
                }
            }
            .show()
    }

    private fun diagnoseCurrentTrigger() {
        readParametersFromUi()
        syncExecutionSettingsFromUi()
        val finalParams = existingStep?.parameters?.toMutableMap() ?: mutableMapOf()
        finalParams.putAll(currentParameters.snapshot())
        val stepForDiagnostic = ActionStep(
            moduleId = module.id,
            parameters = finalParams,
            id = existingStep?.id ?: ""
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val result = TriggerDiagnosticRunner.diagnose(
                context = requireContext(),
                module = module,
                step = stepForDiagnostic,
                allSteps = allSteps ?: emptyList()
            )
            if (!isAdded) return@launch
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(result.title)
                .setMessage(result.message)
                .setPositiveButton(R.string.common_ok, null)
                .show()
        }
    }

    /**
     * 构建异常处理策略的 UI。
     * 包含一个 RadioGroup 选择策略，以及重试相关的 Slider。
     */
    private fun buildErrorHandlingUi() {
        val context = requireContext()
        errorSettingsContent?.removeAllViews()

        val radioGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val rbStop = RadioButton(context).apply {
            text = getString(R.string.editor_error_policy_stop)
            tag = ActionStepExecutionSettings.POLICY_STOP
        }
        val rbSkip = RadioButton(context).apply {
            text = getString(R.string.editor_error_policy_skip)
            tag = ActionStepExecutionSettings.POLICY_SKIP
        }
        val rbRetry = RadioButton(context).apply {
            text = getString(R.string.editor_error_policy_retry)
            tag = ActionStepExecutionSettings.POLICY_RETRY
        }

        radioGroup.addView(rbStop)
        radioGroup.addView(rbSkip)
        radioGroup.addView(rbRetry)

        val retryContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
            visibility = View.GONE
        }

        // 恢复状态
        val executionSettings = currentParameters.getExecutionSettings()
        val currentPolicy = executionSettings.policy
        val currentRetryCount = executionSettings.retryCount.toFloat()
        val currentRetryInterval = executionSettings.retryIntervalMillis.toFloat()

        // --- 重试次数 ---
        val sliderRetryCount = StandardControlFactory.createSliderWithLabel(
            context = context,
            label = getString(R.string.editor_retry_count),
            valueFrom = 1f,
            valueTo = 10f,
            stepSize = 1f,
            currentValue = currentRetryCount,
            valueFormatter = { getString(R.string.editor_retry_times, it.toInt()) }
        )
        retryContainer.addView(sliderRetryCount)

        // --- 重试间隔 ---
        val sliderRetryInterval = StandardControlFactory.createSliderWithLabel(
            context = context,
            label = getString(R.string.editor_retry_interval),
            valueFrom = 100f,
            valueTo = 5000f,
            stepSize = 100f,
            currentValue = currentRetryInterval,
            valueFormatter = { "${it.toLong()} ms" }
        ).apply {
            // 增加间距区分上下两个滑块
            (layoutParams as LinearLayout.LayoutParams).topMargin = (24 * resources.displayMetrics.density).toInt()
        }
        retryContainer.addView(sliderRetryInterval)

        when (currentPolicy) {
            ActionStepExecutionSettings.POLICY_SKIP -> rbSkip.isChecked = true
            ActionStepExecutionSettings.POLICY_RETRY -> rbRetry.isChecked = true
            else -> rbStop.isChecked = true
        }
        retryContainer.isVisible = (currentPolicy == ActionStepExecutionSettings.POLICY_RETRY)

        // 获取滑块引用
        val sliderRetryCountView = sliderRetryCount.getChildAt(1) as Slider
        val sliderRetryIntervalView = sliderRetryInterval.getChildAt(1) as Slider

        // 监听器
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            retryContainer.isVisible = (checkedId == rbRetry.id)
        }

        // 保存引用
        this.errorPolicyGroup = radioGroup
        this.retryCountSlider = sliderRetryCountView
        this.retryIntervalSlider = sliderRetryIntervalView

        errorSettingsContent?.addView(radioGroup)
        errorSettingsContent?.addView(retryContainer)
    }

    private fun syncExecutionSettingsFromUi() {
        currentParameters.setExecutionSettings(readExecutionSettingsFromUi())
    }

    private fun readExecutionSettingsFromUi(): ActionStepExecutionSettings {
        val selectedId = errorPolicyGroup?.checkedRadioButtonId
        val view = errorPolicyGroup?.findViewById<View>(selectedId ?: -1)
        val policy = view?.tag as? String ?: ActionStepExecutionSettings.POLICY_STOP

        return ActionStepExecutionSettings(
            policy = policy,
            retryCount = retryCountSlider?.value?.toInt() ?: ActionStepExecutionSettings.DEFAULT_RETRY_COUNT,
            retryIntervalMillis = retryIntervalSlider?.value?.toLong()
                ?: ActionStepExecutionSettings.DEFAULT_RETRY_INTERVAL_MS
        )
    }

    private fun requestMagicVariableSelection(inputId: String) {
        readParametersFromUi()
        onMagicVariableRequested?.invoke(inputId, currentParameters.asMap())
    }

    private fun mutateSession(
        readUiFirst: Boolean = false,
        rebuildMode: RebuildMode = RebuildMode.IMMEDIATE,
        mutation: ActionEditorSessionState.() -> Unit
    ) {
        if (readUiFirst) {
            readParametersFromUi()
        }
        mutation(currentParameters)
        when (rebuildMode) {
            RebuildMode.NONE -> Unit
            RebuildMode.IMMEDIATE -> buildUi()
            RebuildMode.POST -> postBuildUi()
        }
    }

    private fun applyParameterUpdate(
        inputId: String,
        updatedValue: Any?,
        readUiFirst: Boolean,
        rebuildMode: RebuildMode
    ) {
        mutateSession(readUiFirst = readUiFirst, rebuildMode = rebuildMode) {
            val updatedStep = toActionStep(module.id, existingStep?.id ?: "").copy(
                parameters = snapshot().apply {
                    this[inputId] = updatedValue
                }
            )
            replaceAll(module.onParameterUpdated(updatedStep, inputId, updatedValue))
        }
    }

    private fun postBuildUi() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                buildUi()
            } catch (_: Exception) {
                // 忽略视图已销毁的情况
            }
        }
    }

    private enum class RebuildMode {
        NONE,
        IMMEDIATE,
        POST
    }

    /**
     * 构建UI的逻辑。
     */
    private fun buildUi() {
        val uiModel = computeUiModel()
        applyUiModelCorrections(uiModel)
        renderUiModel(uiModel)
    }

    private fun computeUiModel(): EditorUiModel {
        return ActionEditorUiModelBuilder.build(
            module = module,
            sessionState = currentParameters,
            allSteps = allSteps
        )
    }

    private fun applyUiModelCorrections(uiModel: EditorUiModel) {
        val correctedParameters = uiModel.effectiveParameters
        if (currentParameters.asMap() != correctedParameters) {
            currentParameters.replaceAll(correctedParameters)
        }
    }

    private fun renderUiModel(uiModel: EditorUiModel) {
        customUiContainer?.removeAllViews()
        genericInputsContainer?.removeAllViews()
        editorActionsContainer?.removeAllViews()
        inputViews.clear()
        customEditorHolder = null

        if (uiModel.showCustomUi) {
            val uiProvider = uiModel.uiProvider ?: return
            customEditorHolder = uiProvider.createEditor(
                context = requireContext(),
                parent = customUiContainer!!,
                currentParameters = uiModel.effectiveParameters,
                onParametersChanged = { readParametersFromUi() },
                onMagicVariableRequested = ::requestMagicVariableSelection,
                allSteps = allSteps,
                onStartActivityForResult = onStartActivityForResult
            )
            customUiContainer?.addView(customEditorHolder!!.view)
        }

        customUiCard?.isVisible = uiModel.showCustomUi

        uiModel.genericInputsSection.fields.forEach { fieldModel ->
            val inputView = createViewForInputDefinition(fieldModel)
            genericInputsContainer?.addView(inputView)
            inputViews[fieldModel.inputDefinition.id] = inputView
        }

        if (uiModel.advancedInputsSection.isVisible) {
            if (uiModel.shouldShowAdvancedDivider) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply {
                        setMargins(0, 32, 0, 16)
                    }
                    setBackgroundColor(requireContext().getColor(android.R.color.darker_gray))
                    alpha = 0.2f
                }
                genericInputsContainer?.addView(divider)
            }

            val advancedSection = createAdvancedSection(uiModel.advancedInputsSection.fields)
            genericInputsContainer?.addView(advancedSection)
        }

        genericInputsCard?.isVisible = uiModel.showsAnyGenericInputs

        if (uiModel.editorActionsSection.isVisible) {
            editorActionsContainer?.addView(createEditorActionsSection(uiModel.editorActionsSection.actions))
        }
        editorActionsCard?.isVisible = uiModel.editorActionsSection.isVisible
    }

    private fun createEditorActionsSection(actions: List<EditorAction>): View {
        val context = requireContext()
        val density = resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
            actions.forEachIndexed { index, action ->
                addView(
                    MaterialButton(
                        context,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = action.getLocalizedLabel(context)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (index > 0) topMargin = (12 * density).toInt()
                        }
                        setOnClickListener { action.onClick(context) }
                    }
                )
            }
        }
    }

    /**
     * 动态创建"更多设置"折叠区域。
     */
    private fun createAdvancedSection(fields: List<EditorFieldModel>): View {
        val context = requireContext()
        val density = resources.displayMetrics.density

        // 为每个模块生成唯一的 key 来保存展开状态（单次会话）
        val stateKey = "advanced_section_expanded_${module.id}"
        var isExpanded = expandedSections[stateKey] ?: false

        // 根容器
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // 标题栏 (点击区域)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 使用系统可点击背景
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val typedArray = context.obtainStyledAttributes(attrs)
            background = typedArray.getDrawable(0)
            typedArray.recycle()

            setPadding(
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt(),
                (12 * density).toInt()
            )
        }

        // 图标
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.rounded_settings_24)
            layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt())
            val color = com.google.android.material.color.MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.graphics.Color.GRAY
            )
            setColorFilter(color)
            alpha = 0.7f
        }

        // 文字
        val title = TextView(context).apply {
            text = getString(R.string.editor_more_settings)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * density).toInt()
            }
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // 箭头
        val arrow = ImageView(context).apply {
            setImageResource(R.drawable.rounded_arrow_drop_down_24)
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt())
            val color = com.google.android.material.color.MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.graphics.Color.GRAY
            )
            setColorFilter(color)
            alpha = 0.7f
        }

        headerLayout.addView(icon)
        headerLayout.addView(title)
        headerLayout.addView(arrow)

        // 内容容器 (默认隐藏)
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        // 填充参数到内容容器
        fields.forEach { fieldModel ->
            val inputView = createViewForInputDefinition(fieldModel)
            contentLayout.addView(inputView)
            // 注册到 inputViews，这样 readParametersFromUi 就能自动读取它们
            inputViews[fieldModel.inputDefinition.id] = inputView
        }

        // 设置点击事件
        contentLayout.isVisible = isExpanded
        arrow.rotation = if (isExpanded) 180f else 0f

        headerLayout.setOnClickListener {
            isExpanded = !isExpanded
            contentLayout.isVisible = isExpanded
            arrow.animate()
                .rotation(if (isExpanded) 180f else 0f)
                .setDuration(200)
                .start()

            // 保存展开状态到内存（单次会话）
            expandedSections[stateKey] = isExpanded
        }

        rootLayout.addView(headerLayout)
        rootLayout.addView(contentLayout)
        return rootLayout
    }

    /**
     * 为输入参数创建视图。
     * 对于 CHIP_GROUP 风格，使用简化的布局（不带标签和魔法变量按钮）。
     * 对于 PICKER 类型，使用带选择器图标的输入框。
     * 对于其他风格，使用完整的参数输入行布局。
     */
    private fun createViewForInputDefinition(fieldModel: EditorFieldModel): View {
        val inputDef = fieldModel.inputDefinition
        val currentValue = fieldModel.currentValue

        // CHIP_GROUP 风格不需要魔法变量按钮，使用简化布局
        if (inputDef.inputStyle == InputStyle.CHIP_GROUP) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.row_editor_input, null, false)
            row.findViewById<TextView>(R.id.input_name).text = inputDef.getLocalizedName(requireContext())
            row.findViewById<ImageButton>(R.id.button_magic_variable).visibility = View.GONE

            val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
            valueContainer.removeAllViews()

            val chipGroupView = StandardControlFactory.createChipGroup(
                context = requireContext(),
                options = inputDef.options,
                currentValue = currentValue as? String,
                onSelectionChanged = { selectedItem ->
                    if (currentParameters[inputDef.id] != selectedItem) {
                        applyParameterUpdate(
                            inputId = inputDef.id,
                            updatedValue = selectedItem,
                            readUiFirst = true,
                            rebuildMode = RebuildMode.POST
                        )
                    }
                },
                optionsStringRes = if (inputDef.optionsStringRes.isNotEmpty()) inputDef.optionsStringRes else null
            )
            valueContainer.addView(chipGroupView)
            row.tag = inputDef.id
            return row
        }

        // PICKER 类型使用带选择器图标的输入框
        if (inputDef.pickerType != PickerType.NONE && !inputDef.supportsRichText) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.row_editor_input, null, false)
            row.findViewById<TextView>(R.id.input_name).text = inputDef.getLocalizedName(requireContext())
            row.findViewById<ImageButton>(R.id.button_magic_variable).visibility = View.GONE

            val valueContainer = row.findViewById<ViewGroup>(R.id.input_value_container)
            valueContainer.removeAllViews()

            val pickerInputView = StandardControlFactory.createPickerInput(
                context = requireContext(),
                currentValue = currentValue,
                pickerType = inputDef.pickerType,
                hint = inputDef.getLocalizedHint(requireContext()),
                onPickerClicked = {
                    dispatchPickerRequest(inputDef)
                }
            )
            valueContainer.addView(pickerInputView)
            row.tag = inputDef.id
            return row
        }

        // 其他风格使用标准布局
        return StandardControlFactory.createParameterInputRow(
            context = requireContext(),
            inputDef = inputDef,
            currentValue = currentValue,
            allSteps = allSteps,
            onMagicVariableRequested = ::requestMagicVariableSelection,
            onPickerRequested = ::dispatchPickerRequest,
            onEnumItemSelected = { selectedItem ->
                // 防止重复触发：只在值真正改变时才处理
                if (currentParameters[inputDef.id] != selectedItem) {
                    applyParameterUpdate(
                        inputId = inputDef.id,
                        updatedValue = selectedItem,
                        readUiFirst = true,
                        rebuildMode = RebuildMode.POST
                    )
                }
            }
        ).also { row ->
            bindImmediateDynamicInputUpdates(row, inputDef)
        }
    }

    private fun bindImmediateDynamicInputUpdates(row: View, inputDef: InputDefinition) {
        if (inputDef.staticType != ParameterType.BOOLEAN && inputDef.inputStyle != InputStyle.SWITCH) {
            return
        }
        val toggle = findDescendantMaterialSwitch(row) ?: return
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (currentParameters[inputDef.id] == isChecked) {
                return@setOnCheckedChangeListener
            }
            applyParameterUpdate(
                inputId = inputDef.id,
                updatedValue = isChecked,
                readUiFirst = true,
                rebuildMode = RebuildMode.POST
            )
        }
    }

    private fun findDescendantMaterialSwitch(root: View): MaterialSwitch? {
        if (root is MaterialSwitch) {
            return root
        }
        if (root !is ViewGroup) {
            return null
        }
        for (index in 0 until root.childCount) {
            val match = findDescendantMaterialSwitch(root.getChildAt(index))
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun updateParametersAndRebuildUi(newParameters: Map<String, Any?>) {
        mutateSession {
            putAll(newParameters)
        }
    }

    private fun currentStepForUi(): ActionStep {
        return currentParameters.toActionStep(module.id)
    }

    private fun findDynamicInputDefinition(inputId: String): InputDefinition? {
        return module.getDynamicInputs(currentStepForUi(), allSteps).find { it.id == inputId }
    }

    private fun mergeCustomEditorParametersFromUi() {
        val uiProvider = module.uiProvider
        if (uiProvider != null && customEditorHolder != null && uiProvider.hasCustomEditor()) {
            currentParameters.putAll(uiProvider.readFromEditor(customEditorHolder!!))
        }
    }

    private fun readParametersFromUi() {
        mergeCustomEditorParametersFromUi()

        inputViews.forEach { (id, view) ->
            val inputDef = findDynamicInputDefinition(id)
            val resolvedInputDef = inputDef ?: return@forEach
            val readResult = ActionEditorViewStateReader.readParameterValue(
                view = view,
                inputDefinition = resolvedInputDef,
                currentValue = currentParameters[id]
            )
            if (!readResult.shouldUpdate) return@forEach
            currentParameters[id] = readResult.value
        }
    }

    /**
     * 更新输入框的变量。
     */
    fun updateInputWithVariable(inputId: String, variableReference: String) {
        val inputDef = findDynamicInputDefinition(inputId)
        val path = ParamPath.parse(inputId)
        val richTextView = ActionEditorRichTextLocator.findRichTextView(
            inputId = inputId,
            inputDefinition = inputDef,
            inputViews = inputViews,
            customEditorHolder = customEditorHolder
        )

        if (richTextView != null) {
            richTextView.insertVariablePill(variableReference)
            return
        }

        mutateSession {
            setPath(path, variableReference)
        }
    }

    /**
     * 清除输入框的变量。
     */
    fun clearInputVariable(inputId: String) {
        val path = ParamPath.parse(inputId)
        val topLevelDefaultValue = module.getInputs().find { it.id == path.rootId }?.defaultValue
        mutateSession {
            clearPath(path, topLevelDefaultValue)
        }
    }
}
