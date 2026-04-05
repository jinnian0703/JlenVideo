# JlenVideo

> 一份面向新接手开发者的项目手册。  
> 这份文档的目标不是“把目录列出来”，而是帮助你在第一次打开工程时，知道这个项目是做什么的、应该先看哪里、平时改需求要进哪个模块。

---

## 1. 项目简介

JlenVideo 是一个基于 **Android 原生 + Kotlin + Jetpack Compose** 开发的苹果 CMS 视频客户端。

当前工程已经完成多模块化改造，主要目标是把下面几类职责拆开：

- 启动壳层
- 通用配置与基础能力
- 数据访问与解析
- 页面 UI
- 播放器能力
- 状态管理
- 历史实现兼容区

这样做的意义不是“为了好看”，而是为了让后续开发可以更稳定地迭代：改播放器时尽量不碰页面，改页面时尽量不碰数据层，改状态逻辑时尽量不碰应用入口。

### 当前项目参数

| 项 | 值 |
| --- | --- |
| 项目名 | `JlenVideo` |
| Application Id | `top.jlen.vod` |
| 当前版本 | `2.1.0.2` |
| 当前 versionCode | `25` |
| 默认站点 | `https://cms.jlen.top/` |
| 最低 Android 版本 | `24` |
| 目标 Android 版本 | `34` |
| Java / Kotlin JVM Target | `17` |

---

## 2. 技术栈

### Android 侧

- Kotlin
- Jetpack Compose
- AndroidX
- Lifecycle ViewModel
- Navigation Compose

### 网络与解析

- Retrofit
- OkHttp
- Gson
- Jsoup

### 构建方式

- Gradle
- Kotlin DSL
- 多模块工程

---

## 3. 先别急着改代码：你应该先怎么理解这个项目

如果你是第一次接手，建议不要一上来就钻进几千行旧文件里。

更推荐的理解顺序是：

1. 先看模块清单，知道工程被拆成了哪些块
2. 再看应用壳层，知道启动后先进入哪里
3. 再看状态层，理解页面状态和业务调度怎么流转
4. 再看数据层，理解接口和 HTML / JSON 解析在哪里
5. 最后再看 legacy runtime，理解历史逻辑是怎么被隔离起来的

> 阅读建议  
> 如果你的目标只是“修一个页面问题”，不要先看 `legacy repository`。  
> 如果你的目标是“查接口解析为什么不对”，就优先看 `core:data`。  
> 如果你的目标是“播放器状态同步为什么错了”，就优先看 `feature:state` 和 `feature:player`。

---

## 4. 快速开始

### 4.1 环境准备

建议你本地至少具备：

- JDK 17
- Android Studio 或可用的 Android SDK / Gradle 环境
- Windows PowerShell 或可运行 `gradlew.bat` 的终端

### 4.2 第一次拉起项目

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

如果构建通过，说明模块依赖和当前工程结构是正常的。

### 4.3 构建通过后，建议立刻看这几个文件

```text
settings.gradle.kts
app/build.gradle.kts
feature/shell/.../JlenVideoApp.kt
feature/state/.../AppViewModel.kt
core/data/.../AppleCmsRepository.kt
```

这 5 个入口足够你先建立整个工程的主视图。

---

## 5. 构建方式

### 常用命令

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

### 命令分别做什么

| 命令 | 作用 |
| --- | --- |
| `:app:compileDebugKotlin` | 先验证 Kotlin 编译层是否正常，适合快速检查改动有没有破坏模块依赖 |
| `:app:assembleDebug` | 完整构建 debug APK，适合交付可安装产物 |

### APK 输出位置

当前 debug APK 默认输出到：

- [JlenVideo-2.1.0.2-debug.apk](/F:/codex/1/app/build/outputs/apk/debug/JlenVideo-2.1.0.2-debug.apk)

命名规则由 [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts) 中的打包逻辑统一处理：

```text
JlenVideo-版本号-debug.apk
```

---

## 6. 模块结构详解

这一部分不是简单列目录，而是告诉你“每个模块负责什么，遇到什么问题先去哪里找”。

### 6.1 `app`

`app` 现在是**真正的应用壳层**，职责非常明确：  
只负责 Android 应用入口、打包和最终 APK 产出。

| 模块 | 主要职责 | 关键文件 |
| --- | --- | --- |
| `app` | Application / Activity 入口、最终打包 | [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/activity/MainActivity.kt)、[JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/application/JlenVideoApplication.kt)、[app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts) |

什么时候看这里：

- 应用打不开
- 启动白屏
- Application 初始化问题
- APK 命名、版本号、打包配置问题

---

### 6.2 `core`

`core` 放的是“基础能力”，不直接承载具体页面。

#### `core:model`

只放纯数据模型，避免页面层和数据层各自复制一份结构。

- [Models.kt](/F:/codex/1/core/model/src/main/java/top/jlen/vod/data/shared/model/Models.kt)

什么时候看这里：

- 接口字段结构变了
- 视频、分类、播放源等模型要扩展
- 想确认某个实体字段到底叫什么

#### `core:common`

放全局配置和基础运行时能力。

- [AppConfig.kt](/F:/codex/1/core/common/src/main/java/top/jlen/vod/config/runtime/app/AppConfig.kt)
- [CrashLogger.kt](/F:/codex/1/core/common/src/main/java/top/jlen/vod/logging/crash/handler/CrashLogger.kt)

什么时候看这里：

- 默认站点配置问题
- 运行时版本信息问题
- 崩溃日志记录和读取问题

#### `core:design`

放共享视觉常量，不直接写业务。

- [UiPalette.kt](/F:/codex/1/core/design/src/main/java/top/jlen/vod/ui/theme/palette/system/UiPalette.kt)
- [UiMotion.kt](/F:/codex/1/core/design/src/main/java/top/jlen/vod/ui/motion/spec/system/UiMotion.kt)

什么时候看这里：

- 全局颜色风格调整
- 动画时长、过渡节奏调整
- 多个页面要统一视觉风格

#### `core:data`

这是数据访问中心，也是工程里最重要的模块之一。  
它负责：

- 网络接口
- HTML / JSON 解析
- Cookie
- 搜索历史
- 对外 repository
- legacy repository runtime

关键文件：

- [AppleCmsApi.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/api/service/main/AppleCmsApi.kt)
- [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)
- [AppleCmsRepositorySupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/shell/cms/AppleCmsRepositorySupport.kt)
- [PersistentCookieJar.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/storage/cookie/persistent/PersistentCookieJar.kt)
- [SearchHistoryStore.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/storage/history/local/SearchHistoryStore.kt)

什么时候看这里：

- 接口请求失败
- 苹果 CMS 页面结构变了
- 分类、详情、搜索、用户中心解析出错
- Cookie 丢失、登录状态异常
- 搜索历史不对

---

### 6.3 `feature`

`feature` 是具体功能模块，按业务分拆。

#### `feature:common`

共享 UI 状态和公共组件，不直接承载具体页面业务。

关键文件：

- [HomeUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/home/main/HomeUiState.kt)
- [SearchUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/search/main/SearchUiState.kt)
- [AccountUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/account/main/AccountUiState.kt)
- [DetailUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/detail/main/DetailUiState.kt)
- [PlayerUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/player/main/PlayerUiState.kt)
- [NoticeUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/notice/main/NoticeUiState.kt)
- [SectionTitle.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/section/title/main/SectionTitle.kt)
- [FeedbackPanes.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/feedback/panes/main/FeedbackPanes.kt)
- [ErrorBanner.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/feedback/banner/main/ErrorBanner.kt)
- [PosterRequest.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/poster/request/main/PosterRequest.kt)

什么时候看这里：

- 多个页面共用状态定义
- 通用 loading / error / empty UI
- 通用标题或海报组件

#### `feature:browse`

承载首页、片库、搜索、公告、账号页面。

关键文件：

- [BrowseHomeCategory.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/home/screen/main/BrowseHomeCategory.kt)
- [BrowseSearch.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/search/screen/main/BrowseSearch.kt)
- [BrowseAnnouncements.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/announcements/screen/main/BrowseAnnouncements.kt)
- [BrowseAccount.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/screen/main/BrowseAccount.kt)
- [LegacyBrowseAccount.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/legacy/LegacyBrowseAccount.kt)

什么时候看这里：

- 首页展示问题
- 分类筛选、吸顶、列表分页问题
- 搜索页显示问题
- 公告页布局问题
- 账号页 UI 问题

#### `feature:detail`

承载详情页和内嵌播放器页面。

关键文件：

- [DetailScreen.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/detail/screen/main/DetailScreen.kt)
- [PlayerScreen.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/player/screen/main/PlayerScreen.kt)
- [DetailPlayerScreens.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/shared/components/layout/DetailPlayerScreens.kt)

什么时候看这里：

- 详情页信息不对
- 选源、选集 UI 不对
- 内嵌播放器页面布局或交互异常

#### `feature:player`

承载播放器能力本身。

关键文件：

- [NativeVideoPlayer.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt)
- [FullscreenPlayerActivity.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/fullscreen/activity/main/FullscreenPlayerActivity.kt)
- [FullscreenPlaybackResult.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/fullscreen/contract/result/FullscreenPlaybackResult.kt)
- [PlaybackSnapshot.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/shared/state/playback/current/PlaybackSnapshot.kt)
- [HiddenStreamResolver.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/resolver/support/stream/HiddenStreamResolver.kt)
- [PlayerWebSupport.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/web/support/page/PlayerWebSupport.kt)

什么时候看这里：

- 播放器手势问题
- 双击播放 / 暂停问题
- 全屏同步问题
- 快照恢复问题
- Web 回退问题
- 播放链接解析问题

#### `feature:shell`

承载 Compose 应用导航壳。

- [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)

什么时候看这里：

- 页面跳转不对
- 底部导航问题
- 页面入口关系不清楚

#### `feature:state`

承载状态管理与业务调度，是页面层和数据层之间的桥。

对外入口：

- [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)

什么时候看这里：

- 页面状态为什么没有刷新
- 页面动作为什么没有落到 UI
- 详情、搜索、账号、播放器状态同步为什么错了

---

## 7. 关键目录命名规则说明

这一部分很重要，理解命名规则后，很多文件不用点开就知道大概职责。

### `shell`

薄壳入口层。  
对外暴露稳定入口，自己尽量不承载大段正文逻辑。

典型例子：

- [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)
- [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)

### `runtime`

运行时真正执行逻辑的层。  
这里通常存放仍在运行中的正文实现，不是对外展示壳。

### `core`

runtime 内部的核心正文实现。  
一般用于承接“大实现主体”。

### `legacy`

历史兼容实现区。  
这不是“废代码”，而是仍在工作的老逻辑，只是已经被隔离出来，方便逐步重构。

### `support`

辅助逻辑层。  
通常是纯函数、纯解析、纯状态构造、共用工具。

### `actions`

按动作或业务流拆开的正文文件。  
适合承接“某一组流程”的具体实现，例如首页加载、账号动作、播放器同步。

### `models`

内部数据模型层。  
用于避免某个巨大正文文件把内部数据类全塞在一起。

---

## 8. 关键文件作用说明

这一节专门回答一个问题：**平时遇到问题，应该先看哪个文件。**

### 应用入口

- [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/activity/MainActivity.kt)  
  当应用启动异常、Activity 生命周期问题、入口页面不对时先看这里。

- [JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/application/JlenVideoApplication.kt)  
  当全局初始化、应用级配置、崩溃初始化有问题时先看这里。

### 导航入口

- [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)  
  当你不知道页面是怎么接起来的，先看这里。

### 状态中枢

- [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)  
  这是外部使用的 ViewModel 入口。  
  页面调用动作、读取状态，一般会经过这里。

- [LegacyStateRuntimeViewModelCore.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/runtime/core/LegacyStateRuntimeViewModelCore.kt)  
  当你需要追踪旧逻辑正文是怎么跑的，就要进入这里。

### 数据中枢

- [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)  
  对外数据仓库入口。

- [LegacyAppleCmsRuntimeRepositoryCore.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/runtime/core/LegacyAppleCmsRuntimeRepositoryCore.kt)  
  真正的 legacy repository 正文核心。  
  当你遇到搜索、详情、用户中心、分类、播放解析等数据问题，最终大概率会追到这里。

### 播放器核心

- [NativeVideoPlayer.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt)  
  几乎所有播放器手势、控制层显示、暂停播放行为，都会进这里。

- [FullscreenPlayerActivity.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/fullscreen/activity/main/FullscreenPlayerActivity.kt)  
  全屏模式问题先看这里。

### 页面层

- [BrowseHomeCategory.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/home/screen/main/BrowseHomeCategory.kt)  
  首页 / 分类页问题先看这里。

- [BrowseSearch.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/search/screen/main/BrowseSearch.kt)  
  搜索页 UI 与交互问题先看这里。

- [DetailScreen.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/detail/screen/main/DetailScreen.kt)  
  详情页展示、选源选集问题先看这里。

---

## 9. 推荐阅读顺序

如果你打算系统接手这个项目，建议用下面这个顺序读。

### 第 1 步：先认识工程边界

1. [settings.gradle.kts](/F:/codex/1/settings.gradle.kts)
2. [gradle.properties](/F:/codex/1/gradle.properties)
3. [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)

### 第 2 步：看应用是怎么串起来的

1. [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/activity/MainActivity.kt)
2. [JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/application/JlenVideoApplication.kt)
3. [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)

### 第 3 步：看状态入口

1. [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)
2. 各 `feature:state/support/*`
3. [LegacyStateRuntimeViewModelCore.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/runtime/core/LegacyStateRuntimeViewModelCore.kt)

### 第 4 步：看数据入口

1. [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)
2. [AppleCmsRepositorySupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/shell/cms/AppleCmsRepositorySupport.kt)
3. [LegacyAppleCmsRuntimeRepositoryCore.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/runtime/core/LegacyAppleCmsRuntimeRepositoryCore.kt)

### 第 5 步：按业务去看页面

1. 首页 / 分类：`feature:browse`
2. 详情：`feature:detail`
3. 播放器：`feature:player`
4. 账号：`feature:browse/account`

---

## 10. 维护建议

这部分是最容易被忽略，但最重要的。

### 新代码优先放哪里

- 新的页面 UI：优先放 `feature:*`
- 新的通用状态模型：优先放 `feature:common`
- 新的数据模型：优先放 `core:model`
- 新的接口或解析：优先放 `core:data`
- 新的通用配置或崩溃能力：优先放 `core:common`
- 新的视觉常量：优先放 `core:design`

### 尽量不要破坏的边界

- 不要把页面逻辑再塞回 `app`
- 不要把状态逻辑直接写进页面 Composable
- 不要把 HTML / JSON 解析写进页面层
- 不要把新的大逻辑继续堆进 `shell`
- 不要直接把新逻辑和 `legacy` 紧耦合，优先走当前壳层入口

### 什么时候应该进入 `legacy`

- 修线上真实老逻辑问题
- 当前壳层或 support 已经无法解释现有行为
- 明确知道某条业务链 עדיין 走的是历史正文实现

### 什么时候不要先碰 `legacy`

- 只是改页面布局
- 只是改通用组件样式
- 只是补一个局部状态字段
- 只是改导航入口

> 维护提示  
> 现在的工程结构已经把“入口”和“正文”分开了。  
> 日常改需求时，优先顺着 `shell -> support -> actions/runtime` 这条线查，不要一上来就全局搜大文件正文。

---

## 11. 当前工程约定

- 默认站点来源于 [gradle.properties](/F:/codex/1/gradle.properties)
- APK 命名规则固定为：

```text
JlenVideo-版本号-debug.apk
```

- 小改动也会自动提交并推送
- 发布版本默认会一起做：
  - 修改版本号
  - 编译 APK
  - 提交并推送源码
  - 创建 GitHub Release
  - 上传 APK 到 Release
- GitHub Release 中文说明统一通过 UTF-8 文件和 `--notes-file` 发布

---

## 12. 辅助目录说明

### `templates`

[templates](/F:/codex/1/templates) 目录下放的是模板资源，不属于 Android 主模块代码。

当前内容包括：

- [DYXS2](/F:/codex/1/templates/DYXS2)
- [v2.zip](/F:/codex/1/templates/v2.zip)

如果你在排查 Android 客户端问题，一般不用先看这里。  
只有在需要确认站点模板资源、兼容旧模板数据或做对照时，才需要进入这个目录。

---

## 13. 一句话总结

这个项目现在的结构可以理解成：

- `app`：启动和打包
- `core`：基础能力
- `feature`：页面和功能
- `shell`：稳定入口
- `support`：辅助逻辑
- `actions`：按业务流拆开的正文
- `legacy/runtime`：历史实现兼容区

如果你是第一次接手，先看入口，再看状态，再看数据，最后再进 legacy 正文，会轻松很多。
