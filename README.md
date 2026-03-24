# JlenVideo

`JlenVideo` 是一个基于原生 Android + Jetpack Compose 开发的苹果 CMS 影视客户端，当前版本为 `1.1.2` 正式版。

项目目标不是简单网页套壳，而是围绕苹果 CMS 站点能力，提供更接近原生 App 的浏览、搜索、详情、播放、账号、收藏、历史与会员体验。

当前默认站点：

- `https://cms.jlen.top/`

## 版本信息

- 当前版本：`1.1.2`
- `versionCode`：`4`
- `versionName`：`1.1.2`

版本定义位置：

- [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)

## 1.1.2 更新内容

- 首页推荐改为横幅轮播样式，并支持自动轮播
- 首页推荐轮播支持无限循环，手动拖动时会暂停自动轮播
- App 启动时会自动检查更新，发现新版本后弹窗提示更新
- 首页移除了底部分类分区展示，界面更简洁

## 主要功能

- 首页推荐、最近更新、片库分类浏览
- 站内搜索
- 影视详情页
- 原生播放器接管播放
- 全屏、倍速、暂停、下一集、自动续播
- 自动记录观看历史
- 收藏、历史、会员基础信息
- 登录、注册、找回密码、资料修改
- 邮箱绑定、解绑、验证码处理
- 关于页与崩溃日志查看

## 首页推荐说明

- 首页顶部“推荐”区域读取苹果 CMS 的推荐位 `level=1`
- 当前版本不再使用 `level=9` 轮播推荐和 `level=8` 热播推荐
- 如果软件首页推荐不显示，请先到苹果 CMS 后台为对应视频设置 `level=1`

## 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel
- Navigation Compose
- OkHttp
- Retrofit
- Gson
- Jsoup
- Coil
- Media3 ExoPlayer

## 项目结构

核心文件如下：

- [app/src/main/java/top/jlen/vod/MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/MainActivity.kt)
- [app/src/main/java/top/jlen/vod/CrashLogger.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/CrashLogger.kt)
- [app/src/main/java/top/jlen/vod/JlenVideoApplication.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/JlenVideoApplication.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt)
- [app/src/main/java/top/jlen/vod/data/Models.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/Models.kt)
- [app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt)
- [app/src/main/java/top/jlen/vod/ui/AppViewModel.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)
- [app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt)
- [app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt)
- [app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt)
- [app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt)
- [app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt)

说明：

- `data` 层负责接口访问、页面抓取、HTML 解析、Cookie 持久化
- `ui` 层负责 Compose 页面、播放器、导航与状态展示
- `AppViewModel` 负责把站点数据整合成 App 可直接消费的状态

## 工作原理

这个项目同时使用两类数据来源：

1. 苹果 CMS JSON 接口

- 用于分类、部分影视列表等结构化数据拉取

2. 站点 HTML 页面解析

- 用于搜索、详情、用户中心、收藏、历史、会员、注册、邮箱绑定等功能

整体策略：

- 能走接口的地方尽量走接口
- 接口不够时直接解析页面
- 播放页拿到真实地址后，交给原生播放器接管播放

## 如何修改 API 地址

当前站点地址配置在：

- [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)

找到这一行：

```kotlin
buildConfigField("String", "APPLE_CMS_BASE_URL", "\"https://cms.jlen.top/\"")
```

改成你自己的苹果 CMS 站点，例如：

```kotlin
buildConfigField("String", "APPLE_CMS_BASE_URL", "\"https://your-domain.com/\"")
```

注意事项：

- 必须带协议头，例如 `https://`
- 末尾建议保留 `/`
- 如果站点开启了 Cloudflare、人机验证或额外反爬，部分功能可能需要额外适配
- 如果站点模板结构变化较大，搜索、详情、收藏、历史、账号等 HTML 解析规则也要同步调整

重点排查文件：

- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt)

## 模板与路由配置

当前客户端对接时，参考使用的模板文件为：

- [templates/v2.zip](/F:/codex/1/templates/v2.zip)

推荐使用方式：

- 推荐直接使用仓库内附带的 [templates/v2.zip](/F:/codex/1/templates/v2.zip) 作为模板参考或部署底包
- 如果准备复刻 `cms.jlen.top` 当前结构，优先使用这份模板，客户端适配会更稳定
- 如果更换了别的模板，也可以继续适配，但搜索、详情、播放、专题、演员、文章、用户中心等页面的解析逻辑可能需要重新调整

首页推荐位适配说明：

- 当前客户端首页推荐区默认抓取苹果 CMS 的 `level=1`
- 也就是说，后台需要把想展示到首页推荐区的内容设置为 `level=1`
- 如果后台没有设置 `level=1`，首页推荐区可能为空，但“最近更新”和“片库分类”仍会正常加载

如果你的站点同样基于这套模板或相近结构，建议同时开启苹果 CMS 后台的路由伪静态配置：

- 隐藏后缀：开启
- 路由状态：开启
- 伪静态状态：开启

建议写入后台“路由伪静态设置”的规则如下：

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

说明：

- 这组规则会直接影响分类页、详情页、播放页、搜索页、专题页和文章页的访问路径
- 如果你使用的是别的模板，路由可以不同，但要同步检查客户端解析逻辑
- 如果播放页、搜索页、用户中心打不开，优先确认这些路由配置是否与站点真实访问路径一致

## 编译环境

建议环境：

- Android Studio Hedgehog 或更高版本
- JDK `17`
- Android SDK `34`
- Gradle Wrapper

当前项目配置：

- `minSdk = 24`
- `targetSdk = 34`
- `compileSdk = 34`
- `jvmTarget = 17`

## 编译教程

### 1. 克隆项目

```bash
git clone https://github.com/jinnian0703/JlenVideo.git
cd JlenVideo
```

### 2. 用 Android Studio 打开

- 打开 Android Studio
- 选择项目根目录
- 等待 Gradle 同步完成

### 3. 修改站点 API

根据上面的“如何修改 API 地址”一节，修改：

- [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)

### 4. 编译调试包

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

生成的 APK 默认路径：

- `app/build/outputs/apk/debug/app-debug.apk`

### 5. 编译发布包

```powershell
.\gradlew.bat assembleRelease
```

生成的 APK 默认路径：

- `app/build/outputs/apk/release/`

注意：

- 当前 `release` 默认没有开启混淆
- 正式分发前建议自行配置签名、混淆、资源压缩与安全加固

## 调试建议

如果你接入新的苹果 CMS 站点后遇到问题，可以按下面排查：

### 搜索无结果

- 检查搜索页路径是否仍是 `/vodsearch/-------------/?wd=关键词`
- 检查搜索结果 DOM 结构是否变化

### 播放地址解析失败

- 检查播放页的 `player_aaaa` 数据结构
- 检查播放器 iframe、m3u8、mp4 链接提取规则
- 检查是否需要额外的 `Referer` 或 `Origin`

### 收藏、历史、会员、资料失败

- 检查用户中心路径是否仍是 `/index.php/user/...`
- 检查页面字段标题和 HTML 结构是否变化
- 检查站点是否启用了验证码、风控或登录保护

重点排查文件：

- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/ui/AppViewModel.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)

## 注册与账号系统说明

当前版本已经支持原生登录、注册、找回密码、邮箱绑定与解绑流程。

注册页会动态读取站点真实注册配置，并根据站点要求加载：

- 用户名
- 密码
- 确认密码
- 邮箱
- 邮箱验证码
- 图片验证码

默认站点 `cms.jlen.top` 当前的常见流程为：

- 邮箱注册
- 邮箱验证码
- 图片验证码

## 崩溃日志

App 现在内置了异常日志记录能力。发生闪退后，可以在“我的 > 关于”中查看：

- 崩溃时间
- 线程
- 版本号
- 包名
- 堆栈信息

这样在排查线上闪退时，不需要额外连接电脑抓日志。

## 检查更新说明

当前版本的“检查更新”功能基于 GitHub Release：

- App 会请求 GitHub 最新发布接口：`https://api.github.com/repos/jinnian0703/JlenVideo/releases/latest`
- 版本号读取自 Release 的 `tag_name`，默认支持 `v1.1.2` 这种格式，进入 App 后会自动去掉前面的 `v`
- 发布说明读取自 Release 的 `body`
- “查看发布”打开 Release 页面 `html_url`
- “前往下载”优先打开 Release 资产里的 APK 下载地址 `browser_download_url`
- App 当前版本号来自 [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts) 里的 `versionName`

相关代码位置：

- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt)
- [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)

### 如何修改检查更新地址

如果后面你要把更新检测改成你自己的 GitHub 仓库，优先改下面两处：

1. 修改更新接口地址

在 [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt) 里找到：

```kotlin
.url("https://api.github.com/repos/jinnian0703/JlenVideo/releases/latest")
```

改成你自己的仓库，例如：

```kotlin
.url("https://api.github.com/repos/你的用户名/你的仓库名/releases/latest")
```

2. 修改 Release 页面兜底地址

在 [app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt) 里找到：

```kotlin
"https://github.com/jinnian0703/JlenVideo/releases"
```

改成你自己的 Release 页面，例如：

```kotlin
"https://github.com/你的用户名/你的仓库名/releases"
```

### 如何正确发布让 App 检测到更新

每次发新版本时，建议按这个顺序操作：

1. 修改 [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts) 里的 `versionCode` 和 `versionName`
2. 提交代码并推送到 GitHub
3. 打版本标签，例如 `v1.1.2`
4. 创建 GitHub Release，并确保这个标签对应的 Release 已发布，不是草稿
5. 上传 APK 文件到 Release 资产
6. 在 Release 说明里填写更新内容，App 会直接读取这里的文本

注意事项：

- `versionName` 要和 GitHub Release 的 `tag_name` 对应，例如本地是 `1.1.2`，Release 标签建议用 `v1.1.2`
- 如果没有上传 APK 资产，“前往下载”会自动退回到 Release 页面
- 如果 Release 还是 Draft，GitHub 最新发布接口可能不会返回它
- 如果你改成私有仓库，未登录的普通请求可能无法正常读取最新 Release，需要改成带鉴权的更新接口

## 开发与发布说明

本仓库当前发布为 `1.1.2` 正式版，建议后续版本遵循语义化版本：

- `1.1.2`：问题修复
- `1.2.0`：新增功能
- `2.0.0`：重大不兼容变更

推荐发布流程：

1. 修改代码并自测
2. 执行 `assembleDebug` 或 `assembleRelease`
3. 更新 README 或变更说明
4. 提交代码
5. 打 tag，例如 `v1.1.2`
6. 推送到 GitHub
7. 创建 GitHub Release

## 已知说明

- 项目依赖目标苹果 CMS 站点的前端结构，模板差异可能导致部分解析逻辑需要调整
- 一些第三方播放线路可能存在跨域、Referer、防盗链等限制
- 如果站点启用了复杂验证码、人机验证或额外风控，还需要继续做定制适配

## 开源协议

本项目采用 MIT License，详见：

- [LICENSE](/F:/codex/1/LICENSE)

## 仓库地址

- GitHub: [https://github.com/jinnian0703/JlenVideo](https://github.com/jinnian0703/JlenVideo)
