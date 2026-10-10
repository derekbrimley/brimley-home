package home.brimley.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryTest {
    @Test fun retriesWhileTheSpeakerIsOffThenSucceeds() = runTest {
        var calls = 0
        val r = retryWhileSpeakerOff { if (++calls < 3) throw ApiException("The kitchen speaker isn't on (Spotify sees no devices)") else "ok" }
        assertEquals("ok", r)
        assertEquals(3, calls)
    }

    @Test fun otherErrorsAreNotRetried() = runTest {
        var calls = 0
        val e = runCatching { retryWhileSpeakerOff<Unit> { calls++; throw ApiException("Crate 500") } }.exceptionOrNull()
        assertEquals("Crate 500", e?.message)
        assertEquals(1, calls)
    }

    @Test fun givesUpAfterTheLastAttempt() = runTest {
        var calls = 0
        val e = runCatching { retryWhileSpeakerOff<Unit>(attempts = 3) { calls++; throw ApiException("The kitchen speaker isn't on") } }.exceptionOrNull()
        assertEquals(3, calls)
        assertEquals("The kitchen speaker isn't on", e?.message)
    }
}
