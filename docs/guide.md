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

推荐先按下面的顺序理解：

1. `settings.gradle.kts`：确认项目模块。
2. `gradle.properties`：确认应用版本、SDK 和默认站点配置。
3. `app/build.gradle.kts`：确认应用壳层、版本读取和 APK 命名逻辑。
4. `feature/shell/src/main/java/top/jlen/vod/ui/navigation/app/main/JlenVideoApp.kt`：理解导航、底栏、全局弹窗和页面入口。
5. `feature/state/src/main/java/top/jlen/vod/ui/viewmodel/shell/AppViewModel.kt`：理解页面调用的状态入口。
6. `core/data/src/main/java/top/jlen/vod/data/repository/shell/cms/AppleCmsRepository.kt`：理解数据仓库外壳。
7. `feature/player/src/main/java/top/jlen/vod/ui/nativeplayer/view/main/NativeVideoPlayer.kt`：理解播放器核心交互。

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

## 命名约定

- `shell`：稳定入口，保持轻量。
- `runtime`：实际运行时实现。
- `core`：runtime 内部核心正文。
- `legacy`：仍在运行的历史实现兼容区。
- `support`：解析、工具和状态构造辅助层。
- `actions`：按业务动作拆分的状态或数据操作。
- `models`：内部数据模型。
