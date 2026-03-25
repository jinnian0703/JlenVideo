# JlenVideo

`JlenVideo` 是一个基于原生 Android + Jetpack Compose 开发的苹果 CMS 影视客户端，当前版本为 `1.1.9` 正式版。

项目目标不是简单套网页，而是围绕苹果 CMS 站点能力，提供更接近原生 App 的浏览、搜索、详情、播放、账号、收藏、历史与会员体验。

当前默认站点：

- `https://cms.jlen.top/`

## 版本信息

- 当前版本：`1.1.9`
- `versionCode`：`11`
- `versionName`：`1.1.9`

版本定义位置：

- [app/build.gradle.kts](F:/codex/1/app/build.gradle.kts)

## 1.1.9 更新内容

本次 `1.1.9` 主要更新：

- 重建搜索页结构，搜索入口、搜索记录、热搜榜和搜索结果页分离
- 新增多平台热搜榜抓取
- 已接入腾讯视频、爱奇艺、优酷、芒果 TV 热搜内容
- 修复爱奇艺第 `10` 条显示成 `01` 的问题
- 修复优酷热搜数据解析失败导致不显示的问题
- 同步版本号到 `1.1.9`

## 主要功能

- 首页推荐、最近更新、片库分类浏览
- 搜索页、搜索记录、热搜榜、独立搜索结果页
- 影视详情页
- 原生播放器接管播放
- 全屏、倍速、暂停、下一集、自动连播
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

## 搜索热搜说明

当前搜索页热搜榜会优先读取以下平台公开页面数据：

- 腾讯视频
- 爱奇艺
- 优酷
- 芒果 TV

说明：

- 热搜榜为抓取公开页面的实时内容，平台结构调整后可能需要同步更新解析规则
- 当前优酷热搜来源使用优酷 H5 首页公开数据
- 当前芒果 TV 热搜来源使用首页“当季热播”区域

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

- [MainActivity.kt](F:/codex/1/app/src/main/java/top/jlen/vod/MainActivity.kt)
- [CrashLogger.kt](F:/codex/1/app/src/main/java/top/jlen/vod/CrashLogger.kt)
- [JlenVideoApplication.kt](F:/codex/1/app/src/main/java/top/jlen/vod/JlenVideoApplication.kt)
- [AppleCmsRepository.kt](F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [AppleCmsApi.kt](F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt)
- [Models.kt](F:/codex/1/app/src/main/java/top/jlen/vod/data/Models.kt)
- [PersistentCookieJar.kt](F:/codex/1/app/src/main/java/top/jlen/vod/data/PersistentCookieJar.kt)
- [AppViewModel.kt](F:/codex/1/app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)
- [JlenVideoApp.kt](F:/codex/1/app/src/main/java/top/jlen/vod/ui/JlenVideoApp.kt)
- [BrowseScreens.kt](F:/codex/1/app/src/main/java/top/jlen/vod/ui/BrowseScreens.kt)
- [DetailPlayerScreens.kt](F:/codex/1/app/src/main/java/top/jlen/vod/ui/DetailPlayerScreens.kt)
- [NativeVideoPlayer.kt](F:/codex/1/app/src/main/java/top/jlen/vod/ui/NativeVideoPlayer.kt)
- [FullscreenPlayerActivity.kt](F:/codex/1/app/src/main/java/top/jlen/vod/ui/FullscreenPlayerActivity.kt)

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

- [app/build.gradle.kts](F:/codex/1/app/build.gradle.kts)

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
- 如果站点模板结构变化较大，搜索、详情、收藏、历史、账号等 HTML 解析规则也需要同步调整

## 模板与推荐使用

当前客户端对接时参考使用的模板文件为：

- [templates/v2.zip](F:/codex/1/templates/v2.zip)
- [templates/DYXS2](F:/codex/1/templates/DYXS2)

推荐使用方式：

- 推荐直接使用仓库内附带的 [templates/v2.zip](F:/codex/1/templates/v2.zip) 作为模板参考或部署底包
- 如果需要查看已解压的模板目录结构，可直接参考 [templates/DYXS2](F:/codex/1/templates/DYXS2)
- 如果准备复刻 `cms.jlen.top` 当前结构，优先使用这份模板，客户端适配会更稳定
- 如果更换了别的模板，也可以继续适配，但搜索、详情、播放、专题、演员、文章、用户中心等页面的解析逻辑可能需要重新调整

## 苹果 CMS 路由规则

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

### 4. 编译正式包

如果后续需要正式包，可在配置签名后执行：

```powershell
.\gradlew.bat assembleRelease
```

生成路径：

- `app/build/outputs/apk/release/app-release.apk`

## 检查更新说明

当前版本的“检查更新”功能基于 GitHub Release：

- 版本号读取自 Release 的 `tag_name`，默认支持 `v1.1.9` 这种格式，进入 App 后会自动去掉前面的 `v`
- 发布说明读取自 Release 的 `body`
- “查看发布”打开 Release 页面 `html_url`
- “前往下载”优先打开 Release 资产里的 APK 下载地址 `browser_download_url`

如果你要改成自己的仓库更新：

1. 修改 [AppleCmsRepository.kt](F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt) 里的 GitHub Release 接口地址
2. 修改同文件里的 Release 页面兜底地址
3. 修改 [app/build.gradle.kts](F:/codex/1/app/build.gradle.kts) 中的 `versionCode` 和 `versionName`
4. 打 tag，例如 `v1.1.9`
5. 创建 GitHub Release 并上传 APK

注意事项：

- `versionName` 要和 GitHub Release 的 `tag_name` 对应，例如本地是 `1.1.9`，Release 标签建议用 `v1.1.9`
- 如果没有上传 APK 资产，“前往下载”会自动退回到 Release 页面
- 如果 Release 还是 Draft，GitHub 最新发布接口可能不会返回它

## 开发与发布说明

本仓库当前发布为 `1.1.9` 正式版，建议后续版本遵循语义化版本：

- `1.1.9`：问题修复
- `1.2.0`：新增功能
- `2.0.0`：重大不兼容变更

推荐发布流程：

1. 修改代码并自测
2. 执行 `assembleDebug` 或 `assembleRelease`
3. 更新 README 或变更说明
4. 提交代码
5. 打 tag，例如 `v1.1.9`
6. 推送到 GitHub
7. 创建 GitHub Release

## 开源协议

本项目采用 MIT License，详见：

- [LICENSE](F:/codex/1/LICENSE)

## 仓库地址

- GitHub: [https://github.com/jinnian0703/JlenVideo](https://github.com/jinnian0703/JlenVideo)
