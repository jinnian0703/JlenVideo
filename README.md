# JlenVideo

`JlenVideo` 是一个基于原生 Android + Jetpack Compose 开发的苹果 CMS 影视客户端，当前版本为 `1.0.0` 正式版。

项目目标不是简单套壳网页，而是围绕苹果 CMS 站点能力，提供更接近原生 App 的浏览、搜索、详情、播放、账号、收藏、历史与会员体验。

当前默认站点：

- `https://cms.jlen.top/`

## 版本信息

- 当前版本：`1.0.0`
- `versionCode`：`1`
- `versionName`：`1.0.0`

版本定义位置：

- [app/build.gradle.kts](/F:/codex/1/app/build.gradle.kts)

## 主要功能

- 首页推荐、最近更新、片库分类浏览
- 站内搜索
- 影视详情页
- 原生播放器接管播放
- 全屏、倍速、暂停、继续播放
- 自动记录历史
- 收藏、观看记录
- 登录、注册、资料修改
- 邮箱绑定、邮箱解绑
- 会员中心基础信息展示

首页推荐说明：

- 首页顶部“推荐”区域读取苹果 CMS 的推荐位 `level=1`
- 当前版本已经移除 `level=9` 轮播推荐与 `level=8` 热播推荐
- 如果你希望首页推荐正常显示，请先在站点后台给对应影视内容设置 `level=1`

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

核心目录如下：

- [app/src/main/java/top/jlen/vod/MainActivity.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/MainActivity.kt)
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

- `data` 层负责接口访问、页面抓取、HTML 解析、Cookie 持久化。
- `ui` 层负责 Compose 页面、播放器、导航与状态展示。
- `AppViewModel` 负责把站点数据整合成 App 可直接消费的状态。

## 工作原理

这个项目同时使用了两类数据来源：

1. 苹果 CMS JSON 接口
- 用于分类、部分影视列表等结构化数据拉取。

2. 站点 HTML 页面解析
- 用于搜索、详情、用户中心、收藏、历史、会员、注册、邮箱绑定等功能。

因为不同苹果 CMS 主题对前端结构有差异，所以这里采用了：

- 能走接口的地方尽量走接口
- 接口不够时直接解析页面
- 播放页使用原生播放器接管实际播放地址

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
- 末尾最好保留 `/`
- 如果你的站点开启了 Cloudflare、人机验证或额外反爬，部分功能可能需要额外适配
- 不同主题模板的用户中心 HTML 结构不同，注册、登录、收藏、历史、会员区可能需要微调解析规则

如果你换了站点后功能异常，优先检查这些位置：

- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsApi.kt)

## 模板与路由配置

当前这版客户端对接时，参考使用的站点模板文件为：

- [templates/v2.zip](/F:/codex/1/templates/v2.zip)

推荐使用方式：

- 推荐直接使用仓库内附带的 [templates/v2.zip](/F:/codex/1/templates/v2.zip) 作为站点模板参考或部署底包。
- 如果你准备复刻当前 `cms.jlen.top` 这一套前台结构，优先使用这份模板，客户端适配会更稳定。
- 如果你更换了别的模板，客户端仍可继续适配，但搜索、详情、播放、专题、演员、文章、会员中心等 HTML 结构可能需要重新调整解析逻辑。

首页推荐位适配说明：

- 当前客户端首页推荐区默认抓取苹果 CMS 的 `level=1`
- 也就是说，站点后台需要把你想展示到首页推荐区的内容标记为 `level=1`
- 如果后台没有设置 `level=1`，首页推荐区可能为空，但“最近更新”和“片库分类”仍会正常加载

如果你的站点也是基于这一套模板或相近结构，建议后台同时开启路由伪静态相关设置，否则搜索、详情、播放、专题、演员、文章等路径可能与客户端解析逻辑不一致。

建议后台设置：

- 隐藏后缀：开启
- 路由状态：开启
- 伪静态状态：开启

推荐将下面这组规则配置到苹果 CMS 的“路由伪静态设置”里：

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

- 这组规则会直接影响分类页、详情页、播放页、搜索页、专题页和文章页的访问路径。
- 如果你使用的是别的模板，路由可以不同，但要同步检查客户端解析逻辑。
- 播放页、搜索页、用户中心页如果打不开，优先先检查这里的配置是否和站点真实访问路径一致。

## 编译环境

建议环境：

- Android Studio Hedgehog 以上
- JDK `17`
- Android SDK `34`
- Gradle Wrapper

本项目当前配置：

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

### 2. 使用 Android Studio 打开

- 打开 Android Studio
- 选择项目根目录
- 等待 Gradle 同步完成

### 3. 修改站点 API

按上面的“如何修改 API 地址”一节，修改：

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

生成的 APK 默认在：

- `app/build/outputs/apk/debug/app-debug.apk`

### 5. 编译发布包

```powershell
.\gradlew.bat assembleRelease
```

生成的 APK 默认在：

- `app/build/outputs/apk/release/`

注意：

- 当前 `release` 默认没有开启混淆优化
- 正式分发前建议自行配置签名、混淆、资源压缩和安全加固

## 调试建议

如果你接入新的苹果 CMS 站点后遇到问题，可以按下面排查：

### 搜索无结果

- 检查搜索页路径是否仍然是 `/vodsearch/-------------/?wd=关键词`
- 检查搜索结果 DOM 结构是否变化

### 播放地址解析失败

- 检查播放页的 `player_aaaa` 数据结构
- 检查播放器 iframe / m3u8 / mp4 链接提取规则
- 检查是否需要额外 Referer 或 Origin

### 收藏、历史、会员、资料失败

- 检查用户中心路径是否仍为 `/index.php/user/...`
- 检查页面字段标题和 HTML 结构是否变化
- 检查站点是否启用了验证码、风控或登录保护

重点排查文件：

- [app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/data/AppleCmsRepository.kt)
- [app/src/main/java/top/jlen/vod/ui/AppViewModel.kt](/F:/codex/1/app/src/main/java/top/jlen/vod/ui/AppViewModel.kt)

## 注册系统说明

当前版本已经支持原生注册入口。

注册页会动态读取站点真实注册配置，并根据站点要求加载：

- 用户名
- 密码
- 确认密码
- 邮箱或手机号
- 短信/邮件验证码
- 图片验证码

当前默认站点 `cms.jlen.top` 的注册规则为：

- 邮箱注册
- 邮箱验证码
- 图片验证码

## 开发与发布说明

本仓库当前发布为 `1.0.0` 正式版，建议后续版本遵循语义化版本：

- `1.0.1`：问题修复
- `1.1.0`：新增功能
- `2.0.0`：重大不兼容变更

推荐发布流程：

1. 修改代码并自测
2. 执行 `assembleDebug` 或 `assembleRelease`
3. 更新 README 或变更说明
4. 提交代码
5. 打 tag，例如 `v1.0.1`
6. 推送到 GitHub
7. 创建 GitHub Release

## 已知说明

- 项目依赖目标苹果 CMS 站点的前端结构，主题差异可能导致部分解析逻辑需要调整
- 一些第三方播放线路可能存在跨域、Referer、防盗链等限制
- 若站点启用复杂验证码、人机验证或登录风控，需额外适配

## 开源协议

本项目采用 MIT License，详见：

- [LICENSE](/F:/codex/1/LICENSE)

## 仓库地址

- GitHub: [https://github.com/jinnian0703/JlenVideo](https://github.com/jinnian0703/JlenVideo)
