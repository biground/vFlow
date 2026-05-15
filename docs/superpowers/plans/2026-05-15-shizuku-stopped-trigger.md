# Shizuku 不可用触发器实施计划

> **给 AI 智能体工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 新增一个自动触发器，在 Shizuku 曾经可用后变为不可用时尽快启动工作流。

**架构：** 触发器模块负责元数据、摘要和输出；Handler 负责注册 Shizuku Binder 事件并用 2 秒轮询兜底。核心转移语义放进纯 Kotlin 状态机，单元测试先验证它只在 `available -> unavailable` 时触发。

**技术栈：** Kotlin、Android Service、Shizuku API 13.1.5、JUnit4、Gradle Android unit tests。

---

### 任务 1：核心状态机测试与实现

**文件：**
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/ShizukuStoppedTriggerState.kt`
- 创建：`app/src/test/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/ShizukuStoppedTriggerStateTest.kt`

- [ ] **步骤 1：编写失败的测试**

```kotlin
@Test
fun `does not trigger when initial state is unavailable`() {
    val state = ShizukuStoppedTriggerState()

    assertFalse(state.update(isAvailable = false))
}
```

```kotlin
@Test
fun `triggers once when shizuku changes from available to unavailable`() {
    val state = ShizukuStoppedTriggerState()

    assertFalse(state.update(isAvailable = true))
    assertTrue(state.update(isAvailable = false))
    assertFalse(state.update(isAvailable = false))
}
```

```kotlin
@Test
fun `arms again after shizuku becomes available`() {
    val state = ShizukuStoppedTriggerState()

    state.update(isAvailable = true)
    assertTrue(state.update(isAvailable = false))
    assertFalse(state.update(isAvailable = false))
    assertFalse(state.update(isAvailable = true))
    assertTrue(state.update(isAvailable = false))
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`JDK_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"; JAVA_HOME="$JDK_HOME" ./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.core.workflow.module.triggers.handlers.ShizukuStoppedTriggerStateTest'`

预期：FAIL，原因是测试文件或 `ShizukuStoppedTriggerState` 尚不存在。

- [ ] **步骤 3：编写最小实现**

```kotlin
class ShizukuStoppedTriggerState {
    private var hasSeenAvailable = false
    private var hasTriggeredForCurrentOutage = false

    fun update(isAvailable: Boolean): Boolean {
        if (isAvailable) {
            hasSeenAvailable = true
            hasTriggeredForCurrentOutage = false
            return false
        }

        if (hasSeenAvailable && !hasTriggeredForCurrentOutage) {
            hasTriggeredForCurrentOutage = true
            return true
        }

        return false
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行同步骤 2。

预期：PASS。

### 任务 2：触发器模块与输出测试

**文件：**
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/ShizukuStoppedTriggerData.kt`
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/ShizukuStoppedTriggerModule.kt`
- 创建：`app/src/test/java/com/chaomixian/vflow/core/workflow/module/triggers/ShizukuStoppedTriggerModuleTest.kt`
- 修改：`app/src/main/res/values/strings_module.xml`
- 修改：`app/src/main/res/values-en/strings_module.xml`

- [ ] **步骤 1：编写失败的测试**

```kotlin
@Test
fun `module exposes reason and checked at outputs`() {
    val outputs = ShizukuStoppedTriggerModule().getOutputs(null)

    assertEquals(VTypeRegistry.STRING.id, outputs.first { it.id == "reason" }.typeName)
    assertEquals(VTypeRegistry.NUMBER.id, outputs.first { it.id == "checked_at" }.typeName)
}
```

```kotlin
@Test
fun `execute maps trigger data to outputs`() = runBlocking {
    val module = ShizukuStoppedTriggerModule()
    val context = createContext(
        triggerData = ShizukuStoppedTriggerData(
            reason = ShizukuStoppedTriggerData.REASON_BINDER_DEAD,
            checkedAt = 1234L
        )
    )

    val result = module.execute(context) { }

    assertTrue(result is ExecutionResult.Success)
    val outputs = (result as ExecutionResult.Success).outputs
    assertEquals("binder_dead", (outputs["reason"] as VString).raw)
    assertEquals(1234L, (outputs["checked_at"] as VNumber).raw.toLong())
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`JDK_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"; JAVA_HOME="$JDK_HOME" ./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.core.workflow.module.triggers.ShizukuStoppedTriggerModuleTest'`

预期：FAIL，原因是模块类尚不存在。

- [ ] **步骤 3：编写最小实现**

实现模块 ID `vflow.trigger.shizuku_stopped`，无输入，两个输出：`reason` 和 `checked_at`。`execute` 使用 `ShizukuStoppedTriggerData`，缺失时输出 `unknown` 与 `0`。

- [ ] **步骤 4：运行测试验证通过**

运行同步骤 2。

预期：PASS。

### 任务 3：Handler 与注册

**文件：**
- 创建：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/ShizukuStoppedTriggerHandler.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/TriggerHandlerRegistry.kt`
- 修改：`app/src/main/java/com/chaomixian/vflow/core/module/ModuleRegistry.kt`

- [ ] **步骤 1：实现 Handler**

Handler 继承 `ListeningTriggerHandler`。`startListening` 注册 `Shizuku.addBinderDeadListener`、`Shizuku.addBinderReceivedListenerSticky` 并启动轮询任务。每次状态检查调用 `ShizukuStoppedTriggerState.update`，返回 true 时对所有 `listeningTriggers` 调用 `executeTrigger(context, trigger, ShizukuStoppedTriggerData(reason, System.currentTimeMillis()))`。

- [ ] **步骤 2：注册模块与 Handler**

在 `ModuleRegistry.initialize` 的触发器列表中注册 `ShizukuStoppedTriggerModule()`。在 `TriggerHandlerRegistry.initialize` 注册 `ShizukuStoppedTriggerModule().id` 到 `ShizukuStoppedTriggerHandler()`。

- [ ] **步骤 3：运行窄范围测试**

运行任务 1、任务 2 的两个测试类。

预期：PASS。

### 任务 4：验证、打包和安装

**文件：**
- 检查：全部本次变更文件

- [ ] **步骤 1：自审 diff**

运行：`git diff --stat && git diff --check`

预期：无 whitespace error，变更只包含触发器、注册、字符串、测试、计划。

- [ ] **步骤 2：运行目标测试**

运行：`JDK_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"; JAVA_HOME="$JDK_HOME" ./gradlew :app:testDebugUnitTest --tests 'com.chaomixian.vflow.core.workflow.module.triggers.handlers.ShizukuStoppedTriggerStateTest' --tests 'com.chaomixian.vflow.core.workflow.module.triggers.ShizukuStoppedTriggerModuleTest'`

预期：PASS。

- [ ] **步骤 3：构建 debug APK**

运行：`JDK_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"; JAVA_HOME="$JDK_HOME" ./gradlew :app:assembleDebug`

预期：BUILD SUCCESSFUL，生成 `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`。

- [ ] **步骤 4：安装并确认包信息**

运行：`/Users/biground/Library/Android/sdk/platform-tools/adb devices`，确认单台设备后运行 `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`，再运行 `adb shell dumpsys package com.chaomixian.vflow | rg -n "versionName|versionCode|lastUpdateTime"`。

预期：安装成功，并能看到包版本与更新时间。

- [ ] **步骤 5：提交**

运行：`git add <本次实现文件> && git commit -m "feat(触发器): 新增 Shizuku 不可用触发器"`

预期：提交只包含当前任务变更。
