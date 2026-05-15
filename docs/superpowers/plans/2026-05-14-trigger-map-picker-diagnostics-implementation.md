# 触发器地图选点与诊断实施计划

> **给 AI 智能体工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 为位置触发器增加高德地图选点，为触发器编辑器增加只诊断不执行的测试入口，并在设置中加入位置触发器定位间隔。

**架构：** 地图选点作为位置触发器编辑器的可选增强，仍回填现有经纬度/半径/名称参数。触发器诊断通过独立诊断接口运行，不复用真实工作流执行协调器。定位间隔集中在偏好对象中，设置页写入后通知触发器服务重载。

**技术栈：** Kotlin、Android ViewBinding/View UI、Compose 设置页、Material Components、SharedPreferences、JUnit、Gradle Android。

---

## 文件结构

- 创建 `app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerPreferences.kt`：集中保存位置触发器定位间隔默认值、范围、规范化和读取。
- 创建 `app/src/test/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerPreferencesTest.kt`：验证间隔规范化。
- 创建 `app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/diagnostics/TriggerDiagnosticResult.kt`：诊断状态模型。
- 创建 `app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/diagnostics/TriggerDiagnosticRunner.kt`：通用权限/参数诊断与位置触发器专属诊断入口。
- 创建 `app/src/test/java/com/chaomixian/vflow/core/workflow/module/triggers/diagnostics/TriggerDiagnosticRunnerTest.kt`：验证位置参数诊断和不会暴露执行路径。
- 创建 `app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/AmapLocationPickerActivity.kt`：高德地图选点页面。
- 修改 `app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerHandler.kt`：使用设置中的主动定位间隔。
- 修改 `app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerUIProvider.kt` 与 `app/src/main/res/layout/partial_location_trigger_editor.xml`：增加地图选点按钮并处理回填。
- 修改 `app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt` 与 `app/src/main/res/layout/sheet_action_editor.xml`：触发器模式显示测试按钮并运行诊断。
- 修改 `app/src/main/java/com/chaomixian/vflow/ui/settings/SettingsScreen.kt`、`SettingsRoute.kt`、`SettingsViewModel.kt` 和字符串资源：新增定位间隔设置。
- 修改 `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`：加入高德 SDK 依赖、Key metadata 和选点 Activity。

## 任务 1：定位间隔偏好

**文件：**
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerPreferences.kt`
- 测试：`app/src/test/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerPreferencesTest.kt`

- [ ] **步骤 1：编写失败的测试**

测试 `normalizeIntervalMinutes(0)` 返回 1，`normalizeIntervalMinutes(99)` 返回 30，默认值为 5。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.core.workflow.module.triggers.LocationTriggerPreferencesTest'`
预期：FAIL，类型或方法不存在。

- [ ] **步骤 3：实现偏好对象**

实现常量 `DEFAULT_INTERVAL_MINUTES = 5`、`MIN_INTERVAL_MINUTES = 1`、`MAX_INTERVAL_MINUTES = 30`，以及 `normalizeIntervalMinutes()`、`getIntervalMinutes(context)`、`setIntervalMinutes(context, minutes)`、`getIntervalMillis(context)`。

- [ ] **步骤 4：运行测试验证通过**

运行同一步骤 2，预期 PASS。

## 任务 2：设置页接入定位间隔

**文件：**
- 修改：`app/src/main/java/com/chaomixian/vflow/ui/viewmodel/SettingsViewModel.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/ui/settings/SettingsScreen.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/ui/settings/SettingsRoute.kt`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-en/strings.xml`

- [ ] **步骤 1：扩展 UI state 和 actions**

增加 `locationUpdateIntervalMinutes: Int` 和 `onSetLocationUpdateIntervalMinutes: (Int) -> Unit`。

- [ ] **步骤 2：在 General 区域加入 Slider**

使用现有 `NativeSliderRow` 展示 1-30 分钟范围，值变更写入偏好。

- [ ] **步骤 3：设置变更后重载触发器**

在 `SettingsRoute` 调用 ViewModel 保存后，向 `TriggerService.ACTION_RELOAD_TRIGGERS` 发 `startService`，让位置监听重新注册。

## 任务 3：位置 handler 使用设置间隔

**文件：**
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/LocationTriggerHandler.kt`

- [ ] **步骤 1：替换硬编码主动定位间隔**

网络定位和 GPS 定位的 `minTimeMs` 读取 `LocationTriggerPreferences.getIntervalMillis(context)`。

- [ ] **步骤 2：保留被动监听**

`PASSIVE_PROVIDER` 仍使用 0，以便继续复用系统被动定位，不制造额外轮询。

## 任务 4：触发器诊断模型与位置诊断

**文件：**
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/diagnostics/TriggerDiagnosticResult.kt`
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/diagnostics/TriggerDiagnosticRunner.kt`
- 测试：`app/src/test/java/com/chaomixian/vflow/core/workflow/module/triggers/diagnostics/TriggerDiagnosticRunnerTest.kt`

- [ ] **步骤 1：编写失败的参数诊断测试**

测试非法纬度、非法经度、非正半径返回 `INVALID_CONFIG`。

- [ ] **步骤 2：实现诊断结果和纯参数诊断**

结果状态包含 `SUCCESS`、`NOT_MATCHED`、`MISSING_PERMISSION`、`INVALID_CONFIG`、`UNSUPPORTED`、`UNKNOWN`。

- [ ] **步骤 3：实现运行时诊断**

先校验参数，再检查模块所需权限。位置触发器读取最近位置并判断 `enter`；`exit` 对无历史状态返回限制说明。诊断不调用 `TriggerExecutionCoordinator` 或 `WorkflowExecutor`。

## 任务 5：编辑器测试按钮

**文件：**
- 修改：`app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/ActionEditorSheet.kt`
- 修改：`app/src/main/res/layout/sheet_action_editor.xml`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-en/strings.xml`

- [ ] **步骤 1：识别触发器编辑模式**

`ActionEditorSheet` 根据 `module.id.startsWith("vflow.trigger.")` 显示测试按钮。

- [ ] **步骤 2：运行诊断**

点击按钮读取当前 UI 参数，构造临时 `ActionStep`，调用 `TriggerDiagnosticRunner.diagnose()`，用对话框展示结果。

- [ ] **步骤 3：保证不保存参数**

测试按钮只读取当前表单状态，不调用 `onSave`，不 `dismiss()`。

## 任务 6：高德地图选点

**文件：**
- 创建：`app/src/main/java/com/chaomixian/vflow/ui/workflow_editor/AmapLocationPickerActivity.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/LocationTriggerUIProvider.kt`
- 修改：`app/src/main/res/layout/partial_location_trigger_editor.xml`
- 修改：`app/build.gradle.kts`
- 修改：`app/src/main/AndroidManifest.xml`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-en/strings.xml`

- [ ] **步骤 1：添加依赖和 Manifest**

添加高德地图 SDK 依赖和 `com.amap.api.v2.apikey` metadata，Key 从 `local.properties` 的 `AMAP_API_KEY` 进入 manifest placeholder。

- [ ] **步骤 2：实现选点 Activity**

Activity 包含 `MapView`，支持点击地图更新 marker 和 circle，确认后返回纬度、经度和名称。

- [ ] **步骤 3：编辑器回填**

位置触发器新增“地图选点”按钮，启动 Activity 并把结果写入已有输入框。

## 任务 7：验证、打包、安装

**文件：**
- 读取：`skills/vflow-development/workflows/package-and-install.md`

- [ ] **步骤 1：运行窄测试**

运行新增偏好和诊断测试。

- [ ] **步骤 2：运行 app 单元测试或可承受的相关测试**

优先运行 `./gradlew :app:testDebugUnitTest`。若现有 unrelated 测试失败，记录失败。

- [ ] **步骤 3：自审 diff**

运行 `git diff --stat` 和关键 diff 检查，确认未改动无关用户文件。

- [ ] **步骤 4：打包安装**

用 OpenJDK 17 执行 `./gradlew :app:assembleDebug`，安装 `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` 到当前连接设备，并用 `dumpsys package` 确认。

