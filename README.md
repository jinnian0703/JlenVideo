# JlenVideo

> 面向新接手开发者的项目说明。  
> 当前分支是 **单模块 Android 工程**，核心代码集中在 `app` 模块内。

---

## 项目简介

JlenVideo 是一个基于 **Kotlin + Jetpack Compose + Media3** 开发的苹果 CMS 视频客户端。

当前分支的工程目标比较直接：

- 提供首页、片库、搜索、详情、账号、播放器等基础能力
- 通过苹果 CMS 站点获取内容数据
- 支持原生播放器、全屏播放、线路切换、剧集切换、搜索历史、Cookie 持久化等功能

当前默认站点：

- `https://cms.jlen.top/`

相关服务仓库：

- 使用的 API 项目：
  [maccms-pure-video-api](https://github.com/jinnian0703/maccms-pure-video-api)
- 使用的管理系统：
  [appcenter-standalone-admin](https://github.com/jinnian0703/appcenter-standalone-admin)

---

## 当前版本

| 项目 | 值 |
| --- | --- |
| 应用名 | `JlenVideo` |
| Application Id | `top.jlen.vod` |
| versionName | `2.1.1.0` |
| versionCode | `24` |
| minSdk | `24` |
| targetSdk | `34` |
| compileSdk | `34` |
| JVM Target | `17` |

APK 命名规则：

```text
JlenVideo-版本号-debug.apk
```

---

## 技术栈

### Android

- Kotlin
- Jetpack Compose
- AndroidX Navigation Compose
- Lifecycle ViewModel
- Media3 ExoPlayer

### 网络与解析

- Retrofit
- OkHttp
- Gson
- Jsoup

### 构建

- Gradle
- Kotlin DSL

---

## 快速开始

### 1. 环境准备

建议本地具备：

- JDK 17
- Android Studio 或完整 Android SDK
- Windows PowerShell

### 2. 首次构建

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

如果构建通过，说明当前依赖、SDK 和工程结构都正常。

### 3. 建议先看的文件

如果你第一次接手，推荐按这个顺序读：

1. [settings.gradle.kts](/F:/codex/1/settings.gradle.kts)
2. [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)
3. [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/MainActivity.kt)
4. [JlenVideoApp.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt)
5. [AppViewModel.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)
6. [AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
7. [NativeVideoPlayer.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt)

> 阅读建议  
> 如果你只是修播放器问题，优先看 `NativeVideoPlayer.kt` 和 `FullscreenPlayerActivity.kt`。  
> 如果你是查接口或解析问题，优先看 `AppleCmsRepository.kt`、`AppleCmsApi.kt` 和 `Models.kt`。

---

## 构建方式

### 常用命令

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

### APK 输出

默认输出目录：

- [JlenVideo-2.1.1.0-debug.apk](/F:/codex/1/app/build/outputs/apk/debug/JlenVideo-2.1.1.0-debug.apk)

---

## 项目结构详解

当前分支不是多模块结构，核心代码集中在 `app`。

### 顶层目录

| 路径 | 作用 |
| --- | --- |
| `app/` | Android 应用主模块，绝大多数业务代码都在这里 |
| `templates/` | 模板与辅助资源，不属于主 Android 运行代码 |
| `core/` | 当前分支下未接入构建的实验性或历史结构目录 |
| `feature/` | 当前分支下未接入构建的实验性或历史结构目录 |

> 说明  
> 以当前 `settings.gradle.kts` 为准，真正参与构建的是 `:app`。  
> `core/` 和 `feature/` 目录当前没有被 `include()` 到 Gradle 模块里，不要误以为它们已经生效。

---

## `app` 模块结构

### 入口层

| 文件 | 作用 |
| --- | --- |
| [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/MainActivity.kt) | Android Activity 入口 |
| [JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/JlenVideoApplication.kt) | Application 初始化入口 |
| [CrashLogger.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/CrashLogger.kt) | 崩溃日志记录与读取 |

什么时候看：

- 应用启动异常
- Application 初始化问题
- 崩溃日志没有落盘

### 数据层

| 文件 | 作用 |
| --- | --- |
| [AppleCmsApi.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt) | Retrofit API 定义 |
| [AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt) | 数据入口、页面抓取、解析、用户中心、播放地址等主逻辑 |
| [Models.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/Models.kt) | 数据模型定义 |
| [PersistentCookieJar.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt) | Cookie 持久化 |
| [SearchHistoryStore.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/SearchHistoryStore.kt) | 搜索历史持久化 |

什么时候看：

- 分类、详情、搜索、账号、公告、播放地址解析错误
- 登录状态丢失
- 搜索历史不对

### 状态与页面层

| 文件 | 作用 |
| --- | --- |
| [AppViewModel.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/AppViewModel.kt) | 页面状态与业务调度中心 |
| [JlenVideoApp.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt) | Compose 应用壳和导航入口 |
| [BrowseScreens.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt) | 首页、片库、搜索、账号等页面集合 |
| [DetailPlayerScreens.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt) | 详情页与播放页 UI |

什么时候看：

- 页面状态不刷新
- 点击动作没有生效
- 首页、分类、搜索、详情、账号页面显示不对

### 播放器层

| 文件 | 作用 |
| --- | --- |
| [NativeVideoPlayer.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt) | 原生播放器 UI、手势、控制层、进度条等 |
| [FullscreenPlayerActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt) | 全屏播放器入口 |
| [PlayerWebSupport.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/PlayerWebSupport.kt) | Web 回退与网页播放器辅助逻辑 |
| [HiddenStreamResolver.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/HiddenStreamResolver.kt) | 隐藏播放流解析 |

什么时候看：

- 双击播放/暂停问题
- 控件显示隐藏问题
- 进度条拖动问题
- 全屏播放同步问题
- 播放地址识别异常

### 视觉与辅助层

| 文件 | 作用 |
| --- | --- |
| [UiPalette.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/UiPalette.kt) | 颜色与视觉常量 |
| [UiMotion.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/UiMotion.kt) | 动画与交互节奏常量 |
| [ExternalLinkHelper.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/ExternalLinkHelper.kt) | 外链打开辅助 |

---

## 关键目录命名和理解方式

虽然当前分支是单模块，但理解代码时仍然建议按“职责”来分层：

- `data`：接口、解析、持久化
- `ui`：页面、播放器、状态调度、导航
- `templates`：模板资源和辅助文件

可以把当前工程理解成：

```text
app
├─ data    -> 数据入口
├─ ui      -> 页面与播放器
└─ entry   -> Application / Activity
```

---

## 推荐阅读顺序

### 如果你要快速上手

1. [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)
2. [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/MainActivity.kt)
3. [JlenVideoApp.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt)
4. [AppViewModel.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)
5. [AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)

### 如果你要改播放器

1. [NativeVideoPlayer.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt)
2. [FullscreenPlayerActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt)
3. [DetailPlayerScreens.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt)

### 如果你要改接口或站点解析

1. [AppleCmsApi.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt)
2. [AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
3. [Models.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/Models.kt)

---

## 维护建议

### 新代码优先往哪里加

- 新页面：优先放 `app/src/main/java/top/jlen/vod/ui`
- 新接口与解析：优先放 `app/src/main/java/top/jlen/vod/data`
- 新公共视觉常量：优先放 `UiPalette.kt` / `UiMotion.kt`

### 尽量不要做的事

- 不要把页面直接写成和数据请求强耦合
- 不要把 HTML / JSON 解析散落到 UI 层
- 不要把播放器细节逻辑塞回页面文件
- 不要误把 `core/`、`feature/` 当成当前构建主入口

### 遇到问题时怎么排查

| 问题类型 | 优先检查 |
| --- | --- |
| 页面展示问题 | `BrowseScreens.kt` / `DetailPlayerScreens.kt` |
| 状态不更新 | `AppViewModel.kt` |
| 接口或解析问题 | `AppleCmsRepository.kt` |
| 播放器交互问题 | `NativeVideoPlayer.kt` |
| 登录状态问题 | `PersistentCookieJar.kt` / `AppleCmsRepository.kt` |

---

## 当前工程约定

- 默认站点在 [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts) 的 `BuildConfig.APPLE_CMS_BASE_URL`
- APK 命名规则固定为：

```text
JlenVideo-版本号-debug.apk
```

- 小改动也会自动提交并推送
- 发布版本默认会一起完成：
  1. 修改版本号
  2. 编译 APK
  3. 提交并推送源码
  4. 创建 GitHub Release
  5. 上传 APK 到 Release
- Release 中文说明统一通过 UTF-8 文件和 `--notes-file` 发布

---

## 2.1.1.0 更新说明

- 优化播放器逻辑

---

## 附：模板目录说明

[templates](/F:/codex/1/templates) 目录用于放模板和辅助资源，当前内容包括：

- [DYXS2](/F:/codex/1/templates/DYXS2)
- [v2.zip](/F:/codex/1/templates/v2.zip)

平时排查 Android 客户端问题时，一般不需要先看这里。
