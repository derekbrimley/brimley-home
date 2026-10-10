package home.brimley.tv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

// Just enough protobuf for the Android TV remote protocol: varints and
// length-delimited fields, written by hand so there's no codegen step.
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter { writeVarint(out, field.toLong() shl 3); writeVarint(out, value); return this }
    fun varint(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())
    fun bool(field: Int, value: Boolean): ProtoWriter = varint(field, if (value) 1L else 0L)
    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        writeVarint(out, (field.toLong() shl 3) or 2L)
        writeVarint(out, value.size.toLong())
        out.write(value)
        return this
    }
    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))
    fun message(field: Int, build: ProtoWriter.() -> Unit): ProtoWriter = bytes(field, ProtoWriter().apply(build).toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()
}

// One decoded message: field number -> values in arrival order (Long for
// varints, ByteArray for length-delimited). The last value wins on read.
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int): Boolean = fields.containsKey(field)
    fun long(field: Int): Long? = fields[field]?.lastOrNull() as? Long
    fun int(field: Int): Int? = long(field)?.toInt()
    fun bool(field: Int): Boolean = (long(field) ?: 0L) != 0L
    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = linkedMapOf<Int, MutableList<Any>>()
            var i = 0
            fun readVarint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    if (i >= data.size) throw EOFException("truncated varint")
                    val b = data[i++].toInt() and 0xff
                    result = result or ((b and 0x7f).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IllegalArgumentException("varint too long")
                }
            }
            while (i < data.size) {
                val key = readVarint()
                val field = (key ushr 3).toInt()
                when ((key and 7L).toInt()) {
                    0 -> fields.getOrPut(field) { mutableListOf() }.add(readVarint())
                    2 -> {
                        val len = readVarint().toInt()
                        if (len < 0 || i + len > data.size) throw EOFException("truncated field $field")
                        fields.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(i, i + len))
                        i += len
                    }
                    1 -> i += 8   // fixed64: not used by the TV, skipped
                    5 -> i += 4   // fixed32: likewise
                    else -> throw IllegalArgumentException("wire type ${key and 7L}")
                }
            }
            return ProtoMessage(fields)
        }
    }
}

internal fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (true) {
        if (v and 0x7fL.inv() == 0L) { out.write(v.toInt()); return }
        out.write(((v and 0x7fL) or 0x80L).toInt())
        v = v ushr 7
    }
}

// Both ports frame each message with a varint length.
fun writeFrame(out: OutputStream, message: ByteArray) {
    val b = ByteArrayOutputStream()
    writeVarint(b, message.size.toLong())
    b.write(message)
    out.write(b.toByteArray())
    out.flush()
}

fun readFrame(input: InputStream): ByteArray {
    var shift = 0
    var len = 0L
    while (true) {
        val b = input.read()
        if (b < 0) throw EOFException("connection closed")
        len = len or ((b and 0x7f).toLong() shl shift)
        if (b and 0x80 == 0) break
        shift += 7
    }
    if (len > 1_000_000) throw IllegalArgumentException("frame too large")
    val buf = ByteArray(len.toInt())
    var off = 0
    while (off < buf.size) {
        val n = input.read(buf, off, buf.size - off)
        if (n < 0) throw EOFException("connection closed")
        off += n
    }
    return buf
}
