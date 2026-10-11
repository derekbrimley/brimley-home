package home.brimley.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeSession(on: Boolean?) : TvSession {
    override val status = MutableStateFlow(RemoteStatus(poweredOn = on))
    val sent = mutableListOf<ByteArray>()
    var powerTurnsOn = true
    var sendsFail = false
    private val closed = CompletableDeferred<Unit>()
    override suspend fun send(frame: ByteArray) {
        if (sendsFail) throw IOException("connection reset")
        sent += frame
        if (frame.contentEquals(RemoteMessages.key(KeyCode.POWER)) && powerTurnsOn) {
            status.value = status.value.copy(poweredOn = status.value.poweredOn != true)
        }
    }
    override suspend fun awaitClosed() = closed.await()
    override fun close() { closed.complete(Unit) }
}

private class FakeConnector(val sessions: MutableMap<String, FakeSession> = mutableMapOf()) : TvConnector {
    val attempts = mutableListOf<String>()
    var certChanged = false
    val certChangedHosts = mutableSetOf<String>()
    val connectDelayMs = mutableMapOf<String, Long>()
    override suspend fun connect(host: String, pinnedCertSha256: String): TvSession {
        attempts += host
        connectDelayMs[host]?.let { kotlinx.coroutines.delay(it) }
        if (certChanged || host in certChangedHosts) throw TvCertificateChanged()
        return sessions[host] ?: throw IOException("no route to $host")
    }
    override suspend fun startPairing(host: String): PairingSession = error("not used")
}

private class MemoryStore(override var host: String? = "10.0.0.5", override var serverCertSha256: String? = "pin") : TvStore {
    override fun clear() { host = null; serverCertSha256 = null }
}

private val bluey = StartedTitle("Bluey", "Disney+", null)
private const val LINK = "https://www.disneyplus.com/series/bluey/1"
private const val MAX_LINK = "https://play.max.com/movie/abc"

@OptIn(ExperimentalCoroutinesApi::class)
class TvControllerTest {
    @Test fun playOnAnOffTvTurnsItOnThenOpensTheTitle() = runTest {
        val tv = FakeSession(on = false)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        assertEquals(TvState.Off, c.state.value)

        assertTrue(c.play(LINK, bluey).isSuccess)
        assertEquals(listOf(RemoteMessages.key(KeyCode.POWER).hex(), RemoteMessages.appLink(LINK).hex()), tv.sent.map { it.hex() })
        runCurrent()
        assertEquals(TvState.On(null, bluey), c.state.value)
    }

    @Test fun playSaysSoWhenTheTvNeverWakes() = runTest {
        val tv = FakeSession(on = false).apply { powerTurnsOn = false }
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        assertEquals("The TV didn't turn on", c.play(LINK, bluey).exceptionOrNull()?.message)
        assertEquals(1, tv.sent.size)   // power only, no link
    }

    @Test fun tvOffDoesNothingWhenTheTvIsAlreadyOffOrUnknown() = runTest {
        for (initial in listOf(false, null)) {
            val tv = FakeSession(on = initial)
            val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
            c.start(); runCurrent()
            c.powerOff()
            assertTrue(tv.sent.isEmpty())
        }
    }

    @Test fun tvOffSendsPowerWhenOnAndForgetsTheTitle() = runTest {
        val tv = FakeSession(on = true)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        c.play(LINK, bluey)
        c.powerOff(); runCurrent()
        assertEquals(TvState.Off, c.state.value)
        tv.status.value = tv.status.value.copy(poweredOn = true); runCurrent()
        assertEquals(TvState.On(null, null), c.state.value)
    }

    @Test fun aNewAddressIsFoundAndRemembered() = runTest {
        val store = MemoryStore(host = "10.0.0.5")
        val conn = FakeConnector(mutableMapOf("10.0.0.9" to FakeSession(on = true)))
        val c = TvController(store, conn, { "10.0.0.9" }, backgroundScope)
        c.start(); runCurrent()
        assertEquals(listOf("10.0.0.5", "10.0.0.9"), conn.attempts)
        assertEquals("10.0.0.9", store.host)
        assertEquals(TvState.On(null, null), c.state.value)
    }

    @Test fun aChangedCertificateMeansPairAgain() = runTest {
        val store = MemoryStore()
        val c = TvController(store, FakeConnector().apply { certChanged = true }, { null }, backgroundScope)
        c.start(); runCurrent()
        assertEquals(TvState.NotPaired, c.state.value)
        assertNull(store.serverCertSha256)
    }

    @Test fun unreachableTvFailsPlayAfterOneRetry() = runTest {
        val conn = FakeConnector()
        val c = TvController(MemoryStore(), conn, { null }, backgroundScope)
        c.start(); runCurrent()
        assertEquals(TvState.Unreachable, c.state.value)
        val before = conn.attempts.size
        assertEquals("Couldn't reach the TV", c.play(LINK, bluey).exceptionOrNull()?.message)
        assertTrue(conn.attempts.size > before)   // play kicked an immediate reconnect
    }

    @Test fun playBeforePairingFailsWithNotPaired() = runTest {
        val c = TvController(MemoryStore(host = null, serverCertSha256 = null), FakeConnector(), { null }, backgroundScope)
        c.start(); runCurrent()
        assertEquals(TvState.NotPaired, c.state.value)
        assertTrue(c.play(LINK, bluey).exceptionOrNull() is TvNotPaired)
    }

    @Test fun playWaitsForAnUnknownPowerStateInsteadOfToggling() = runTest {
        val tv = FakeSession(on = null)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        backgroundScope.launch { kotlinx.coroutines.delay(500); tv.status.value = RemoteStatus(poweredOn = true) }
        assertTrue(c.play(LINK, bluey).isSuccess)
        assertEquals(listOf(RemoteMessages.appLink(LINK).hex()), tv.sent.map { it.hex() })
    }

    @Test fun playWithAPowerStateThatNeverArrivesSendsJustTheLink() = runTest {
        val tv = FakeSession(on = null)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        assertTrue(c.play(LINK, bluey).isSuccess)
        assertEquals(listOf(RemoteMessages.appLink(LINK).hex()), tv.sent.map { it.hex() })
    }

    @Test fun aDifferentTvFoundOnTheNetworkDoesNotErasePairing() = runTest {
        val store = MemoryStore(host = "10.0.0.5")
        val conn = FakeConnector().apply { certChangedHosts += "10.0.0.7" }
        val c = TvController(store, conn, { "10.0.0.7" }, backgroundScope)
        c.start(); runCurrent()
        assertEquals(TvState.Unreachable, c.state.value)
        assertEquals("pin", store.serverCertSha256)
        assertEquals("10.0.0.5", store.host)
    }

    @Test fun remoteButtonsSurviveADeadConnection() = runTest {
        val tv = FakeSession(on = true)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        tv.sendsFail = true
        c.volumeUp(); c.volumeDown(); c.playPause(); c.powerOff()   // must not throw
    }

    @Test fun playWaitsLongEnoughForANewAddressToBeFound() = runTest {
        val store = MemoryStore(host = "10.0.0.5")
        val conn = FakeConnector(mutableMapOf("10.0.0.9" to FakeSession(on = true)))
        conn.connectDelayMs["10.0.0.5"] = 5_000   // the dead address times out
        var discoveries = 0
        val c = TvController(store, conn, { discoveries++; if (discoveries == 1) null else { kotlinx.coroutines.delay(6_000); "10.0.0.9" } }, backgroundScope)
        c.start(); advanceTimeBy(5_001); runCurrent()
        assertEquals(TvState.Unreachable, c.state.value)
        assertTrue(c.play(LINK, bluey).isSuccess)
        assertEquals("10.0.0.9", store.host)
    }

    private fun FakeSession.oks() = sent.count { it.contentEquals(RemoteMessages.key(KeyCode.DPAD_CENTER)) }

    @Test fun maxFromColdPressesOkForTheProfileThenForPlay() = runTest {
        val tv = FakeSession(on = true)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        assertTrue(c.play(MAX_LINK, bluey).isSuccess)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(0, tv.oks())   // nothing until the app is in front
        tv.status.value = tv.status.value.copy(appPackage = "com.wbd.stream"); runCurrent()
        advanceTimeBy(5_001); runCurrent()
        assertEquals(1, tv.oks())
        advanceTimeBy(4_001); runCurrent()
        assertEquals(2, tv.oks())
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, tv.oks())
    }

    @Test fun maxAlreadyOpenSkipsTheProfilePress() = runTest {
        val tv = FakeSession(on = true).apply { status.value = status.value.copy(appPackage = "com.wbd.stream") }
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        c.play(MAX_LINK, bluey)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, tv.oks())
    }

    @Test fun primePressesOkOnce() = runTest {
        val tv = FakeSession(on = true)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        c.play("https://www.amazon.com/gp/video/detail/B0ABC", bluey)
        tv.status.value = tv.status.value.copy(appPackage = "com.amazon.amazonvideo.livingroom"); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, tv.oks())
    }

    @Test fun okPressesStopWhenAnotherAppComesToTheFront() = runTest {
        val tv = FakeSession(on = true)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        c.play(MAX_LINK, bluey)
        tv.status.value = tv.status.value.copy(appPackage = "com.wbd.stream"); runCurrent()
        advanceTimeBy(5_001); runCurrent()
        tv.status.value = tv.status.value.copy(appPackage = "com.google.android.apps.tv.launcherx"); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, tv.oks())
    }

    @Test fun aTabletButtonStopsTheOkPresses() = runTest {
        val tv = FakeSession(on = true)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        c.play(MAX_LINK, bluey)
        tv.status.value = tv.status.value.copy(appPackage = "com.wbd.stream"); runCurrent()
        c.playPause()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, tv.oks())
    }

    @Test fun appsThatPlayOnTheirOwnGetNoPresses() = runTest {
        val tv = FakeSession(on = true)
        val c = TvController(MemoryStore(), FakeConnector(mutableMapOf("10.0.0.5" to tv)), { null }, backgroundScope)
        c.start(); runCurrent()
        c.play("https://www.youtube.com/watch?v=abc", bluey)
        tv.status.value = tv.status.value.copy(appPackage = "com.google.android.youtube.tv"); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, tv.oks())
    }
}
