# JlenVideo

`JlenVideo` 是一个基于原生 Android + Jetpack Compose 开发的苹果 CMS 影视客户端，当前版本为 `1.1.4` 正式版。

项目目标不是简单网页套壳，而是围绕苹果 CMS 站点能力，提供更接近原生 App 的浏览、搜索、详情、播放、账号、收藏、历史与会员体验。

当前默认站点：

- `https://cms.jlen.top/`

## 版本信息

- 当前版本：`1.1.4`
- `versionCode`：`6`
- `versionName`：`1.1.4`

版本定义位置：

- [app/build.gradle.kts](app/build.gradle.kts)

## 1.1.4 更新内容

本次 `1.1.4` 仅记录当前版本实际变更：

- 修复网络异常时首页直接显示英文原始报错的问题
- 网络或站点异常时，改为显示中文提示并带出站点域名
- 修复部分机型打开 GitHub 更新链接时被误判失败的问题
- 统一“立即更新 / 查看发布 / 前往下载”的外链打开逻辑

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
- GitHub Release 检查更新

## 首页推荐说明

- 首页顶部“推荐”区域读取苹果 CMS 推荐位 `level=1`
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

- [app/src/main/java/top/jlen/vod/MainActivity.kt](app/src/main/java/top/jlen/vod/MainActivity.kt)
- [app/src/main/java/top/jlen/vod/CrashLogger.kt](app/src/main/java/top/jlen/vod/CrashLogger.kt)
- [app/src/main/java/top/jlen/vod/JlenVideoApplication.kt](app/src/main/java/top/jlen/vod/JlenVideoApplication.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt](app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt)
- [app/src/main/java/top/jlen/vod/data/Models.kt](app/src/main/java/top/jlen/vod/data/Models.kt)
- [app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt](app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt)
- [app/src/main/java/top/jlen/vod/ui/AppViewModel.kt](app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)
- [app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt](app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt)
- [app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt](app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt)
- [app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt](app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt)
- [app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt](app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt)
- [app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt](app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt)

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

- [app/build.gradle.kts](app/build.gradle.kts)

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

## 模板与路由配置

当前客户端对接时，参考使用的模板文件为：

- [templates/v2.zip](templates/v2.zip)

推荐使用方式：

- 推荐直接使用仓库内附带的 [templates/v2.zip](templates/v2.zip) 作为模板参考或部署底包
- 如果准备复刻 `cms.jlen.top` 当前结构，优先使用这份模板，客户端适配会更稳定
- 如果更换了别的模板，也可以继续适配，但搜索、详情、播放、专题、演员、文章、用户中心等页面的解析逻辑可能需要重新调整

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

生成路径：

- `app/build/outputs/apk/debug/app-debug.apk`

## 检查更新说明

当前版本的“检查更新”功能基于 GitHub Release：

- 版本号读取自 Release 的 `tag_name`，默认支持 `v1.1.4` 这种格式，进入 App 后会自动去掉前面的 `v`
- 发布说明读取自 Release 的 `body`
- “查看发布”打开 Release 页面 `html_url`
- “前往下载”优先打开 Release 资产里的 APK 下载地址 `browser_download_url`

如果你要改成自己的仓库更新：

1. 修改 [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt) 里的 GitHub Release 接口地址
2. 修改同文件里的 Release 页面兜底地址
3. 修改 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
4. 打 tag，例如 `v1.1.4`
5. 创建 GitHub Release 并上传 APK

注意事项：

- `versionName` 要和 GitHub Release 的 `tag_name` 对应，例如本地是 `1.1.4`，Release 标签建议用 `v1.1.4`
- 如果没有上传 APK 资产，“前往下载”会自动退回到 Release 页面
- 如果 Release 还是 Draft，GitHub 最新发布接口可能不会返回它

## 开发与发布说明

本仓库当前发布为 `1.1.4` 正式版，建议后续版本遵循语义化版本：

- `1.1.4`：问题修复
- `1.2.0`：新增功能
- `2.0.0`：重大不兼容变更

推荐发布流程：

1. 修改代码并自测
2. 执行 `assembleDebug` 或 `assembleRelease`
3. 更新 README 或变更说明
4. 提交代码
5. 打 tag，例如 `v1.1.4`
6. 推送到 GitHub
7. 创建 GitHub Release

## 开源协议

本项目采用 MIT License，详见：

- [LICENSE](LICENSE)

## 仓库地址

- GitHub: [https://github.com/jinnian0703/JlenVideo](https://github.com/jinnian0703/JlenVideo)
