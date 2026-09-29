package top.jlen.vod.performance

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val APP_ID = "top.jlen.vod"

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun withoutProfile() = startup(CompilationMode.None())
    @Test fun withProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = benchmark.measureRepeated(
        packageName = APP_ID,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        awaitLibraryTab()
    }
}

@RunWith(AndroidJUnit4::class)
class LibraryScrollBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    @Test fun scroll() = benchmark.measureRepeated(
        packageName = APP_ID,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            awaitLibraryTab()
            device.findObject(By.text("片库")).click()
            device.waitForIdle()
        }
    ) { scrollLibrary() }
}

@RunWith(AndroidJUnit4::class)
class AppBaselineProfile {
    @get:Rule val profile = BaselineProfileRule()
    @Test fun homeAndLibrary() = profile.collect(packageName = APP_ID) {
        pressHome()
        startActivityAndWait()
        awaitLibraryTab()
        device.findObject(By.text("片库")).click()
        device.waitForIdle()
        scrollLibrary()
    }
}

private fun MacrobenchmarkScope.awaitLibraryTab() {
    assertTrue("请先完成用户协议/登录引导，并关闭代理或 VPN。", device.wait(Until.hasObject(By.text("片库")), 20_000))
}

private fun MacrobenchmarkScope.scrollLibrary() {
    assertTrue("片库必须加载出可滚动内容，不能用空列表测量。",
        device.wait(Until.hasObject(By.res("library_videos").scrollable(true)), 20_000))
    val x = device.displayWidth / 2
    val top = device.displayHeight * 45 / 100
    val bottom = device.displayHeight * 80 / 100
    repeat(3) {
        device.swipe(x, bottom, x, top, 20)
        device.waitForIdle()
    }
    device.swipe(x, top, x, bottom, 20)
    device.waitForIdle()
}
