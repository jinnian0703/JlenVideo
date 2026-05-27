# JlenVideo

JlenVideo 是一个基于 Kotlin、Jetpack Compose 和 Media3 的 Android 视频客户端，用于浏览、搜索和播放苹果 CMS 站点提供的影视内容。

当前工程已经完成 `app / core / feature` 多模块拆分，代码按数据、设计、播放、页面、导航和状态调度分层，便于继续维护和扩展。

## 项目信息

| 项目 | 值 |
| --- | --- |
| Application Id | `top.jlen.vod` |
| 当前版本 | `2.1.1.8` |
| 当前 versionCode | `34` |
| minSdk | `24` |
| targetSdk | `34` |
| compileSdk | `34` |
| JVM Target | `17` |
| 默认站点 | `https://cms.jlen.top/` |

相关仓库：

| 仓库 | 作用 |
| --- | --- |
| [maccms-pure-video-api](https://github.com/jinnian0703/maccms-pure-video-api) | 客户端使用的影视数据 API，负责苹果 CMS 内容接口、搜索、详情和播放数据适配。 |
| [appcenter-standalone-admin](https://github.com/jinnian0703/appcenter-standalone-admin) | 独立后台管理系统，负责应用公告、版本发布、心跳设备信息和运营工具管理。 |

## 功能概览

- 首页精选、最近更新、分类片库和搜索结果浏览
- 视频详情、播放线路切换、选集切换和播放进度同步
- 追剧入口，集中管理关注内容和续播状态
- 账号登录、注册、找回密码、资料编辑、邮箱绑定和会员签到
- 播放记录、积分日志、会员状态和账号总览
- 首次启动用户协议与首登引导
- 公告列表、公告弹窗、公告详情和 HTML 富文本展示
- 应用更新检查、发布页跳转和崩溃日志查看/清理

## 开发引导

新接手项目时可以先阅读 [JlenVideo 开发引导](docs/guide.md)，里面整理了推荐阅读顺序、常见问题定位入口和模块改动建议。

## 技术栈

- Kotlin
- Jetpack Compose
- AndroidX Lifecycle / ViewModel
- Navigation Compose
- Media3 ExoPlayer
- Retrofit / OkHttp
- Gson
- Jsoup
- Coil
- Gradle Kotlin DSL

## 模块结构

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

可以按四组理解这套结构：

- `app` 是最终装配层，只放 Application、Activity 和打包配置。
- `core:*` 是基础能力层，放模型、配置、视觉规范、数据访问和站点解析。
- `feature:*` 是业务功能层，放可见页面、播放器、共享组件和状态调度。
- `feature:shell` 是应用壳层，负责把各业务模块接到顶层导航、底栏、引导和全局弹窗上。

整体依赖方向保持从外到内：

```text
app
└─ feature:shell
   ├─ feature:browse
   ├─ feature:detail
   ├─ feature:player
   ├─ feature:state
   └─ feature:common
      └─ core:*
```

实际 Gradle 依赖以各模块 `build.gradle.kts` 为准。维护时尽量让 `core:*` 保持可复用，避免反向依赖具体页面；页面模块负责展示和交互，接口请求、站点解析和缓存逻辑优先留在 `core:data`。

### `app`

最终应用壳层，负责 Android Application、Activity 入口和打包配置。

关键文件：

- `app/src/main/java/top/jlen/vod/bootstrap/activity/MainActivity.kt`
- `app/src/main/java/top/jlen/vod/bootstrap/application/JlenVideoApplication.kt`
- `app/build.gradle.kts`

### `core:model`

共享数据模型层，包含视频、分类、用户、公告、更新信息等模型。

关键文件：

- `core/model/src/main/java/top/jlen/vod/data/shared/model/Models.kt`

### `core:common`

通用运行时配置和基础能力，包括应用版本信息、站点配置和崩溃日志。

关键文件：

- `core/common/src/main/java/top/jlen/vod/config/runtime/app/AppConfig.kt`
- `core/common/src/main/java/top/jlen/vod/logging/crash/handler/CrashLogger.kt`

### `core:design`

共享视觉规范，包括颜色、动效和尺寸 token。

关键文件：

- `core/design/src/main/java/top/jlen/vod/ui/theme/palette/system/UiPalette.kt`
- `core/design/src/main/java/top/jlen/vod/ui/theme/dimens/system/UiDimens.kt`
- `core/design/src/main/java/top/jlen/vod/ui/motion/spec/system/UiMotion.kt`

### `core:data`

数据访问和解析层，负责接口请求、HTML/JSON 解析、Cookie、搜索历史和 legacy repository。

关键文件：

- `core/data/src/main/java/top/jlen/vod/data/api/service/main/AppleCmsApi.kt`
- `core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt`
- `core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/shell/LegacyAppleCmsRuntimeRepository.kt`
- `core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/runtime/core/LegacyAppleCmsRuntimeRepositoryCore.kt`

### `feature:common`

跨页面复用的 UI 组件和状态对象，包括空状态、错误提示、验证码、海报角标和展开文本。

### `feature:browse`

浏览侧页面模块，包含首页、片库、搜索、追剧、公告和账号页。

关键文件：

- `feature/browse/src/main/java/top/jlen/vod/ui/home/screen/main/BrowseHomeCategory.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/search/screen/main/BrowseSearch.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/follow/screen/main/BrowseFollow.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/announcements/screen/main/BrowseAnnouncements.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/account/screen/main/BrowseAccount.kt`

### `feature:detail`

视频详情页和内嵌播放页 UI。

关键文件：

- `feature/detail/src/main/java/top/jlen/vod/ui/detail/screen/main/DetailScreen.kt`
- `feature/detail/src/main/java/top/jlen/vod/ui/player/screen/main/PlayerScreen.kt`

### `feature:player`

播放器能力模块，包含 Media3 播放器、全屏播放、手势控制和隐藏播放地址解析。

关键文件：

- `feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt`
- `feature/player/src/main/java/top/jlen/vod/ui/fullscreen/activity/main/FullscreenPlayerActivity.kt`
- `feature/player/src/main/java/top/jlen/vod/ui/resolver/support/stream/HiddenStreamResolver.kt`

### `feature:shell`

应用导航壳层，负责顶层导航、底栏、首次启动引导和全局弹窗挂载。

关键文件：

- `feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt`
- `feature/shell/src/main/java/top/jlen/vod/ui/navigation/onboarding/OnboardingScreens.kt`
- `feature/shell/src/main/java/top/jlen/vod/ui/navigation/dialogs/AppDialogs.kt`

### `feature:state`

状态调度和业务桥接层，对外提供 ViewModel，内部按业务动作拆分首页、搜索、账号、播放等状态逻辑。

关键文件：

- `feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt`
- `feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/shell/LegacyStateRuntimeViewModel.kt`
- `feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/runtime/core/LegacyStateRuntimeViewModelCore.kt`

## 代码组织约定

- `shell`：稳定入口，尽量保持轻量。
- `runtime`：实际运行时实现。
- `core`：runtime 内部核心逻辑。
- `legacy`：仍在运行的历史实现兼容区。
- `support`：解析、工具和状态构造辅助层。
- `actions`：按业务动作拆分的状态或数据操作。
- `models`：内部数据模型，避免正文文件混放大量数据类。

## 维护建议

- 新页面优先放入对应 `feature:*` 模块。
- 新数据模型优先放入 `core:model`。
- 新接口、站点解析和 repository 逻辑优先放入 `core:data`。
- 新通用 UI 或共享状态优先放入 `feature:common`。
- 顶层导航、引导和全局弹窗优先收敛到 `feature:shell`。
- 避免在页面组件里直接写站点解析逻辑。

## 许可证

本项目使用 [MIT License](LICENSE)。
