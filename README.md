# JlenVideo

JlenVideo 是一个基于 Android 原生技术栈实现的苹果 CMS 视频客户端。  
当前工程已经完成多模块化改造，主目标是把启动壳、基础能力、页面功能、状态管理、播放器能力和历史兼容实现分开，方便后续继续维护和迭代。

## 当前状态

- 项目名：`JlenVideo`
- 应用包名：`top.jlen.vod`
- 当前版本：`2.1.0.2`
- 当前版本号：`25`
- 默认站点：`https://cms.jlen.top/`
- 回退站点：`http://82.157.207.243:39888/`
- 最低 Android 版本：`24`
- 目标 Android 版本：`34`
- JDK / Kotlin JVM Target：`17`

## 技术栈

- Android + Kotlin
- Jetpack Compose
- Gradle Kotlin DSL
- 多模块工程结构

## 目录结构

### 根目录

- [settings.gradle.kts](/F:/codex/1/settings.gradle.kts)
  统一声明所有 Gradle 模块。
- [build.gradle.kts](/F:/codex/1/build.gradle.kts)
  根工程构建配置。
- [gradle.properties](/F:/codex/1/gradle.properties)
  Android SDK、版本号、站点地址等全局参数。
- [memory.md](/F:/codex/1/memory.md)
  当前协作规则与发布规则记忆文件。
- [templates](/F:/codex/1/templates)
  项目附带的站点模板与模板压缩包，不参与 Android 主构建。

### app

`app` 现在只承担 Android 应用壳层职责。

- [MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/activity/MainActivity.kt)
  应用启动入口 Activity。
- [JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/bootstrap/application/JlenVideoApplication.kt)
  Application 入口。
- [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)
  应用模块构建配置，负责 APK 命名为 `JlenVideo-版本号-debug.apk`。

### core:model

纯数据模型层。

- [Models.kt](/F:/codex/1/core/model/src/main/java/top/jlen/vod/data/shared/model/Models.kt)
  苹果 CMS 数据模型、视频模型、分类模型等共享数据定义。

### core:common

通用配置与基础运行时能力。

- [AppConfig.kt](/F:/codex/1/core/common/src/main/java/top/jlen/vod/config/runtime/app/AppConfig.kt)
  运行时应用配置读取。
- [CrashLogger.kt](/F:/codex/1/core/common/src/main/java/top/jlen/vod/logging/crash/handler/CrashLogger.kt)
  崩溃日志记录与读取。

### core:design

共享 UI 风格与动效常量。

- [UiPalette.kt](/F:/codex/1/core/design/src/main/java/top/jlen/vod/ui/theme/palette/system/UiPalette.kt)
  全局色板与主题颜色。
- [UiMotion.kt](/F:/codex/1/core/design/src/main/java/top/jlen/vod/ui/motion/spec/system/UiMotion.kt)
  通用动画和动效时长配置。

### core:data

苹果 CMS 数据访问层，负责接口、缓存、Cookie、搜索历史、解析 support，以及历史兼容的 repository runtime。

#### 对外壳层

- [AppleCmsApi.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/api/service/main/AppleCmsApi.kt)
  API 定义与网络入口。
- [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)
  对外仓库壳层。
- [AppleCmsRepositorySupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/shell/cms/AppleCmsRepositorySupport.kt)
  对外解析 support 壳层。
- [PersistentCookieJar.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/storage/cookie/persistent/PersistentCookieJar.kt)
  Cookie 持久化。
- [SearchHistoryStore.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/storage/history/local/SearchHistoryStore.kt)
  搜索历史持久化。

#### runtime support

- [AppleCmsSupportModels.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/runtime/models/shared/AppleCmsSupportModels.kt)
  runtime 层共用的 support 模型。
- [AppleCmsJsonObjectSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/runtime/parsing/json/AppleCmsJsonObjectSupport.kt)
  `JsonObject` 解析辅助。
- [AppleCmsUrlParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/runtime/parsing/url/AppleCmsUrlParsingSupport.kt)
  URL、媒体地址、播放器配置解析辅助。

#### legacy runtime

这一层是历史实现兼容区，当前已经完成壳层化与职责分层，便于继续渐进式重构。

- [LegacyAppleCmsRuntimeRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/shell/LegacyAppleCmsRuntimeRepository.kt)
  legacy repository 壳层。
- [LegacyAppleCmsRuntimeRepositoryCore.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/runtime/core/LegacyAppleCmsRuntimeRepositoryCore.kt)
  legacy repository 核心正文实现。
- [LegacyAppleCmsRuntimeRepositoryModels.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/models/LegacyAppleCmsRuntimeRepositoryModels.kt)
  legacy repository 内部运行时模型。
- [LegacyAppleCmsRuntimeHomeActions.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/actions/content/LegacyAppleCmsRuntimeHomeActions.kt)
  首页与缓存加载正文。
- [LegacyAppleCmsRuntimeCategoryActions.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/actions/content/LegacyAppleCmsRuntimeCategoryActions.kt)
  分类与分页正文。
- [LegacyAppleCmsRuntimeAuthNoticeActions.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/actions/account/LegacyAppleCmsRuntimeAuthNoticeActions.kt)
  认证、公告、心跳正文。
- [LegacyAppleCmsRuntimeUserActions.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/actions/account/LegacyAppleCmsRuntimeUserActions.kt)
  用户资料、会员、记录动作正文。
- [LegacyAppleCmsRuntimeSearchActions.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/actions/media/LegacyAppleCmsRuntimeSearchActions.kt)
  搜索正文。
- [LegacyAppleCmsRuntimeDetailPlayActions.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/actions/media/LegacyAppleCmsRuntimeDetailPlayActions.kt)
  详情、来源、播放解析正文。

#### legacy parsing

- [LegacyAppleCmsRuntimeParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/shell/LegacyAppleCmsRuntimeParsingSupport.kt)
  legacy parsing 壳层。
- [LegacyAppleCmsRuntimeApiParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/api/LegacyAppleCmsRuntimeApiParsingSupport.kt)
  API 返回与用户中心接口解析。
- [LegacyAppleCmsRuntimeCategoryDetailParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/category/LegacyAppleCmsRuntimeCategoryDetailParsingSupport.kt)
  分类与详情元数据解析。
- [LegacyAppleCmsRuntimeDetailMatchingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/detail/LegacyAppleCmsRuntimeDetailMatchingSupport.kt)
  详情预览匹配、标题归一化、候选评分。
- [LegacyAppleCmsRuntimeNoticeParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/account/LegacyAppleCmsRuntimeNoticeParsingSupport.kt)
  公告解析。
- [LegacyAppleCmsRuntimePagingMembershipParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/account/LegacyAppleCmsRuntimePagingMembershipParsingSupport.kt)
  分页和会员解析。
- [LegacyAppleCmsRuntimeProfileParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/account/LegacyAppleCmsRuntimeProfileParsingSupport.kt)
  资料字段解析。
- [LegacyAppleCmsRuntimeUserCenterParsingSupport.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/account/LegacyAppleCmsRuntimeUserCenterParsingSupport.kt)
  用户中心 HTML / JSON 数据解析。

### feature:common

共享 UI 状态、公共组件和外链工具。

- [HomeUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/home/main/HomeUiState.kt)
  首页状态模型。
- [SearchUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/search/main/SearchUiState.kt)
  搜索状态模型。
- [AccountUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/account/main/AccountUiState.kt)
  账号状态模型。
- [DetailUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/detail/main/DetailUiState.kt)
  详情状态模型。
- [PlayerUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/player/main/PlayerUiState.kt)
  播放器状态模型。
- [NoticeUiState.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/state/models/notice/main/NoticeUiState.kt)
  公告状态模型。
- [SectionTitle.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/section/title/main/SectionTitle.kt)
  通用分区标题组件。
- [FeedbackPanes.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/feedback/panes/main/FeedbackPanes.kt)
  通用空态、加载态、错误态组件。
- [ErrorBanner.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/feedback/banner/main/ErrorBanner.kt)
  通用错误横幅组件。
- [PosterRequest.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/components/poster/request/main/PosterRequest.kt)
  封面请求与图片加载辅助。
- [ExternalLinkHelper.kt](/F:/codex/1/feature/common/src/main/java/top/jlen/vod/ui/link/external/browser/main/ExternalLinkHelper.kt)
  外部链接打开辅助。

### feature:browse

首页、片库、搜索、公告、账号页。

- [BrowseHomeCategory.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/home/screen/main/BrowseHomeCategory.kt)
  首页与分类页 UI。
- [BrowseSearch.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/search/screen/main/BrowseSearch.kt)
  搜索页 UI。
- [BrowseAnnouncements.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/announcements/screen/main/BrowseAnnouncements.kt)
  公告页 UI。
- [BrowseAccount.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/screen/main/BrowseAccount.kt)
  账号页入口壳。
- [LegacyBrowseAccount.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/legacy/LegacyBrowseAccount.kt)
  账号页 legacy UI 主实现。
- [AccountAboutPane.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/pane/about/AccountAboutPane.kt)
  关于页壳层。
- [AccountAuthPaneWrappers.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/pane/auth/AccountAuthPaneWrappers.kt)
  登录、注册、找回密码入口壳。
- [AccountProfilePaneWrappers.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/pane/profile/AccountProfilePaneWrappers.kt)
  资料页入口壳。
- [AccountRecordPane.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/pane/record/AccountRecordPane.kt)
  收藏/历史页入口壳。
- [AccountMembershipPane.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/account/pane/membership/AccountMembershipPane.kt)
  会员页入口壳。
- [BrowseScreens.kt](/F:/codex/1/feature/browse/src/main/java/top/jlen/vod/ui/shared/components/layout/BrowseScreens.kt)
  browse 模块共享布局和辅助组件。

### feature:detail

详情页与内嵌播放器页面。

- [DetailScreen.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/detail/screen/main/DetailScreen.kt)
  详情页 UI。
- [PlayerScreen.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/player/screen/main/PlayerScreen.kt)
  内嵌播放器页面 UI。
- [DetailPlayerScreens.kt](/F:/codex/1/feature/detail/src/main/java/top/jlen/vod/ui/shared/components/layout/DetailPlayerScreens.kt)
  detail 模块共享组件。

### feature:player

原生播放器、全屏播放器、解析支持。

- [NativeVideoPlayer.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt)
  Compose 原生播放器主体。
- [FullscreenPlayerActivity.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/fullscreen/activity/main/FullscreenPlayerActivity.kt)
  全屏播放器 Activity。
- [FullscreenPlaybackResult.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/fullscreen/contract/result/FullscreenPlaybackResult.kt)
  全屏返回结果契约。
- [PlaybackSnapshot.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/shared/state/playback/current/PlaybackSnapshot.kt)
  播放快照状态。
- [HiddenStreamResolver.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/resolver/support/stream/HiddenStreamResolver.kt)
  隐藏流解析支持。
- [PlayerWebSupport.kt](/F:/codex/1/feature/player/src/main/java/top/jlen/vod/ui/web/support/page/PlayerWebSupport.kt)
  Web 播放回退支持。

### feature:shell

应用导航壳层。

- [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)
  Compose 应用导航入口。

### feature:state

状态管理、业务调度和 legacy ViewModel runtime。

#### 对外壳层

- [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)
  对外 ViewModel 壳层。

#### support

`feature:state` 下的 `support` 目录存放纯状态构造和运行态辅助逻辑，已经按 `account / detail / home / notice / player / search` 分开。

#### legacy runtime

- [LegacyStateRuntimeViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/shell/LegacyStateRuntimeViewModel.kt)
  legacy ViewModel 壳层。
- [LegacyStateRuntimeViewModelCore.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/runtime/core/LegacyStateRuntimeViewModelCore.kt)
  legacy ViewModel 核心正文实现。
- [LegacyStateRuntimeAccountNoticeActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/account/LegacyStateRuntimeAccountNoticeActions.kt)
  账号初始化、公告、更新检查正文。
- [LegacyStateRuntimeAccountSectionActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/account/LegacyStateRuntimeAccountSectionActions.kt)
  账号分区切换正文。
- [LegacyStateRuntimeAccountAuthActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/account/LegacyStateRuntimeAccountAuthActions.kt)
  登录注册找回密码正文。
- [LegacyStateRuntimeAccountMutationActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/account/LegacyStateRuntimeAccountMutationActions.kt)
  资料保存、邮箱绑定、删除记录等正文。
- [LegacyStateRuntimeAccountContentActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/account/LegacyStateRuntimeAccountContentActions.kt)
  资料、会员、收藏、历史内容加载正文。
- [LegacyStateRuntimeHomeActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/content/LegacyStateRuntimeHomeActions.kt)
  首页和分类正文。
- [LegacyStateRuntimeSearchActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/content/LegacyStateRuntimeSearchActions.kt)
  搜索正文。
- [LegacyStateRuntimeDetailActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/detail/LegacyStateRuntimeDetailActions.kt)
  详情、来源、历史恢复正文。
- [LegacyStateRuntimePlayerActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/player/LegacyStateRuntimePlayerActions.kt)
  播放器解析、选集、全屏同步正文。
- [LegacyStateRuntimeSharedActions.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/shared/LegacyStateRuntimeSharedActions.kt)
  legacy ViewModel 共用辅助动作。

## 当前工程约定

- APK 命名格式：`JlenVideo-版本号-debug.apk`
- 小改动也会自动提交并推送
- 发布版本默认包含：改版本号、编译 APK、提交推送、创建 GitHub Release、上传 APK
- GitHub Release 文案使用 UTF-8 文件和 `--notes-file`

## 构建与运行

### 常用命令

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

### 当前 debug APK

- [JlenVideo-2.1.0.2-debug.apk](/F:/codex/1/app/build/outputs/apk/debug/JlenVideo-2.1.0.2-debug.apk)

## 推荐阅读顺序

如果要理解当前工程，建议按这个顺序看：

1. [settings.gradle.kts](/F:/codex/1/settings.gradle.kts)
2. [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)
3. [JlenVideoApp.kt](/F:/codex/1/feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt)
4. [AppViewModel.kt](/F:/codex/1/feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt)
5. [AppleCmsRepository.kt](/F:/codex/1/core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt)
6. 然后再进入各个 `legacy/runtime`、`actions`、`support` 目录看具体实现

## 当前多模块目标

当前结构的目标不是把历史实现全部一次性删除，而是：

- 对外入口保持稳定
- 新逻辑优先落在清晰的模块边界里
- 历史实现通过 `legacy/runtime` 逐步收口和替换
- 在不影响现有功能的前提下，持续降低后续维护成本
