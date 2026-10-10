package home.brimley.tv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
fun String.unhex(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

class ProtoTest {
    @Test fun writesVarintsStringsAndNestedMessages() {
        val bytes = ProtoWriter().varint(1, 2).varint(2, 200).message(10) { string(1, "atvremote"); string(2, "Brimley Home") }.toByteArray()
        assertEquals("080210c80152190a0961747672656d6f7465120c4272696d6c657920486f6d65", bytes.hex())
    }

    @Test fun readsWhatItWrites() {
        val m = ProtoMessage.parse(ProtoWriter().varint(1, 622).bool(3, true).message(2) { string(12, "com.netflix.ninja") }.toByteArray())
        assertEquals(622, m.int(1))
        assertEquals(true, m.bool(3))
        assertEquals("com.netflix.ninja", m.message(2)?.string(12))
        assertNull(m.message(9))
        assertEquals(false, m.bool(9))
    }

    @Test fun emptyNestedMessageIsPresentButBlank() {
        // proto3 leaves out false: remote_start{started=false} arrives as c20200
        val m = ProtoMessage.parse("c20200".unhex())
        assertEquals(false, m.message(40)?.bool(1))
    }

    @Test fun framesRoundTripPastOneLengthByte() {
        val payload = ByteArray(300) { it.toByte() }
        val out = ByteArrayOutputStream()
        writeFrame(out, payload)
        assertEquals("ac02", out.toByteArray().copyOfRange(0, 2).hex())
        assertArrayEquals(payload, readFrame(ByteArrayInputStream(out.toByteArray())))
    }

    @Test(expected = EOFException::class) fun truncatedFrameThrows() {
        readFrame(ByteArrayInputStream("0501".unhex()))
    }
}
