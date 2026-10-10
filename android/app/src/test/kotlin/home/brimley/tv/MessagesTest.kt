package home.brimley.tv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class MessagesTest {
    @Test fun pairingMessages() {
        assertEquals("080210c80152190a0961747672656d6f7465120c4272696d6c657920486f6d65", PairingMessages.request("Brimley Home").hex())
        assertEquals("080210c801a201080a04080310061801", PairingMessages.options().hex())
        assertEquals("080210c801f201080a04080310061001", PairingMessages.configuration().hex())
        assertEquals(200, PairingMessages.status(PairingMessages.options()))
    }

    // Vector: SHA-256 over unsigned big-endian client modulus, client exponent,
    // server modulus, server exponent, then the code's last four hex digits.
    private val clientMod = BigInteger("C3A1".repeat(64), 16)
    private val serverMod = BigInteger("9F3B".repeat(64), 16)
    private val e = BigInteger.valueOf(65537)
    private val secretHex = "31c6c1db00a06eb01949edff4b7e58ab1d467ace128153274066522e7b620760"

    @Test fun pairingSecretForTheRightCode() {
        assertEquals(secretHex, PairingSecret.compute("31A1B2", clientMod, e, serverMod, e)?.hex())
    }

    @Test fun codesAreForgivingAboutCaseAndSpaces() {
        assertEquals(secretHex, PairingSecret.compute(" 31a1b2 ", clientMod, e, serverMod, e)?.hex())
    }

    @Test fun aMistypedCodeIsCaughtLocally() {
        assertNull(PairingSecret.compute("32A1B2", clientMod, e, serverMod, e))   // wrong check byte
        assertNull(PairingSecret.compute("31A1B", clientMod, e, serverMod, e))    // too short
        assertNull(PairingSecret.compute("31A1BZ", clientMod, e, serverMod, e))   // not hex
        assertNull(PairingSecret.normalize("31A1B2C"))
    }

    @Test fun remoteMessages() {
        assertEquals("0a2c08ee0412270a0444432d3112084461796c6967687418012201312a0961747672656d6f74653205312e302e30", RemoteMessages.configure("DC-1", "Daylight").hex())
        assertEquals("120308ee04", RemoteMessages.setActive().hex())
        assertEquals("4a020807", RemoteMessages.pingResponse(7).hex())
        assertEquals("5204081a1003", RemoteMessages.key(KeyCode.POWER).hex())
        assertEquals("d205280a2668747470733a2f2f7777772e6e6574666c69782e636f6d2f7469746c652f3830313137323931", RemoteMessages.appLink("https://www.netflix.com/title/80117291").hex())
    }

    @Test fun protocolAnswersTheTvAndTracksState() {
        val p = RemoteProtocol("DC-1", "Daylight")
        assertArrayEquals(RemoteMessages.configure("DC-1", "Daylight"), p.onMessage("0a1908ee0412140a0a4368726f6d65636173741206476f6f676c65".unhex()).single())
        assertArrayEquals(RemoteMessages.setActive(), p.onMessage("120308ee04".unhex()).single())
        assertArrayEquals(RemoteMessages.pingResponse(7), p.onMessage("420408071009".unhex()).single())
        assertNull(p.status.poweredOn)

        assertTrue(p.onMessage("c202020801".unhex()).isEmpty())
        assertEquals(true, p.status.poweredOn)
        p.onMessage("a201170a1508036211636f6d2e6e6574666c69782e6e696e6a61".unhex())
        assertEquals("com.netflix.ninja", p.status.appPackage)
        p.onMessage("920306306438194000".unhex())
        assertEquals(25, p.status.volume)
        assertEquals(100, p.status.volumeMax)
        p.onMessage("c20200".unhex())
        assertEquals(false, p.status.poweredOn)
    }
}
