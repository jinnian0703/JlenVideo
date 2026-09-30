package top.jlen.vod.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DlnaSessionControllerTest {
    private val device = DlnaDevice("http://192.168.1.2/device.xml", "电视", "http://192.168.1.2/control")

    @Test
    fun pendingConnectionBlocksLocalPlaybackAndDuplicatePushIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val remote = FakeTransport().apply { load = { gate.await(); DlnaCastResult.Success } }
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集")
        assertTrue(controller.state.value.isActive)
        runCurrent()
        controller.pushIfConnected("episode1", "第一集")
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("episode1"), remote.urls)
        assertEquals("episode1", controller.state.value.url)
        assertNull(controller.state.value.connectingDevice)
    }

    @Test
    fun fastEpisodeChangesCancelOldLoadWithoutClearingNewState() = runTest {
        val remote = FakeTransport()
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集")
        runCurrent()
        remote.load = { url -> if (url == "episode2") awaitCancellation() else DlnaCastResult.Success }
        controller.pushIfConnected("episode2", "第二集")
        runCurrent()
        controller.pushIfConnected("episode3", "第三集")
        runCurrent()
        assertTrue(controller.state.value.isActive)
        assertEquals("episode3", controller.state.value.url)
        assertNull(controller.state.value.connectingDevice)
    }

    @Test
    fun disconnectWaitsForStopAndRetainsPositionWithMediaIdentity() = runTest {
        val stopGate = CompletableDeferred<Unit>()
        val remote = FakeTransport().apply {
            position = 42_000L
            stopAction = { stopGate.await() }
        }
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集")
        runCurrent()
        controller.disconnect()
        runCurrent()
        assertTrue(controller.state.value.disconnecting)
        controller.castTo(device, "episode2", "第二集")
        runCurrent()
        assertEquals(listOf("episode1"), remote.urls)
        stopGate.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.isActive)
        assertEquals("episode1", controller.stopped.value?.url)
        assertEquals(42_000L, controller.stopped.value?.positionMs)
        controller.castTo(device, "episode2", "第二集")
        runCurrent()
        assertEquals("episode2", controller.state.value.url)
    }

    @Test
    fun disconnectDuringFirstLoadDoesNotLeaveSpinnerOrSendNewPlay() = runTest {
        val remote = FakeTransport().apply { load = { awaitCancellation() } }
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集")
        runCurrent()
        controller.disconnect()
        runCurrent()
        assertFalse(controller.state.value.isActive)
        assertNull(controller.state.value.connectingDevice)
        assertEquals(1, remote.stops)
        assertNull(controller.stopped.value?.positionMs)
    }

    @Test
    fun failedEpisodePushDisconnectsWithoutApplyingPreviousEpisodePosition() = runTest {
        val remote = FakeTransport()
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集")
        runCurrent()
        remote.load = { DlnaCastResult.Rejected }
        controller.pushIfConnected("episode2", "第二集")
        runCurrent()
        assertFalse(controller.state.value.isActive)
        assertEquals("episode2", controller.stopped.value?.url)
        assertNull(controller.stopped.value?.positionMs)
    }

    @Test
    fun slowSeekDoesNotDelayConnectedState() = runTest {
        val remote = FakeTransport().apply { seekAction = { awaitCancellation() } }
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集", 42_000L)
        runCurrent()
        assertEquals(device, controller.state.value.device)
        assertNull(controller.state.value.connectingDevice)
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(device, controller.state.value.device)
    }

    @Test
    fun repeatedStoppedStateEndsSessionEvenIfPlayingWasNeverObserved() = runTest {
        val remote = FakeTransport().apply { transportStateValue = "STOPPED" }
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集")
        runCurrent()
        advanceTimeBy(9_001L)
        runCurrent()
        assertFalse(controller.state.value.isActive)
        assertEquals("episode1", controller.stopped.value?.url)
    }

    @Test
    fun lostDeviceDisconnectsAfterThreeFailures() = runTest {
        val remote = FakeTransport().apply { transportStateValue = null }
        val controller = DlnaSessionController(backgroundScope, remote)
        controller.castTo(device, "episode1", "第一集")
        runCurrent()
        advanceTimeBy(9_001L)
        runCurrent()
        assertFalse(controller.state.value.isActive)
    }

    @Test
    fun stopProgressIsConsumedOnlyByMatchingEvent() = runTest {
        val controller = DlnaSessionController(backgroundScope, FakeTransport())
        controller.castTo(device, "episode1", "第一集")
        runCurrent()
        controller.disconnect()
        runCurrent()
        val event = requireNotNull(controller.stopped.value)
        controller.acknowledgeStopped(event.id - 1)
        assertEquals(event, controller.stopped.value)
        controller.acknowledgeStopped(event.id)
        assertNull(controller.stopped.value)
    }

    private class FakeTransport : DlnaTransport {
        val urls = mutableListOf<String>()
        var stops = 0
        var position: Long? = 0L
        var transportStateValue: String? = "PLAYING"
        var load: suspend (String) -> DlnaCastResult = { DlnaCastResult.Success }
        var stopAction: suspend () -> Unit = {}
        var seekAction: suspend () -> Boolean = { true }
        override suspend fun play(device: DlnaDevice, url: String, title: String): DlnaCastResult {
            urls += url
            return load(url)
        }
        override suspend fun stop(device: DlnaDevice) { stops++; stopAction() }
        override suspend fun seek(device: DlnaDevice, positionMs: Long) = seekAction()
        override suspend fun positionMs(device: DlnaDevice) = position
        override suspend fun transportState(device: DlnaDevice) = transportStateValue
    }
}
