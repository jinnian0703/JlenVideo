# JlenVideo

`JlenVideo` 是一个基于原生 Android + Jetpack Compose 开发的苹果 CMS 影视客户端，目标不是简单套壳网页，而是在保留苹果 CMS 站点能力的前提下，提供更接近原生 App 的浏览、搜索、详情、播放和账号体验。

当前默认适配站点：

- `https://cms.jlen.top/`

当前正式版：

- `versionName`: `2.1.0.0`
- `versionCode`: `23`

版本配置位置：

- [app/build.gradle.kts](app/build.gradle.kts)

仓库地址：

- Android 客户端: [https://github.com/jinnian0703/JlenVideo](https://github.com/jinnian0703/JlenVideo)
- 新版本后端: [https://github.com/jinnian0703/ys](https://github.com/jinnian0703/ys)

## 目录

- [项目特点](#项目特点)
- [适配说明](#适配说明)
- [接口说明](#接口说明)
- [模板文件](#模板文件)
- [模板预览](#模板预览)
- [主要功能](#主要功能)
- [技术栈](#技术栈)
- [核心目录](#核心目录)
- [如何修改站点 API](#如何修改站点-api)
- [苹果 CMS 路由规则](#苹果-cms-路由规则)
- [编译教程](#编译教程)
- [1. 克隆项目](#1-克隆项目)
- [2. 使用 Android Studio 打开](#2-使用-android-studio-打开)
- [3. 编译调试包](#3-编译调试包)
- [4. 编译正式包](#4-编译正式包)
- [检查更新说明](#检查更新说明)
- [发布建议](#发布建议)
- [2.1.0.0 版本说明](#2100-版本说明)
- [2.0.2.6 版本说明](#2026-版本说明)
- [2.0.2.5 版本说明](#2025-版本说明)
- [2.0.2.4 版本说明](#2024-版本说明)
- [2.0.2.3 版本说明](#2023-版本说明)
- [2.0.2.2 版本说明](#2022-版本说明)
- [2.0.2.1 版本说明](#2021-版本说明)
- [2.0.2.0 版本说明](#2020-版本说明)
- [License](#license)

## 项目特点

- 原生首页、片库、详情、搜索、播放、账号页，不直接套用网页界面
- 新版主链路基于 API 接口，首页、搜索、详情、用户中心等功能统一走接口数据
- 首页推荐位可直接读取苹果 CMS 推荐数据
- 原生播放器接管网页播放，支持全屏、倍速、暂停、下一集和自动连播
- 支持登录、注册、找回密码、邮箱绑定、资料修改、收藏、历史、会员信息
- 支持热搜榜、搜索记录和独立搜索结果页
- 支持崩溃日志记录与 GitHub Release 检查更新

## 适配说明

这个客户端是围绕苹果 CMS 站点能力做的原生封装，整体适配思路如下：

- 新版功能链路以 API 接口为核心，首页、分类、搜索、详情、用户中心、会员、收藏、历史均优先使用接口返回
- 搜索端由新后端提供接口支持，底层已切到 `Meilisearch` 检索引擎
- 前端主要关注接口字段兼容与播放链路衔接，不再依赖模板页面结构作为主数据源
- 播放页解析出真实播放地址后，交给原生播放器继续处理

目前已接入或适配的内容包括：

- 首页推荐
- 最近更新
- 片库分类与分页
- 搜索与热搜
- 影视详情
- 线路与选集
- 原生接管播放
- 用户中心相关功能

## 接口说明

当前版本以接口驱动为主，主要分为 4 组：

- 主站视频接口：`{APPLE_CMS_BASE_URL}/api.php/video/...`
- 主站用户接口：`{APPLE_CMS_BASE_URL}/api.php/user/...`
- 内容中心接口：`https://user.jlen.top/api.php?action=...`
- 更新检测接口：GitHub Releases `releases/latest`

当前线上后端与接口改造可参考：

- 后端仓库：[`jinnian0703/ys`](https://github.com/jinnian0703/ys)

### 1. 主站视频接口

接口基址：

- `https://cms.jlen.top/api.php/video`

当前客户端主要使用这些接口：

- `GET /api.php/video/categories`
  用途：获取片库分类列表。
- `GET /api.php/video/recommends?limit=16`
  用途：获取首页推荐内容，前端会按可播放内容过滤后展示。
- `GET /api.php/video/latest?page=1&limit=36`
  用途：获取首页“最近更新”与最近更新分页列表。
- `GET /api.php/video/list?type_id={typeId}&page=1&limit=36`
  用途：获取指定分类下的片库分页数据。
- `GET /api.php/video/search?wd={keyword}&page=1&limit=60`
  用途：搜索影片。
  说明：新版后端的搜索实现已切换为 `Meilisearch`。
- `GET /api.php/video/detail?vod_id={vodId}`
  用途：获取影片详情、线路、选集等播放前数据。
- `GET /api.php/video/memberInfo`
  用途：获取当前登录用户的会员信息、基础资料、头像、套餐等聚合数据。
- `GET /api.php/video/favorites?user_id={userId}&page=1&limit=20`
  用途：获取收藏列表。
- `GET /api.php/video/history?user_id={userId}&page=1&limit=20`
  用途：获取播放历史列表。
- `POST /api.php/video/favorite`
  用途：新增或删除收藏。
  常用参数：`user_id`、`vod_id`、`action=add|delete`。
- `POST /api.php/video/historyRecord`
  用途：新增或删除播放记录。
  常用参数：
  新增记录：`user_id`、`vod_id`、`sid`、`nid`、`action=add`
  删除记录：`user_id`、`ulog_id`、`action=delete`
- `POST /api.php/video/portrait`
  用途：上传头像。
  支持两种上传方式：
  1. `multipart/form-data` 的 `file`
  2. 表单字段 `imgdata=data:image/...;base64,...`

### 2. 主站用户接口

接口基址：

- `https://cms.jlen.top/api.php/user`

当前客户端主要使用这些接口：

- `GET /api.php/user/get_detail?id={userId}`
  用途：获取用户详情。
  返回内容通常包括：用户名、用户组、积分、到期时间、头像、邮箱、手机号、QQ 等。

### 3. 内容中心接口

接口基址：

- `https://user.jlen.top/api.php`

当前客户端主要使用这些接口：

- `GET /api.php?action=me`
  用途：获取聚合后的用户资料、会员信息、会员套餐。
- `GET /api.php?action=notices&app_version={version}&user_id={userId}`
  用途：获取公告列表、弹窗公告、置顶公告等。
- `POST /api.php?action=heartbeat`
  用途：上报在线心跳、页面路由、影片与剧集位置信息。
  常用参数：`device_id`、`platform`、`app_version`、`route`、`user_id`、`vod_id`、`sid`、`nid`。
- `POST /api.php?action=user_profile`
  用途：统一处理用户资料相关写操作。
  当前前端已使用它处理：
  保存资料：`user_pwd`、`user_pwd1`、`user_pwd2`、`user_qq`、`user_email`、`user_phone`、`user_question`、`user_answer`
  发送邮箱验证码：`op=send_bind_code`
  绑定邮箱：`op=bind_email`
  解绑邮箱：`op=unbind_email`
  升级会员：`op=upgrade_membership`，并提交 `group_id`、`long`

### 4. 更新检测接口

当前应用内更新读取：

- `https://api.github.com/repos/jinnian0703/JlenVideo/releases/latest`

前端使用字段：

- `tag_name`：最新版本号
- `html_url`：Release 页面地址
- `body`：更新日志
- `assets[].browser_download_url`：APK 下载地址

## 模板文件

仓库内已附带当前适配时参考使用的模板文件：

- [templates/v2.zip](templates/v2.zip)
- [templates/DYXS2](templates/DYXS2)

推荐使用方式：

- 如果你准备复刻旧版站点结构或对照历史模板，优先使用 [templates/v2.zip](templates/v2.zip)
- 如果你需要查看模板解压后的目录、页面和静态资源，可以直接参考 [templates/DYXS2](templates/DYXS2)
- 新版客户端主链路并不依赖模板 HTML 结构，但如果你保留了旧版兼容兜底逻辑，替换模板时仍需注意少量页面解析兼容

## 模板预览

以下截图来自附带模板 `DYXS2`：

![模板首页预览](templates/DYXS2/static/image/screenshot/index.jpg)
![模板播放页预览](templates/DYXS2/static/image/screenshot/play.jpg)

## 主要功能

- 首页推荐、最近更新、片库浏览
- 搜索页重构，支持搜索记录、热搜榜和独立结果页
- 热搜榜聚合腾讯视频、爱奇艺、优酷、芒果 TV 等平台公开数据
- 影视详情页、线路切换、选集切换
- 原生播放器接管播放
- 播放器支持全屏、倍速、暂停、继续播放、下一集、自动播放下一集
- 自动记录观看历史
- 收藏、历史、会员基础信息展示
- 登录、注册、找回密码、资料修改、邮箱绑定和解绑
- 关于页、版本信息、更新检查、崩溃日志

## 技术栈

- Kotlin
- Jetpack Compose
- Navigation Compose
- ViewModel
- OkHttp
- Retrofit
- Gson
- Jsoup
- Coil
- Media3 ExoPlayer

## 核心目录

主要代码位置如下：

- [app/src/main/java/top/jlen/vod/MainActivity.kt](app/src/main/java/top/jlen/vod/MainActivity.kt)
- [app/src/main/java/top/jlen/vod/JlenVideoApplication.kt](app/src/main/java/top/jlen/vod/JlenVideoApplication.kt)
- [app/src/main/java/top/jlen/vod/CrashLogger.kt](app/src/main/java/top/jlen/vod/CrashLogger.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt](app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/data/Models.kt](app/src/main/java/top/jlen/vod/data/Models.kt)
- [app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt](app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt)
- [app/src/main/java/top/jlen/vod/data/SearchHistoryStore.kt](app/src/main/java/top/jlen/vod/data/SearchHistoryStore.kt)
- [app/src/main/java/top/jlen/vod/ui/AppViewModel.kt](app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)
- [app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt](app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt)
- [app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt](app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt)
- [app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt](app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt)
- [app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt](app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt)
- [app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt](app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt)

## 如何修改站点 API

当前站点基础地址配置在：

- [app/build.gradle.kts](app/build.gradle.kts)

默认配置如下：

```kotlin
buildConfigField("String", "APPLE_CMS_BASE_URL", "\"https://cms.jlen.top/\"")
buildConfigField("String", "APPLE_CMS_FALLBACK_BASE_URL", "\"http://82.157.207.243:39888/\"")
```

如果你要替换主站视频接口地址，可以改成自己的域名，例如：

```kotlin
buildConfigField("String", "APPLE_CMS_BASE_URL", "\"https://your-domain.com/\"")
```

如果你连内容中心接口也要一起替换，还需要同步修改：

- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt) 中的 `APP_CENTER_API_URL`
- GitHub Release 检测地址

修改时建议注意这些点：

- 必须带协议头，例如 `https://`
- 末尾建议保留 `/`
- 如果站点开启了 Cloudflare、人机验证或额外反爬，部分功能可能需要补充适配
- 如果你替换后端，建议优先保持上面列出的接口路径和字段兼容

## 苹果 CMS 路由规则

如果你的站点同样基于这套模板，建议在苹果 CMS 后台开启以下选项：

- 隐藏后缀：开启
- 路由状态：开启
- 伪静态状态：开启

建议使用下面这组路由伪静态规则：

```text
map   => map/index
rss/index   => rss/index
rss/baidu => rss/baidu
rss/google => rss/google
rss/sogou => rss/sogou
rss/so => rss/so
rss/bing => rss/bing
rss/sm => rss/sm

index-<page?>   => index/index

gbook-<page?>   => gbook/index
gbook$   => gbook/index

topic-<page?>   => topic/index
topic$  => topic/index
topicdetail-<id>   => topic/detail

actor-<page?>   => actor/index
actor$ => actor/index
actordetail-<id>   => actor/detail
actorshow/<area?>-<blood?>-<by?>-<letter?>-<level?>-<order?>-<page?>-<sex?>-<starsign?>   => actor/show

role-<page?>   => role/index
role$ => role/index
roledetail-<id>   => role/detail
roleshow/<by?>-<letter?>-<level?>-<order?>-<page?>-<rid?>   => role/show

vodtype/<id>-<page?>   => vod/type
vodtype/<id>   => vod/type
voddetail/<id>   => vod/detail
vodrss-<id>   => vod/rss
vodplay/<id>-<sid>-<nid>   => vod/play
voddown/<id>-<sid>-<nid>   => vod/down
vodshow/<id>-<area?>-<by?>-<class?>-<lang?>-<letter?>-<level?>-<order?>-<page?>-<state?>-<tag?>-<year?>   => vod/show
vodsearch/<wd?>-<actor?>-<area?>-<by?>-<class?>-<director?>-<lang?>-<letter?>-<level?>-<order?>-<page?>-<state?>-<tag?>-<year?>   => vod/search
vodplot/<id>-<page?>   => vod/plot
vodplot/<id>   => vod/plot

arttype/<id>-<page?>   => art/type
arttype/<id>   => art/type
artshow-<id>   => art/show
artdetail-<id>-<page?>   => art/detail
artdetail-<id>   => art/detail
artrss-<id>-<page>   => art/rss
artshow/<id>-<by?>-<class?>-<level?>-<letter?>-<order?>-<page?>-<tag?>   => art/show
artsearch/<wd?>-<by?>-<class?>-<level?>-<letter?>-<order?>-<page?>-<tag?>   => art/search

label-<file> => label/index

plotdetail/<id>-<page?>   => plot/plot
plotdetail/<id>   => plot/detail
```

## 编译教程

### 1. 克隆项目

```bash
git clone https://github.com/jinnian0703/JlenVideo.git
cd JlenVideo
```

### 2. 使用 Android Studio 打开

- 打开 Android Studio
- 选择项目根目录
- 等待 Gradle 同步完成

### 3. 编译调试包

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

输出位置：

- `app/build/outputs/apk/debug/app-debug.apk`

### 4. 编译正式包

配置签名后执行：

```powershell
.\gradlew.bat assembleRelease
```

输出位置：

- `app/build/outputs/apk/release/app-release.apk`

## 检查更新说明

当前版本的更新检测基于 GitHub Release：

- 接口读取 `releases/latest`
- 版本号取自 `tag_name`
- 更新说明取自 `body`
- “查看发布”跳转到 Release 页面
- “前往下载”优先读取 APK 资产下载地址

如果你想切换成自己的仓库更新逻辑，建议按下面修改：

1. 修改 [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt) 中的 GitHub Release 接口地址
2. 修改同文件中的 Release 页面兜底地址
3. 修改 [app/build.gradle.kts](app/build.gradle.kts) 里的 `versionCode` 和 `versionName`
4. 创建新的 tag，例如 `v2.0.1.1`
5. 创建 GitHub Release 并上传 APK

注意事项：

- `versionName` 建议与 Release 标签对应，例如本地 `2.0.1.1` 对应 `v2.0.1.1`
- 如果没有上传 APK 资产，下载按钮会自动回退到 Release 页面
- 如果最新版本仍是 Draft，GitHub 的最新发布接口通常不会返回

## 发布建议

后续建议按以下流程发布版本：

1. 修改代码并完成自测
2. 执行调试包或正式包构建
3. 更新 README、更新日志或 Release 说明
4. 提交代码并推送
5. 打 tag
6. 创建 GitHub Release
7. 上传 APK

## 2.1.0.0 版本说明

当前仓库版本现已提升为 `2.1.0.0`。

`2.1.0.0` 主要用于切换新一版服务端与接口，并补充最近一轮账号相关修复。

- 更换服务器，整体访问稳定性进一步提升
- 搜索功能升级为 `Meilisearch` 检索引擎，搜索速度大幅提升，结果返回更快
- 更换并升级部分接口，提升数据加载效率与兼容性
- 优化用户中心相关接口适配，会员信息、个人资料等加载更稳定
- 修复头像上传相关问题，减少上传后显示异常的情况
- 优化部分页面数据刷新逻辑，改善资料更新后的显示体验

## 2.0.2.6 版本说明

当前仓库版本现已提升为 `2.0.2.6`。
`2.0.2.6` 主要用于接入管理后台在线统计，并继续优化公告系统体验。
- 接入 `user.jlen.top` 管理后台在线统计心跳，进入页面后立即上报并定时续期
- 首页顶部接入公告入口、公告列表、公告轮播和新公告弹窗
- 公告详情页改为按 HTML 结构分段渲染，样式更接近后台预览
- 公告时间显示、未读状态和公告列表交互继续优化

## 2.0.2.5 版本说明

当前仓库版本现已提升为 `2.0.2.5`。

`2.0.2.5` 主要用于整理最近一轮首页、搜索和播放记录体验优化。

- 播放记录改为更快出首屏，并补充线路名缓存与并发去重
- 首页推荐和最近更新卡片去掉重复文案，过滤无意义的 `NO` 数字角标
- 首页更新角标仅在屏幕内可见时滚动，并压缩为更清晰的集数样式
- 最近更新和片库列表的继续加载改为更早预加载，滚动更顺滑
- 热门搜索关键词过滤站点附带的干扰后缀，减少误搜

## 2.0.2.4 版本说明

当前仓库版本现已提升为 `2.0.2.4`。

`2.0.2.4` 主要用于整理最近一轮播放页和详情页体验调整。

- 详情页收藏提示改为更清晰的状态反馈
- 收藏成功、重复收藏、未登录等提示文案统一优化
- 播放页移除了单独的“查看详情”入口，界面更简洁

## 2.0.2.3 版本说明

当前仓库版本现已提升为 `2.0.2.3`。

`2.0.2.3` 主要用于发布最近一轮选集区域性能优化。

- 选集面板改为三列显示，减少文字截断和轮播触发概率
- 详情页超大选集列表改为分页展示，单页最多渲染 60 集
- 详情页滑动时暂停选集轮播，停止后再恢复
- 仅保留当前选中长标题的轮播，纯数字选集不再触发轮播

## 2.0.2.2 版本说明

当前仓库版本现已提升为 `2.0.2.2`。

`2.0.2.2` 主要用于恢复片库分类网页来源。

- 片库分类列表切回网页分类页解析
- 分类分页改为走 `vodshow/...` 网页路由
- 分类项优先从站点网页结构读取
- 最近更新继续保持使用 `https://cms.jlen.top/label/new/`

## 2.0.2.1 版本说明

当前仓库版本现已提升为 `2.0.2.1`。

`2.0.2.1` 用于通过应用内更新弹窗发布维护公告。

- 当前服务正在维护中
- 预计于 `2026-03-27` 完成维护
- 本次版本主要用于向现有用户展示维护通知，不涉及新的功能改动

## 2.0.2.0 版本说明

当前仓库版本现已提升为 `2.0.2.0`。

`2.0.2.0` 主要整理并合入了最近一轮详情页、片库和搜索体验优化。

- 详情页元信息文字改为按字段分隔，阅读更清晰
- 片库分类切换逻辑与缓存策略持续优化
- 片库分类数据源切换为结构化 API，减少网页解析开销
- 搜索空状态提示文案更简洁，移除了多余返回按钮
- 搜索结果稳定性继续优化，减少上一次关键词结果串入的问题

## License

本项目采用 MIT License，详见：

- [LICENSE](LICENSE)
