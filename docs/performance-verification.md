# 性能优化与验证

本轮保留心跳、登录、续播保存频率、分类交互和原有配色。

1. 环境检测放到 IO 线程，首次检查不跳过，前台仍每 2 秒检查，后台暂停；心跳逻辑未改。普通日志串行后台写入，日志详情按需后台读取，崩溃时等待关键记录落盘。
2. 首页与异常兜底不再预先加载首个具体分类；默认“全部”直接使用最新内容，选中具体分类时再加载。
3. 续播记录照常保存。追剧只增量更新当前影片，精确到分钟的卡片时间不再随每个播放快照刷新；异步缓存合并写入，网络刷新不覆盖更新的续播卡片。
4. 分类滚动距离不是 Compose 状态，只有展开值变化才刷新；父页面滚动位置保存不产生逐像素重组。
5. 首页下拉只强制刷新首页、公告与分类入口，不清除其他页面缓存或搜索位置。设置中的完整清缓存入口保留。其他缓存继续使用原 TTL。
6. 原有 23 条应用 Baseline Profile 规则按编译产物校正，新增签名检查脚本与可选设备测量模块。

启动兼容性：项目使用 Compose 1.6。生命周期所有者必须读取 `androidx.compose.ui.platform.LocalLifecycleOwner`（与现有播放器一致），不引入 Lifecycle 2.8 的反射桥接；其 2.8.2 AAR 自带 keep 规则把返回值误写为数组，R8 后可能取不到实际对象。需在 release mapping / runtime classpath 检查无该桥接路径，并在设备上验证冷启动。

## 静态和功能验证

```powershell
.\gradlew.bat testReleaseUnitTest :app:assembleRelease --console=plain
.\scripts\verify-baseline-profile.ps1
.\scripts\verify-release-startup.ps1
```

需要 JDK 17 和 Android SDK。签名脚本直接检查 release class 中的方法描述符，拒绝失效规则。单测验证滚动状态写入次数、追剧增量/合并行为，不代表真机帧率测试。

## 真机测量（未连接设备时不可声称已完成）

使用专用测试设备，推荐 Android 14+。安装测试变体会覆盖同包名应用；使用现有签名，不卸载、不清除用户数据。手动完成协议/登录引导，关闭代理/VPN，保证网络稳定、首页与片库已有足够内容，退出播放。大屏和手机分别记录结果，不把空列表的结果作为滚动收益。

```powershell
# 编译测量 APK；默认生产构建完全不引入 performance 模块。
.\gradlew.bat -PenablePerformanceTests=true :performance:assembleBenchmark :performance:assembleProfile
# 优化后的 release 同配置变体，对比有/无 Profile 的冷启动。
.\gradlew.bat -PenablePerformanceTests=true :performance:connectedBenchmarkAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=top.jlen.vod.performance.StartupBenchmark,top.jlen.vod.performance.LibraryScrollBenchmark'
# 未混淆、非调试变体采集实际首页/片库路径；低 Android 版本可能需要 root。
.\gradlew.bat -PenablePerformanceTests=true :performance:connectedProfileAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=top.jlen.vod.performance.AppBaselineProfile'
```

首次运行前可先运行 `:app:installBenchmark` 或 `:app:installProfile` 并手动准备测试内容。采集产物位于 `performance/build/outputs/connected_android_test_additional_output/`。核对采集文件无混淆名后再合并进源 Profile；不要把 benchmark 混淆 APK 的规则直接写回源码。

看冷启动 TTID 和列表 `frameDurationCpuMs` / `frameOverrunMs` 分位数，保留原始 JSON/trace。当前测试不把服务端响应时间当成纯客户端性能，也不测耗电；有/无 Profile 对比不等同于本轮全部优化的前后对比。实际首页内容完成时间、播放发热/耗电、长列表和折叠屏还需同设备、同网络、同数据的前后对照。

依据：[Compose 性能最佳实践](https://developer.android.com/develop/ui/compose/performance/bestpractices)、[Baseline Profile 采集和验证](https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure)。
