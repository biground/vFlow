# Shizuku 不可用触发器设计

## 背景

vFlow 已支持 Shizuku 作为 Shell/Core 能力的权限后端，但目前没有一个自动触发器能在 Shizuku 运行状态丢失时启动工作流。用户希望像 MacroDroid 的“Shizuku 已停止”触发器一样，在 Shizuku 不可用后尽快执行一个工作流，用于配置后续的重启或恢复动作。

## 目标

- 新增自动触发器 `vflow.trigger.shizuku_stopped`。
- 当 Shizuku 曾经可用且已授权，随后变为不可用时触发一次工作流。
- 启动监听时如果 Shizuku 本来就不可用，不立即触发，避免误报。
- Shizuku 重新可用后重置状态，下一次从可用变为不可用时可再次触发。
- 触发器输出可用于工作流后续判断，至少包含不可用原因和检测时间。

## 非目标

- 不在触发器内部直接重启 Shizuku。触发器只负责启动工作流，重启逻辑由用户在工作流步骤中配置。
- 不新增 Core 或 Shizuku UserService 协议。
- 不把 Root 可用性视为 Shizuku 可用性；该触发器只关注 Shizuku。

## 用户体验

触发器显示在“触发器”分类中，名称为“Shizuku 已停止”。它不需要参数，摘要显示“Shizuku 不可用时触发”。用户可以创建一个工作流，以该触发器作为自动触发条件，再添加用于恢复 Shizuku 或提醒用户的步骤。

## 触发语义

状态定义使用现有 `ShellManager.isShizukuActive(context)`：

- `available`: `Shizuku.pingBinder()` 为真且 `checkSelfPermission()` 已授权。
- `unavailable`: ping 失败、权限丢失、Shizuku 服务停止或 API 调用异常。

触发规则：

1. Handler 启动后记录当前状态。
2. 只有当内部状态已经见过 `available`，再检测到 `unavailable` 时，才触发工作流。
3. 同一次不可用期间只触发一次。
4. 重新检测到 `available` 后允许下一次不可用再次触发。

## 检测机制

采用事件监听优先、轮询兜底：

- 注册 `Shizuku.addBinderDeadListener`，Binder 死亡时立即检查状态并触发。
- 注册 `Shizuku.addBinderReceivedListenerSticky`，Binder 恢复时刷新内部状态。
- 启动一个短间隔轮询任务作为兜底，检查 `ShellManager.isShizukuActive(context)` 的状态转移。默认间隔为 2 秒。
- 停止监听时移除 Shizuku listener 并取消轮询任务。

这样可以在常见的 Shizuku 停止事件上快速响应，同时覆盖权限变化、进程恢复、listener 未收到事件等边界。

## 工作流输出

`ShizukuStoppedTriggerModule.execute` 从 `triggerData` 中读取检测结果并输出：

- `reason`: 字符串，初始值可为 `binder_dead` 或 `unavailable`。
- `checked_at`: 数字，检测时间戳，毫秒。

如果没有 `triggerData`，输出默认 `reason = "unknown"`，`checked_at = 0`。

## 代码改动范围

- 新增 `ShizukuStoppedTriggerModule`，定义触发器元数据、输出和摘要。
- 新增 `ShizukuStoppedTriggerData`，作为 `Parcelable` 触发数据。
- 新增 `ShizukuStoppedTriggerHandler`，负责 listener 和轮询状态机。
- 在 `ModuleRegistry` 注册模块。
- 在 `TriggerHandlerRegistry` 注册 handler。
- 增加中英文字符串资源。
- 增加聚焦单元测试，验证模块输出和 Handler 状态机的核心触发语义。

## 错误处理与兼容性

- 触发器没有参数，不涉及 enum 迁移。
- Shizuku API 调用异常视为不可用，但只有经历过可用状态后才触发。
- Handler 捕获 listener 注册/移除异常并继续依赖轮询兜底，避免触发服务崩溃。
- 不更改现有工作流 JSON 结构；旧工作流不受影响。

## 验证

- 运行新增触发器相关单元测试。
- 运行触发器或模块注册相关测试，确认模块可被注册和执行。
- 运行 `:app:testDebugUnitTest` 的相关窄范围测试。
- 完成实现后按项目要求执行自审、构建 debug APK 并安装到当前连接设备。
