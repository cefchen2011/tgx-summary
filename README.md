# TGX 消息总结（Telegram X · LSPosed 模块）

给 **Telegram X**（`org.thunderdog.challegram`）加一个消息总结功能：
屏幕边缘一个半圆按钮，点开是 Material 3 风格的小窗叠加层，可以选**总结条数**和**详细程度（简洁/标准/详细）**，
点「生成总结」后把当前会话最近 N 条消息交给 **DeepSeek** 总结，**中文输出**。

```
┌────────────────────────────┐
│ 消息总结                 ▾ │
│ StarVoyager Ideal          │
│ 总结条数       −   50   +  │
│ 详细程度  [简洁|标准|详细]  │
│ ┌────────────────────────┐ │
│ │       生成总结         │ │
│ └────────────────────────┘ │
│ ┌────────────────────────┐ │
│ │ 背景：                 │ │
│ │ - 聊天标题为…          │ │
│ │ - 参与者包括…          │ │
│ └────────────────────────┘ │
└────────────────────────────┘
```

## 环境要求

- 已 root 的 Android（本项目在 Redmi / Android 16 / Magisk + LSPosed 2.1.1 上验证通过）
- LSPosed（或兼容的 Xposed 框架）
- 目标应用：Telegram X `org.thunderdog.challegram`
- 一个 DeepSeek API Key（https://platform.deepseek.com ）

## 编译

```powershell
# JDK 17 + Gradle 8.4 + Android SDK (platform 34 / build-tools 34.0.0)
& "D:\tool\gradle-8.4\bin\gradle.bat" assembleDebug --no-daemon
# 产物：app\build\outputs\apk\debug\app-debug.apk
```

`app/libs/api-82.jar` 是 Xposed API，仅 `compileOnly`，不会打进 APK（运行时由 LSPosed 提供）。

## 安装与启用

1. `adb install -r app-debug.apk`
2. 打开 **LSPosed 管理器 → 模块 → 勾选「TGX 消息总结」**，作用域勾选 **Telegram X**
3. 给 Telegram X 授予悬浮窗权限（模块内有一键按钮，需要 root；等价命令）：
   ```bash
   adb shell appops set org.thunderdog.challegram SYSTEM_ALERT_WINDOW allow
   ```
4. 强制停止并重新打开 Telegram X

## 配置 API Key

打开模块 App「TGX 消息总结」，填入 **DeepSeek API Key**（接口地址与模型一般不用改），点「保存」并「测试连接」。

> 保存时会用 root 把配置同步写进 Telegram X 自己的 SharedPreferences（文件名 `tgxsum`）。
> 因为 hook 运行在 Telegram X 进程内，同进程读取最可靠；跨进程广播只作为兜底。
> **改完配置后请重启一次 Telegram X 使其生效。**

## 使用

1. 在 Telegram X 里打开任意聊天
2. 点屏幕边缘的半圆（可上下拖动，位置会被记住）
3. 在面板里设置条数与详细程度，点「生成总结」
4. 结果可滚动、可长按选择复制

详细程度：

| 档位 | 说明 |
| --- | --- |
| 简洁 | 1–2 句话，不超过约 80 字 |
| 标准 | 3–6 条要点 + 一行结论 |
| 详细 | 分节：背景 / 讨论要点 / 结论与待办，带发言者归属 |

## 实现要点

Telegram X 的发布版被 R8 混淆了业务类，但 `org.drinkless.tdlib.TdApi` 与
`org.drinkless.tdlib.Client` 被 ProGuard 规则保留，所以本模块**完全不依赖被混淆的类**：

- **钩 Client.send(TdApi$Function, ResultHandler)**：ResultHandler 接口本身被混淆成
  `org.drinkless.tdlib.c`，因此从不按名字查找——而是遍历 Client 的声明方法，
  用「参数是 TdApi$Function + 某个接口 + 返回 void」这个形状定位 send，
  第二个参数类型就是 ResultHandler。所有回调都用 `java.lang.reflect.Proxy` 动态实现该接口。
- **识别当前会话**：监听 OpenChat / ViewMessages / GetChatHistory 等函数里的 chatId 字段。
- **取消息**：用同一个 Client 实例自己发 `GetChatHistory(chatId, 0, 0, count, false)`，
  再用 `GetUser` 解析发送者名字，拼成 transcript。
- **全反射**：TdApi 的类与字段全部通过字符串 + XposedHelpers 访问，没有任何编译期依赖。

悬浮层：

- `TYPE_APPLICATION_OVERLAY` 窗口。页面切前后台只切换**可见性**，不 remove/add 窗口，
  因此不会闪烁、也不会重置拖动位置（Telegram X 的 Activity 会以约 1Hz 频率 pause/resume，必须防抖）。

## 文件结构

| 文件 | 作用 |
| --- | --- |
| HookEntry.java | Xposed 入口，只对 Telegram X 生效 |
| Td.java | TDLib 反射桥：找 send、发请求、识别当前会话 |
| Msg.java | 消息解码（文本/图片/语音…） |
| ChatSummarizer.java | 取历史 → 拼 transcript → 调 DeepSeek |
| DeepSeekClient.java | DeepSeek Chat Completions 客户端 |
| SummaryPrompts.java | 中文 prompt（简洁/标准/详细） |
| OverlayController.java | 悬浮窗（半圆 + 面板）与生命周期 |
| OverlayPanel.java / SemiCircleView.java / Md3.java | Material 3 界面 |
| ConfigProvider.java / ConfigReceiver.java / HookConfig.java / TgPrefs.java | 配置读取（进程内优先，跨进程兜底） |
| MainActivity.java / Config.java | 模块设置页 |

## 排障

LSPosed 会把 XposedBridge.log 写到 `/data/adb/lspd/log/modules_*.log`，过滤 `[TGXSum]`：

```bash
su -c "grep TGXSum /data/adb/lspd/log/modules_*.log | tail -30"
```

常见提示：

- `未配置 DeepSeek API Key` → 模块 App 里没保存，或保存后没重启 Telegram X
- `未识别到当前会话` → 先在 Telegram X 里打开一个聊天，再点半圆
- `addView(handle) failed` → 没给 Telegram X 悬浮窗权限
