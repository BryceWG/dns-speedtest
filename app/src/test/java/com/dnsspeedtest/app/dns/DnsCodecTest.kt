package com.dnsspeedtest.app.dns

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCodecTest {
    @Test
    fun encodeQuery_containsDomainAndType() {
        val query = DnsCodec.encodeQuery("www.example.com", RecordType.A, id = 0x1234)
        val decoded = DnsCodec.decodeMessage(query)
        assertEquals(0x1234, decoded.id)
        assertTrue(decoded.questions.first().contains("www.example.com"))
        assertTrue(decoded.questions.first().contains("A"))
    }

    @Test
    fun decodeMessage_readsARecordAndCompression() {
        val response = buildAResponse("example.com", "93.184.216.34")
        val decoded = DnsCodec.decodeMessage(response)
        assertEquals("NOERROR", decoded.rcode)
        assertEquals(1, decoded.answers.size)
        assertEquals("A", decoded.answers[0].type)
        assertEquals("93.184.216.34", decoded.answers[0].data)
        assertEquals("example.com", decoded.answers[0].name)
    }

    private fun buildAResponse(name: String, ip: String): ByteArray {
        val labels = name.split('.').filter { it.isNotEmpty() }
        val qname = buildList {
            labels.forEach { label ->
                add(label.length.toByte())
                addAll(label.encodeToByteArray().toList())
            }
            add(0)
        }.toByteArray()
        val ipBytes = ip.split('.').map { it.toInt().toByte() }.toByteArray()
        val buffer = ByteBuffer.allocate(512)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(0x1111)
        buffer.putShort(0x8180.toShort())
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.put(qname)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.put(0xC0.toByte())
        buffer.put(12)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(60)
        buffer.putShort(4)
        buffer.put(ipBytes)
        return buffer.array().copyOf(buffer.position())
    }
}
