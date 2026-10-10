package home.brimley.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val closed = CompletableDeferred<Unit>()
    override suspend fun send(frame: ByteArray) {
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
    override suspend fun connect(host: String, pinnedCertSha256: String): TvSession {
        attempts += host
        if (certChanged) throw TvCertificateChanged()
        return sessions[host] ?: throw IOException("no route to $host")
    }
    override suspend fun startPairing(host: String): PairingSession = error("not used")
}

private class MemoryStore(override var host: String? = "10.0.0.5", override var serverCertSha256: String? = "pin") : TvStore {
    override fun clear() { host = null; serverCertSha256 = null }
}

private val bluey = StartedTitle("Bluey", "Disney+", null)
private const val LINK = "https://www.disneyplus.com/series/bluey/1"

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
}
