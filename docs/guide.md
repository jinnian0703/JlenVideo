# JlenVideo 开发引导

这份文档用于帮助新接手项目的人快速理解代码结构、阅读顺序和常见改动入口。README 保持项目概览，这里放更细的开发引导。

## 先了解整体分层

JlenVideo 按多模块组织：

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

模块职责可以按下面的边界判断：

- `app`：只负责最终 Android 应用装配，不承载业务页面和站点逻辑。
- `core:model`：放跨模块共享的数据模型，避免各页面重复定义同类数据。
- `core:common`：放应用运行时配置、版本信息、日志等不依赖 UI 的基础能力。
- `core:design`：放视觉 token、颜色、尺寸和动效规则。
- `core:data`：放接口、仓库、缓存、Cookie、HTML/JSON 解析和站点兼容逻辑。
- `feature:common`：放跨页面复用的 Compose 组件、展示辅助和 UI 状态模型。
- `feature:browse`：放首页、片库、搜索、追剧、公告、账号等浏览侧页面。
- `feature:detail`：放详情页和内嵌播放页。
- `feature:player`：放播放器实现、全屏播放、手势控制和播放地址解析。
- `feature:shell`：放顶层导航、底栏、首次启动引导和全局弹窗挂载。
- `feature:state`：放 ViewModel 和业务动作调度，连接 UI 事件与数据仓库。

依赖方向上，`app` 装配 `feature:shell`，`feature:shell` 串联页面、状态和播放器；页面模块可以依赖 `feature:common`、`core:design`、`core:data`，但 `core:*` 不应依赖具体页面。新增能力时先判断“模型、数据、视觉、通用组件、具体页面、状态调度”分别属于哪一层，避免把业务逻辑堆到页面或顶层导航里。

推荐先按下面的顺序理解：

1. `settings.gradle.kts`：确认项目模块。
2. `gradle.properties`：确认应用版本、SDK 和默认站点配置。
3. `app/build.gradle.kts`：确认应用壳层、版本读取和 APK 命名逻辑。
4. `feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt`：理解导航、底栏、全局弹窗和页面入口。
5. `feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt`：理解页面调用的状态入口。
6. `core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt`：理解数据仓库外壳。
7. `feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt`：理解播放器核心交互。

## 环境准备

建议使用以下环境：

- JDK 17
- Android Studio 或完整 Android SDK
- Windows PowerShell
- Git
- GitHub CLI，用于发布 GitHub Release 时上传 APK

项目的 SDK、版本号和默认站点配置集中在 `gradle.properties`：

```properties
ANDROID_COMPILE_SDK=34
ANDROID_MIN_SDK=24
ANDROID_TARGET_SDK=34
APP_APPLICATION_ID=top.jlen.vod
APP_VERSION_CODE=34
APP_VERSION_NAME=2.1.1.8
APPLE_CMS_BASE_URL=https://cms.jlen.top/
```

本地 `local.properties` 通常由 Android Studio 或 Android SDK 自动生成，不应提交个人机器路径。

## 构建与验证

常用验证命令：

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:lintDebug
```

推荐使用顺序：

1. 普通 Kotlin 或 Compose 改动，先运行 `:app:compileDebugKotlin`。
2. 涉及资源、Manifest、版本号、打包配置或需要交付 APK 时，运行 `:app:assembleRelease`。
3. 需要检查静态问题时，运行 `:app:lintDebug`。

Release APK 会按版本名生成在：

```text
app/build/outputs/apk/release/JlenVideo-版本号-release.apk
```

如果只改文档，通常不需要运行 Android 编译。

## 常见问题从哪里看

### 页面导航、底栏和弹窗

优先看：

- `feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt`
- `feature/shell/src/main/java/top/jlen/vod/ui/navigation/dialogs/AppDialogs.kt`
- `feature/shell/src/main/java/top/jlen/vod/ui/navigation/onboarding/OnboardingScreens.kt`

适合排查：

- 首次启动协议和登录引导
- 底栏切换和双击回顶
- 公告弹窗、更新弹窗、确认弹窗
- 页面路由跳转异常

### 首页、片库、搜索、追剧和账号页

优先看：

- `feature/browse/src/main/java/top/jlen/vod/ui/home/screen/main/BrowseHomeCategory.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/search/screen/main/BrowseSearch.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/follow/screen/main/BrowseFollow.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/account/screen/main/BrowseAccount.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/account/legacy/LegacyBrowseAccount.kt`

适合排查：

- 首页列表和分类列表展示
- 搜索输入、搜索记录、搜索结果分页
- 追剧内容和续播状态
- 登录、注册、找回密码、会员、积分、播放记录

### 公告和富文本

优先看：

- `feature/browse/src/main/java/top/jlen/vod/ui/announcements/screen/main/BrowseAnnouncements.kt`
- `feature/browse/src/main/java/top/jlen/vod/ui/announcements/rich/AnnouncementRichContent.kt`
- `core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/account/LegacyAppleCmsRuntimeNoticeParsingSupport.kt`

适合排查：

- 公告列表和公告详情
- 公告弹窗展示
- HTML 富文本解析
- 链接点击
- 永不过期公告状态

### 详情页和播放页

优先看：

- `feature/detail/src/main/java/top/jlen/vod/ui/detail/screen/main/DetailScreen.kt`
- `feature/detail/src/main/java/top/jlen/vod/ui/player/screen/main/PlayerScreen.kt`
- `feature/detail/src/main/java/top/jlen/vod/ui/shared/components/layout/DetailPlayerScreens.kt`

适合排查：

- 视频简介、封面、角标、播放线路
- 选集展示
- 详情页续播
- 内嵌播放页布局

### 播放器和播放地址解析

优先看：

- `feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt`
- `feature/player/src/main/java/top/jlen/vod/ui/fullscreen/activity/main/FullscreenPlayerActivity.kt`
- `feature/player/src/main/java/top/jlen/vod/ui/resolver/support/stream/HiddenStreamResolver.kt`

适合排查：

- Media3 播放状态
- 全屏播放
- 手势和控制层
- Web 兜底播放地址解析
- 播放源不支持提示

### 数据请求和站点解析

优先看：

- `core/data/src/main/java/top/jlen/vod/data/api/service/main/AppleCmsApi.kt`
- `core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt`
- `core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/shell/LegacyAppleCmsRuntimeRepository.kt`
- `core/data/src/main/java/top/jlen/vod/data/repository/legacy/runtime/cms/runtime/core/LegacyAppleCmsRuntimeRepositoryCore.kt`

适合排查：

- 分类、搜索、详情、播放接口
- HTML 或 JSON 字段变化
- 登录、会员、积分、公告、更新接口
- 心跳上报和设备信息

## 改动建议

- 新页面放到对应 `feature:*` 模块，不要放进 `app`。
- 新通用 UI 优先放到 `feature:common`。
- 新视觉 token 优先放到 `core:design`。
- 新数据模型优先放到 `core:model`。
- 新接口和解析逻辑优先放到 `core:data`。
- 只在 `feature:shell` 管顶层导航、首次启动流程和全局弹窗。
- 页面层避免直接写站点解析逻辑。

## 常见修改路径

### 修改页面 UI

1. 先确认页面属于哪个 `feature:*` 模块。
2. 优先复用 `core:design` 的颜色、尺寸和动效 token。
3. 如果组件会跨页面复用，放到 `feature:common`。
4. 页面只负责展示和回调，不直接写接口解析逻辑。
5. 修改后至少运行 `:app:compileDebugKotlin`。

常见入口：

- 首页/片库：`feature/browse/src/main/java/top/jlen/vod/ui/home/screen/main/BrowseHomeCategory.kt`
- 搜索：`feature/browse/src/main/java/top/jlen/vod/ui/search/screen/main/BrowseSearch.kt`
- 追剧：`feature/browse/src/main/java/top/jlen/vod/ui/follow/screen/main/BrowseFollow.kt`
- 账号：`feature/browse/src/main/java/top/jlen/vod/ui/account/legacy/LegacyBrowseAccount.kt`
- 详情：`feature/detail/src/main/java/top/jlen/vod/ui/detail/screen/main/DetailScreen.kt`
- 播放：`feature/detail/src/main/java/top/jlen/vod/ui/player/screen/main/PlayerScreen.kt`

### 修改状态逻辑

1. 页面回调通常先进 `AppViewModel`。
2. 具体业务逻辑优先找 `feature:state` 下的 `actions` 文件。
3. 不要把新的业务正文堆进 `JlenVideoApp` 或单个页面文件。
4. 涉及列表滚动位置、登录状态、播放状态时，注意现有状态是否需要保存或恢复。

常见入口：

- ViewModel 外壳：`feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt`
- 状态核心：`feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/runtime/core/LegacyStateRuntimeViewModelCore.kt`
- 首页动作：`feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/content/LegacyStateRuntimeHomeActions.kt`
- 搜索动作：`feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/content/LegacyStateRuntimeSearchActions.kt`
- 账号动作：`feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/account`
- 播放动作：`feature/state/src/main/java/top/jlen/vod/ui/viewmodel/legacy/state/actions/player`

### 修改接口或解析

1. 先判断是 API 字段变化、HTML 结构变化，还是客户端展示逻辑问题。
2. API 壳层优先看 `AppleCmsApi` 和 `AppleCmsRepository`。
3. legacy 解析优先看 `LegacyAppleCmsRuntimeRepositoryCore` 及 `core/data/.../support` 下的解析辅助文件。
4. 不要在 Compose 页面里直接解析 HTML 或 JSON。

修改后建议验证：

- 首页加载
- 分类分页
- 搜索结果
- 详情信息
- 播放线路和选集
- 登录/会员/公告等账号相关接口

### 修改公告和更新

公告相关：

- 列表和详情：`feature/browse/src/main/java/top/jlen/vod/ui/announcements/screen/main/BrowseAnnouncements.kt`
- 富文本：`feature/browse/src/main/java/top/jlen/vod/ui/announcements/rich/AnnouncementRichContent.kt`
- 弹窗：`feature/shell/src/main/java/top/jlen/vod/ui/navigation/dialogs/AppDialogs.kt`
- 解析：`core/data/src/main/java/top/jlen/vod/data/support/parsing/legacy/runtime/cms/support/account/LegacyAppleCmsRuntimeNoticeParsingSupport.kt`

更新检查相关：

- UI：账号工具中心和 `AppDialogs.kt`
- 数据：`LegacyAppleCmsRuntimeRepositoryCore.loadLatestRelease`
- Release 发布：GitHub Release 页面和 APK 附件

### 修改播放器

播放器改动风险较高，建议小步验证：

1. 内嵌播放能否进入。
2. 全屏播放能否进入和返回。
3. 播放线路切换是否保留正确状态。
4. 选集切换是否写入播放记录。
5. Web 兜底线路是否还能识别真实播放地址。

主要入口：

- `feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt`
- `feature/player/src/main/java/top/jlen/vod/ui/fullscreen/activity/main/FullscreenPlayerActivity.kt`
- `feature/player/src/main/java/top/jlen/vod/ui/resolver/support/stream/HiddenStreamResolver.kt`
- `feature/detail/src/main/java/top/jlen/vod/ui/player/screen/main/PlayerScreen.kt`

## 版本与发布

版本信息在 `gradle.properties`：

```properties
APP_VERSION_CODE=34
APP_VERSION_NAME=2.1.1.8
```

发布新版本时通常需要：

1. 更新 `APP_VERSION_NAME`。
2. 递增 `APP_VERSION_CODE`。
3. 更新 README 中的当前版本信息。
4. 运行 `:app:compileDebugKotlin` 和 `:app:assembleRelease`。
5. 提交并推送源码。
6. 创建 GitHub Release。
7. 上传 `JlenVideo-版本号-release.apk`。
8. 验证 Release 中文说明和 APK 附件显示正常。

发布说明建议单独使用 UTF-8 Markdown 文件传给 GitHub CLI，避免 PowerShell 中直接内联中文导致编码问题。

## 排查清单

### 编译失败

- 先看失败模块名称，是 `app`、`core:*` 还是 `feature:*`。
- 如果是 import 或依赖方向问题，检查模块间依赖是否合理。
- 如果是 Compose 参数签名问题，检查调用方是否都同步更新。
- 如果是资源或 Manifest 问题，运行完整 `:app:assembleRelease` 更容易暴露。

### 页面卡顿

- 检查滚动列表是否使用稳定 key 和合理 contentType。
- 避免在列表 item 内创建无限动画、大对象或重复正则。
- 海报列表优先使用轻量静态占位。
- 长列表避免在单个 Lazy item 里一次性渲染大量 Column 子项。

### 数据显示不完整

- 先确认后端字段是否完整。
- 再看 `core:model` 是否接收了对应字段。
- 再看 `core:data` 解析是否丢字段。
- 最后看 UI 是否有 `maxLines`、截断、展开/收起或摘要 fallback。

### 播放源不可用

- 先确认该线路返回的是直链、m3u8、网页播放器还是隐藏地址。
- 直链问题看 Media3 错误。
- 网页兜底问题看 `HiddenStreamResolver`。
- 如果多条线路都不可用，需要确认站点源本身是否变更。

## 命名约定

- `shell`：稳定入口，保持轻量。
- `runtime`：实际运行时实现。
- `core`：runtime 内部核心正文。
- `legacy`：仍在运行的历史实现兼容区。
- `support`：解析、工具和状态构造辅助层。
- `actions`：按业务动作拆分的状态或数据操作。
- `models`：内部数据模型。
