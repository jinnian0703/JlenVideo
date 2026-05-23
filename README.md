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
| 当前版本 | `2.1.1.6` |
| 当前 versionCode | `32` |
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

## 5. APK 输出

当前 debug APK：

- [JlenVideo-2.1.1.6-debug.apk](/F:/codex/1/app/build/outputs/apk/debug/JlenVideo-2.1.1.6-debug.apk)

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
- 当前版本为 `2.1.1.6 (32)`
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

## 12. 2.1.1.6 更新说明

- 公告内容支持 HTML 富文本渲染，标题、段落、加粗、颜色、列表和对齐等格式可在客户端正常展示
- 公告详情页、公告提醒弹窗和首页公告入口中的链接支持点击，并通过系统外部浏览器打开
- 更新弹窗和“我的-关于”中的更新说明支持富文本展示
- 账号页更新说明支持展开/收起，展开入口统一放在右侧
- 首页、片库、搜索页和搜索结果页支持双击底部对应导航项回到页面顶部
- 优化搜索记录区域布局，减少卡片内重复信息
- 统一部分页面的卡片圆角、按钮高度、间距、空状态和错误重试样式
- 优化海报角标规则，电影状态、剧集集数等信息在首页、片库、搜索、追剧等页面展示更一致
- 优化详情页简介展示，优先显示完整视频简介，并支持长文本展开/收起
- 降低首页、片库、搜索、追剧和账号记录等列表滑动时的卡顿感
- 拆分部分公共 UI 组件和弹窗组件，降低页面文件复杂度
- 修复 GitHub API 受限时检查更新失败的问题，增加发布页解析兜底
- 修复部分 Web 播放线路没有正确识别兜底播放地址的问题
- 修复后台设置为永不过期的公告被误判为历史公告的问题
- 修复永不过期公告不参与弹窗展示的问题
- 修复公告内容中 Markdown HTML 代码块标记被直接显示的问题
- 修复部分电影海报角标状态丢失的问题
- 修复账号页提示信息列表在部分场景显示不稳定的问题

---

## 13. 2.1.1.5 更新说明

- 新增首次启动用户协议与首登引导：首次打开先确认协议，再进入登录引导；登录可跳过，协议不可跳过
- 首登页支持在当前页完成登录、注册和找回密码，避免跳转到“我的”页打断流程
- 用户协议拒绝流程改为应用内确认弹窗，确认后退出应用
- “我的”页登录后新增账号总览，集中展示用户组、用户 ID、会员到期、积分、签到状态和播放记录数量
- 账号总览新增快捷入口：资料编辑、邮箱绑定、会员签到、积分日志、去追剧、反馈与日志
- 优化未登录“我的”页：去掉顶部横排入口，保留单一登录主按钮，注册、找回密码、关于与日志作为辅助入口
- 调整未登录文案，减少重复说明，突出追剧同步、播放记录、会员积分等具体账号能力
- 注册和找回密码成功后自动回到登录页，预填账号并显示明确成功提示
- 统一账号操作反馈，输入内容变更时自动清理旧错误或成功提示
- “关于与日志”整理为工具中心，分组展示版本更新、发布页、用户协议与隐私说明、崩溃日志刷新与清理
- 播放记录清空增加应用内确认弹窗，确认后才执行清空操作
- 修复续播记录按播放源区分的问题，避免不同来源之间互相覆盖进度

---

## 14. 2.1.1.3 更新说明

- 优化搜索联想请求与面板展示，减少等待感并兼容旧接口兜底
- 修复沉浸式页面顶部安全区，公告页与详情页头部显示更稳定
- 新增底栏一级入口“追剧”，聚合已加入追剧内容的更新与续播状态
- 统一空状态、错误提示和海报失败态的轻量反馈样式
- “我的”页移除旧收藏入口，详情页收藏操作统一改为“追剧”
- 修复追剧相关提示文案与错误提示的旧“收藏”残留
