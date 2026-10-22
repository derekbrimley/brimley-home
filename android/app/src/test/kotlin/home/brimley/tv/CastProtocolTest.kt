package home.brimley.tv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val video = CastVideo("https://site/movies/beach/video?k=x", "Beach Day", "https://site/movies/beach/poster?k=x")
private fun fromTv(namespace: String, payload: String, source: String = "receiver-0") = CastMessage(source, CastProtocol.SENDER, namespace, payload)
private fun CastMessage.type() = Json.parseToJsonElement(payload).jsonObject["type"]!!.jsonPrimitive.content

class CastProtocolTest {
    @Test fun aMessageSurvivesTheWire() {
        val m = CastMessage("sender-brimley", "receiver-0", CastProtocol.NS_RECEIVER, """{"type":"LAUNCH"}""")
        assertEquals(m, CastMessage.decode(m.encode()))
        assertEquals(listOf(0x08, 0x00), m.encode().take(2).map { it.toInt() })   // protocol_version 0 is written
    }

    @Test fun launchesTheReceiverThenLoadsTheFile() {
        val p = CastProtocol(video)
        assertEquals(listOf("CONNECT", "LAUNCH"), p.start().map { it.type() })

        val other = """{"type":"RECEIVER_STATUS","status":{"applications":[{"appId":"E8C28D3C","transportId":"t-home"}]}}"""
        assertEquals(emptyList<CastMessage>(), p.onMessage(fromTv(CastProtocol.NS_RECEIVER, other)))

        val running = """{"type":"RECEIVER_STATUS","requestId":1,"status":{"applications":[{"appId":"CC1AD845","transportId":"t-7"}]}}"""
        val replies = p.onMessage(fromTv(CastProtocol.NS_RECEIVER, running))
        assertEquals(listOf("CONNECT", "LOAD"), replies.map { it.type() })
        assertEquals(listOf("t-7", "t-7"), replies.map { it.destination })
        val media = Json.parseToJsonElement(replies[1].payload).jsonObject["media"]!!.jsonObject
        assertEquals(video.url, media["contentId"]!!.jsonPrimitive.content)
        assertFalse(p.done)

        p.onMessage(fromTv(CastProtocol.NS_MEDIA, """{"type":"MEDIA_STATUS","requestId":0,"status":[]}""", "t-7"))
        assertFalse(p.done)
        p.onMessage(fromTv(CastProtocol.NS_MEDIA, """{"type":"MEDIA_STATUS","requestId":2,"status":[]}""", "t-7"))
        assertTrue(p.done)
        assertNull(p.failure)
    }

    @Test fun answersPings() {
        val replies = CastProtocol(video).onMessage(fromTv(CastProtocol.NS_HEARTBEAT, """{"type":"PING"}"""))
        assertEquals(listOf("PONG"), replies.map { it.type() })
        assertEquals("receiver-0", replies[0].destination)
    }

    @Test fun saysWhenTheTvCantPlayIt() {
        val p = CastProtocol(video)
        p.onMessage(fromTv(CastProtocol.NS_MEDIA, """{"type":"LOAD_FAILED","requestId":2}""", "t-7"))
        assertEquals("The TV couldn't play this video", p.failure)
    }
}
