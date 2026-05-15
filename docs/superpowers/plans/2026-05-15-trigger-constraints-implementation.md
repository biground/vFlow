# 触发器级约束条件实施计划

> **给 AI 智能体工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 为自动触发器增加可编辑、可扩展、可评估的触发器级约束条件。

**架构：** 约束作为挂在触发器 `ActionStep.constraints` 上的模块实例保存，复用模块参数和摘要能力，但通过 `ConstraintModule.evaluateConstraint()` 而不是 `WorkflowExecutor` 执行。触发器命中前统一由 `TriggerConstraintEvaluator` 拦截，位置触发器额外用低成本约束决定是否维持主动网络/GPS 定位。

**技术栈：** Kotlin、Android View/XML、Gson、JUnit、Gradle。

---

## 文件结构

- `app/src/main/java/com/chaomixian/vflow/core/workflow/model/ActionStep.kt`：增加 `constraints` 字段。
- `app/src/main/java/com/chaomixian/vflow/core/workflow/WorkflowManager.kt`：读取持久化 workflow JSON 时递归解析约束。
- `app/src/main/java/com/chaomixian/vflow/api/model/WorkflowModels.kt`、`app/src/main/java/com/chaomixian/vflow/api/handler/ImportExportHandler.kt`：API 和导入路径保留约束。
- `app/src/main/java/com/chaomixian/vflow/core/workflow/constraints/`：新增约束接口、结果、评估上下文和评估器。
- `app/src/main/java/com/chaomixian/vflow/core/workflow/module/constraints/`：新增时间段、星期、全局变量、充电、屏幕、网络约束模块。
- `app/src/main/java/com/chaomixian/vflow/core/module/ModuleCategories.kt`、`app/src/main/java/com/chaomixian/vflow/core/workflow/module/ModuleRegistry.kt`：注册约束分类和内置约束模块。
- `app/src/main/java/com/chaomixian/vflow/core/workflow/TriggerExecutionCoordinator.kt`：在执行工作流前评估最终约束。
- `app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/LocationTriggerHandler.kt`：用低成本约束暂停/恢复主动定位。
- `app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/ActionPickerSheet.kt`、`ActionEditorSheet.kt`、`sheet_action_editor.xml`：约束模块选择、约束折叠区、约束编辑。
- `app/src/test/java/com/chaomixian/vflow/core/workflow/constraints/`：约束评估器和模块测试。
- `app/src/test/java/com/chaomixian/vflow/core/workflow/ActionStepConstraintsSerializationTest.kt`：兼容性测试。

## 任务 1：数据模型与序列化

**文件：**
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/model/ActionStep.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/WorkflowManager.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/api/model/WorkflowModels.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/api/handler/ImportExportHandler.kt`
- 测试：`app/src/test/java/com/chaomixian/vflow/core/workflow/ActionStepConstraintsSerializationTest.kt`

- [ ] **步骤 1：编写失败测试**

测试覆盖旧 JSON 缺省约束、触发器约束递归解析、API DTO 转换保留约束。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.core.workflow.ActionStepConstraintsSerializationTest'`
预期：FAIL，原因是 `ActionStep.constraints` 或解析逻辑不存在。

- [ ] **步骤 3：实现最小数据模型和解析**

给 `ActionStep` 添加默认空列表；手写 JSON 解析和 API DTO 递归读写 `constraints`。

- [ ] **步骤 4：运行测试验证通过**

运行同上，预期 PASS。

## 任务 2：约束接口、评估器和内置模块

**文件：**
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/constraints/ConstraintModule.kt`
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/constraints/ConstraintResult.kt`
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/constraints/ConstraintEvaluationContext.kt`
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/constraints/TriggerConstraintEvaluator.kt`
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/constraints/BuiltInConstraintModules.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/core/module/ModuleCategories.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/ModuleRegistry.kt`
- 测试：`app/src/test/java/com/chaomixian/vflow/core/workflow/constraints/TriggerConstraintEvaluatorTest.kt`
- 测试：`app/src/test/java/com/chaomixian/vflow/core/workflow/module/constraints/BuiltInConstraintModulesTest.kt`

- [ ] **步骤 1：编写失败测试**

测试 AND 语义、禁用约束跳过、未知约束不允许触发、时间段/星期/全局变量约束。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.core.workflow.constraints.*' --tests 'com.chaomixian.vflow.core.workflow.module.constraints.*'`
预期：FAIL，原因是约束接口和模块不存在。

- [ ] **步骤 3：实现评估器和模块**

实现 `LOW/MEDIUM/HIGH` 成本标记，第一版模块全部为低成本或安全中成本；模块执行函数返回不支持普通执行的 Failure，避免被主工作流误用。

- [ ] **步骤 4：运行测试验证通过**

运行同上，预期 PASS。

## 任务 3：触发器执行拦截与位置主动监听门控

**文件：**
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/TriggerExecutionCoordinator.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/LocationTriggerHandler.kt`
- 测试：`app/src/test/java/com/chaomixian/vflow/core/workflow/constraints/TriggerConstraintEvaluatorTest.kt`

- [ ] **步骤 1：编写失败测试**

测试约束不满足时评估器返回 blocked，未知约束 blocked，低成本约束筛选可被位置触发器复用。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.core.workflow.constraints.TriggerConstraintEvaluatorTest'`
预期：FAIL，缺少触发拦截辅助 API。

- [ ] **步骤 3：实现触发拦截**

在 `executeTrigger()` 权限恢复通过后、`WorkflowExecutor.execute()` 前评估约束；blocked 时记录日志并返回 false。

- [ ] **步骤 4：实现位置门控**

在位置 handler 切换到网络/GPS 前检查低成本约束；不满足时保持/回退被动监听，并在时间类约束变化后可通过后续事件恢复主动策略。

- [ ] **步骤 5：运行测试验证通过**

运行同任务 3 步骤 2，预期 PASS。

## 任务 4：编辑器 UI

**文件：**
- 修改：`app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/ActionPickerSheet.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt`
- 修改：`app/src/main/res/layout/sheet_action_editor.xml`
- 修改：`app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/ActionStepAdapter.kt`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-en/strings.xml`
- 测试：`app/src/test/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorUiModelBuilderTest.kt`

- [ ] **步骤 1：编写失败测试**

测试触发器 step 的约束数量能被 UI helper 识别为摘要文本，约束模块 picker 只显示 `constraint` 分类。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.ui.workflow_editor.ActionEditorUiModelBuilderTest'`
预期：FAIL，缺少约束 UI 模型/helper。

- [ ] **步骤 3：实现约束 UI**

给 `ActionPickerSheet` 增加 picker mode；在 `ActionEditorSheet` 新增约束折叠区、添加/编辑/删除/禁用约束；保存时把约束写回当前触发器 step。

- [ ] **步骤 4：运行测试验证通过**

运行同上，预期 PASS。

## 任务 5：整体验证与提交

**文件：**
- 修改：相关实现和测试文件。

- [ ] **步骤 1：自审 diff**

运行：`git diff --check` 和 `git diff --stat`，确认没有格式错误、无关文件未混入。

- [ ] **步骤 2：运行窄测试**

运行任务 1-4 中的所有窄测试，预期 PASS。

- [ ] **步骤 3：运行应用单元测试**

运行：`./gradlew :app:testDebugUnitTest`
预期 PASS。

- [ ] **步骤 4：按项目规则打包安装**

读取 `skills/vflow-development/workflows/package-and-install.md`，按其中 JDK、Gradle、APK、ADB 命令执行。

- [ ] **步骤 5：提交**

只暂存本次约束功能相关文件。提交信息：`feat(触发器约束): 新增触发器级约束条件`
