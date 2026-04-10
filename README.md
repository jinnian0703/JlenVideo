# JlenVideo

> 面向新接手开发者的教程型项目说明。  
> 当前分支为 **module**，已经完成 `app / core / feature` 多模块拆分。

---

## 1. 项目简介

JlenVideo 是一个基于 **Kotlin + Jetpack Compose + Media3** 开发的苹果 CMS 视频客户端。

当前 `module` 分支的重点不是功能实验，而是把原本集中在单模块里的代码拆成清晰的模块边界，便于继续维护、排查和扩展。

当前默认站点：

- `https://cms.jlen.top/`

相关配套仓库：

- API：
  [maccms-pure-video-api](https://github.com/jinnian0703/maccms-pure-video-api)
- 管理系统：
  [appcenter-standalone-admin](https://github.com/jinnian0703/appcenter-standalone-admin)

---

## 2. 当前版本

| 项目 | 值 |
| --- | --- |
| 项目名 | `JlenVideo` |
| Application Id | `top.jlen.vod` |
| 当前版本 | `2.1.1.3` |
| 当前 versionCode | `29` |
| minSdk | `24` |
| targetSdk | `34` |
| compileSdk | `34` |
| JVM Target | `17` |

APK 命名规则：

```text
JlenVideo-版本号-debug.apk
```

---

## 3. 技术栈

### Android

- Kotlin
- Jetpack Compose
- AndroidX
- Lifecycle ViewModel
- Navigation Compose
- Media3 ExoPlayer

### 网络与解析

- Retrofit
- OkHttp
- Gson
- Jsoup

### 构建

- Gradle
- Kotlin DSL
- 多模块工程

---

## 4. 快速开始

### 环境准备

- JDK 17
- Android Studio 或完整 Android SDK
- Windows PowerShell

### 第一次构建

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

如果构建通过，说明当前模块依赖与本地环境都正常。

### 第一次建议看的文件

```text
settings.gradle.kts
gradle.properties
app/build.gradle.kts
feature/shell/.../JlenVideoApp.kt
feature/state/.../AppViewModel.kt
core/data/.../AppleCmsRepository.kt
feature/player/.../NativeVideoPlayer.kt
```

> 阅读建议  
> 想理解整个应用怎么串起来，先看 `feature:shell`。  
> 想查页面状态为什么不对，先看 `feature:state`。  
> 想查接口或解析问题，先看 `core:data`。  
> 想查播放交互问题，先看 `feature:player`。

---

## 5. 构建方式

### 常用命令

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

### APK 输出

- [JlenVideo-2.1.1.3-debug.apk](/F:/codex/1/app/build/outputs/apk/debug/JlenVideo-2.1.1.3-debug.apk)

---

## 6. 模块结构详解

当前工程参与构建的模块如下：

```text
app
core:model
core:common
core:design
core:data
feature:common
feature:browse
feature:detail
feature:player
feature:shell
feature:state
```

### `app`

`app` 是最终应用壳层，只负责：

- Android Application / Activity 入口
- 打包配置
- APK 产出

关键文件：

- [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/activity/MainActivity.kt)
- [JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/application/JlenVideoApplication.kt)
- [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)

什么时候看：

- 应用启动异常
- 版本号 / 打包 / APK 命名问题

### `core:model`

纯数据模型层，放视频、分类、用户中心等共享模型。

关键文件：

- [Models.kt](/F:/codex/1/core/model/src/main/java/top/jlen/vod/data/shared/model/Models.kt)

### `core:common`

通用配置和基础运行时能力。

关键文件：

- [AppConfig.kt](/F:/codex/1/core/common/src/main/java/top/jlen/vod/config/runtime/app/AppConfig.kt)
- [CrashLogger.kt](/F:/codex/1/core/common/src/main/java/top/jlen/vod/logging/crash/handler/CrashLogger.kt)

### `core:design`

共享设计常量。

关键文件：

- [UiPalette.kt](/F:/codex/1/core/design/src/main/java/top/jlen/vod/ui/theme/palette/system/UiPalette.kt)
- [UiMotion.kt](/F:/codex/1/core/design/src/main/java/top/jlen/vod/ui/motion/spec/system/UiMotion.kt)

### `core:data`

数据入口模块，负责：

- Retrofit API
- HTML / JSON 解析
- Cookie 与搜索历史
- Repository 壳层
- legacy runtime repository / parsing

关键文件：

- [AppleCmsApi.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/api/service/main/AppleCmsApi.kt)
- [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)
- [AppleCmsRepositorySupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/shell/cms/AppleCmsRepositorySupport.kt)
- [LegacyAppleCmsRuntimeRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/shell/LegacyAppleCmsRuntimeRepository.kt)
- [LegacyAppleCmsRuntimeRepositoryCore.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/runtime/core/LegacyAppleCmsRuntimeRepositoryCore.kt)

什么时候看：

- 站点数据解析不对
- 分类、搜索、详情、播放地址异常
- 登录、收藏、历史、会员相关数据异常

### `feature:common`

共享 UI 状态模型和公共组件。

### `feature:browse`

首页、片库、搜索、公告、账号相关页面。

关键入口：

- [BrowseHomeCategory.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/home/screen/main/BrowseHomeCategory.kt)
- [BrowseSearch.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/search/screen/main/BrowseSearch.kt)
- [BrowseAnnouncements.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/announcements/screen/main/BrowseAnnouncements.kt)
- [BrowseAccount.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/screen/main/BrowseAccount.kt)

### `feature:detail`

详情页与内嵌播放页 UI。

关键入口：

- [DetailScreen.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/detail/screen/main/DetailScreen.kt)
- [PlayerScreen.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/player/screen/main/PlayerScreen.kt)

### `feature:player`

播放器能力模块。

关键入口：

- [NativeVideoPlayer.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt)
- [FullscreenPlayerActivity.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/fullscreen/activity/main/FullscreenPlayerActivity.kt)
- [HiddenStreamResolver.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/resolver/support/stream/HiddenStreamResolver.kt)

### `feature:shell`

应用导航壳。

关键文件：

- [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)

### `feature:state`

状态调度与业务桥接层。

关键文件：

- [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)
- [LegacyStateRuntimeViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/shell/LegacyStateRuntimeViewModel.kt)
- [LegacyStateRuntimeViewModelCore.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/runtime/core/LegacyStateRuntimeViewModelCore.kt)

---

## 7. 关键目录命名规则

### `shell`

薄壳入口。  
对外提供稳定入口，尽量不放大段正文。

### `runtime`

真正运行时实现所在层。

### `core`

runtime 内部的核心正文。

### `legacy`

历史实现兼容区。  
不是废代码，而是仍在运行、但已被隔离出来的历史正文。

### `support`

辅助逻辑层，常见于纯解析、纯工具、纯状态构造。

### `actions`

按业务动作拆开的正文文件，比如首页加载、账号动作、播放器同步。

### `models`

内部数据模型层，避免巨型正文文件混放数据类。

---

## 8. 关键文件作用说明

### 应用入口

- [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/activity/MainActivity.kt)  
  应用启动入口，Activity 层问题先看这里。

- [JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/application/JlenVideoApplication.kt)  
  Application 初始化问题先看这里。

### 导航入口

- [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)  
  页面导航、底部栏、主页面入口先看这里。

### 状态入口

- [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)  
  ViewModel 对外入口。

- [LegacyStateRuntimeViewModelCore.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/runtime/core/LegacyStateRuntimeViewModelCore.kt)  
  真正的 legacy 状态正文核心。

### 数据入口

- [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)  
  Repository 对外入口。

- [LegacyAppleCmsRuntimeRepositoryCore.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/runtime/core/LegacyAppleCmsRuntimeRepositoryCore.kt)  
  真正的 legacy repository 正文核心。

### 播放器入口

- [NativeVideoPlayer.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt)  
  手势、控制层、暂停播放、进度条等问题优先看这里。

---

## 9. 推荐阅读顺序

推荐按下面顺序理解这个分支：

1. [settings.gradle.kts](/F:/codex/1/settings.gradle.kts)
2. [gradle.properties](/F:/codex/1/gradle.properties)
3. [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)
4. [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)
5. [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)
6. [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)
7. [NativeVideoPlayer.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt)

---

## 10. 维护建议

- 新页面优先加到对应 `feature:*`
- 新数据模型优先加到 `core:model`
- 新接口和解析优先加到 `core:data`
- 新通用状态或组件优先加到 `feature:common`
- 不要把新逻辑再塞回 `app`
- 不要直接在页面里写解析逻辑
- 不要绕过 `shell` 入口直接耦合 legacy core

---

## 11. 当前工程约定

- 默认站点配置在 [gradle.properties](/F:/codex/1/gradle.properties)
- 当前版本为 `2.1.1.3 (29)`
- APK 命名规则固定为：

```text
JlenVideo-版本号-debug.apk
```

- 小改动也自动提交并推送
- 发布版本默认一起完成：
  1. 修改版本号
  2. 编译 APK
  3. 提交并推送源码
  4. 创建 GitHub Release
  5. 上传 APK 到 Release

---

## 12. 2.1.1.3 更新说明

- 优化搜索联想请求与面板展示，减少等待感并兼容旧接口兜底
- 修复沉浸式页面顶部安全区，公告页与详情页头部显示更稳定
- 新增底栏一级入口“追剧”，聚合已加入追剧内容的更新与续播状态
- 统一空状态、错误提示和海报失败态的轻量反馈样式
- “我的”页移除旧收藏入口，详情页收藏操作统一改为“追剧”
- 修复追剧相关提示文案与错误提示的旧“收藏”残留
