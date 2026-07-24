# SolveX — 智能屏幕解析助手

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.4--alpha-orange)](version.json)

SolveX 是一款基于 Android 平台的 AI 屏幕解析工具。通过悬浮球交互、多引擎截屏、大语言模型流式响应，为用户提供即时的题目解答、内容分析和知识辅助。

---

## 一、核心功能

1. **全局悬浮球**
    - 支持自由拖拽吸附，松手自动吸附屏幕边缘
    - 单击触发解析，双击取消任务或弹出扇形菜单
    - 长按切换引擎，闲置 5 秒自动收缩为窄条

2. **扇形快捷菜单**
    - 双击悬浮球唤起，弹性动画展开
    - 支持引擎切换、联网搜索开关、跳转设置、打开百度
    - 菜单项可在悬浮球外观设置中自定义

3. **智能结果抽屉**
    - 侧边栏流式输出 AI 回答，逐字显示无需等待
    - 支持 Markdown 和 LaTeX 公式原生渲染

4. **三引擎截屏**
    - **系统录屏**（MediaProjection）：兼容性最佳
    - **Shizuku ADB**：静默截图，无需每次弹窗授权
    - **无障碍取字**：直接读取屏幕文字节点，无需产生截图文件

5. **选区裁剪系统**
    - 无障碍实时扫描模式：背景透明，直接扫描文字节点
    - 静态图片裁剪模式：在截图上手动框选裁剪区域

6. **多 LLM 提供商**
    - 内置 OpenAI、Anthropic、Google Gemini 适配器
    - 兼容 OpenAI 协议的所有第三方服务

7. **联网搜索**
    - 支持 Tavily 和 Serper.dev 搜索引擎
    - AI 在 Agent 循环中自动调用搜索，无需手动操作

8. **Agent 工具调用**
    - 多轮工具循环（最多 4 轮）
    - AI 可自动联网搜索、复制到剪贴板、悬浮球显示气泡文字

9. **隐私保护**
    - **FLAG_SECURE 防截屏**：覆盖悬浮球、抽屉、选区三层
    - **隐匿模式**：通过 Shizuku 实时监测环境（每 1.5 秒轮询），自动加强保护

---

## 二、使用方式

1. **添加 AI 模型**：进入「设置 → 模型提供商」添加 API 接口
2. **配置助手**（可选）：进入「设置 → 智能助手」创建带独立提示词的 AI 角色
3. **授权权限**：首页点击「启用解析」，按引导卡片完成权限授予
4. **选择截屏模式**：进入「设置 → 通用设置」切换截屏方式
5. **启动服务**：首页点击「启用解析」按钮，长按可快速启动
6. **开始解析**：任意界面单击悬浮球即可触发 AI 分析

---

## 三、悬浮球使用

### 手势操作

| 手势     | 空闲时         | 解析进行中     |
|:-------|:------------|:----------|
| **单击** | 开始解析        | 展开/收起结果抽屉 |
| **双击** | 弹出扇形菜单      | 取消解析      |
| **长按** | 切换引擎        | 无效果       |
| **拖拽** | 移动位置，松手吸附边缘 | 移动位置      |

### 扇形快捷菜单

双击悬浮球唤起，含以下四项：

- **引擎切换**（⚙️） — 切换文本引擎/视觉引擎，当前引擎高亮显示
- **联网搜索开关**（☁️） — 一键开关联网搜索，开启时高亮显示
- **跳转设置**（⚡） — 快速进入设置页面
- **打开百度**（🌐） — 用浏览器打开百度搜索

> 菜单项可在「设置 → 悬浮球外观」中自定义勾选显示。

---

## 四、项目结构

### 模块目录

```
app/src/main/java/com/tianhuiu/solvex/
├── capture/       # 截屏引擎（策略模式，三种实现）
├── floating/      # 悬浮交互层（悬浮球、扇形菜单、抽屉、选区裁剪）
├── mode/          # 模式系统（通用模式 + ModeConfig）
├── network/       # 网络与 AI 层（LLM 适配器、SSE 解析、处理管道、搜索、Agent 工具）
├── render/        # 渲染引擎（Markdown + LaTeX 原生渲染）
├── service/       # 后台服务（MainService、无障碍服务、Shizuku 服务）
├── ui/            # UI 层（首页、历史记录、设置页、通用组件）
├── data/          # 数据层（Room 数据库、DataStore 配置、数据模型）
└── utils/         # 工具类
```

### 技术栈

- **架构**：MVVM + 手动依赖注入（AppContainer 持有全局单例）
    - OkHttpClient、UnifiedLLMClient、ProcessingPipeline 等核心组件集中管理
    - 支持运行时重建网络栈（切换信任所有证书）
- **UI**：Jetpack Compose + Material 3 + Navigation Compose
    - 单 Activity 架构，Compose 页面导航
- **网络**：OkHttp + SSE 流式传输，kotlinx.serialization 序列化
    - 统一 SSE 解析器，支持首块超时检测
    - 内置 OpenAI、Anthropic、Gemini 四种协议适配
- **持久化**：Room（历史记录）+ DataStore Preferences（配置）
    - 配置单键 JSON 序列化存储，历史记录分页查询
- **截屏**：MediaProjection / Shizuku ADB / AccessibilityService
    - 统一 ScreenCaptureEngine 接口，MainService 按策略创建
- **渲染**：intellij-markdown 解析 + jlatexmath 渲染
    - 标题/列表/代码块/引用/LaTeX 公式全支持
- **后台**：LifecycleService 前台服务
    - 前台通知保持存活，Shizuku 用户服务实现特权操作

### 联网搜索

- **支持引擎**
    - **Tavily**：默认端点 `api.tavily.com/search`，POST 请求
    - **Serper.dev**：默认端点 `google.serper.dev/search`，X-API-KEY 认证
    - **Brave Search**：默认端点 `api.search.brave.com/res/v1/web/search`，GET 请求
- **配置步骤**：「设置 → 联网配置」添加搜索引擎 → 填写 API Key → 开启总开关
- **调用方式**：AI 在 Agent 工具循环中**自动调用** `web_search` 工具，结果格式化为文本供参考

---

## 五、快速开始

### 编译环境

- JDK 21 / Kotlin 2.1
- Android Studio Ladybug 2024.2.1+
- Android SDK 36（minSdk 31）

### 构建

```bash
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK（arm64-v8a，含混淆）
```

---

## 六、扩展

- **新 LLM 提供商**
    - `ProviderKind` 添加枚举
    - 实现 `ProviderAdapter` 接口（stream / fetchModels）
    - `UnifiedLLMClient.getAdapter()` 添加路由
- **新 Agent 工具**
    - 实现 `AgentTool` 接口（name / definition / invoke）
    - `ProcessingPipeline.buildTools()` 注册
- **新搜索引擎**
    - 实现 `SearchEngineAdapter` 接口（search / validate）
    - `SearchOrchestrator.getAdapter()` 添加分支
    - `SearchProviderKind` 添加枚举
- **新截屏引擎**
    - 实现 `ScreenCaptureEngine` 接口（prepare / capture / release）
    - `CaptureMode` 添加模式常量
    - `MainService.startAsForeground()` 创建分支

---

## 七、许可

- **开源协议**: Apache License 2.0
- **源码地址
  **: [GitHub](https://github.com/xingtianiy/SolveX) / [Gitee](https://gitee.com/xingtianiy/SolveX)
- **反馈渠道**: GitHub / Gitee Issues
