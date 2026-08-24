package com.dnsspeedtest.app.dns

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.random.Random

object DnsCodec {
    fun encodeQuery(
        domain: String,
        type: RecordType,
        id: Int = Random.nextInt(0, 0xFFFF),
        recursionDesired: Boolean = true,
    ): ByteArray {
        val qname = encodeName(normalizeDomain(domain))
        val buffer = ByteBuffer.allocate(12 + qname.size + 4 + 11)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(id.toShort())
        val flags = if (recursionDesired) 0x0100 else 0
        buffer.putShort(flags.toShort())
        buffer.putShort(1)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.putShort(1)
        buffer.put(qname)
        buffer.putShort(type.code.toShort())
        buffer.putShort(1)
        // EDNS0 OPT pseudo-record, UDP payload 1232
        buffer.put(0)
        buffer.putShort(41)
        buffer.putShort(1232)
        buffer.putInt(0)
        buffer.putShort(0)
        return buffer.array().copyOf(buffer.position())
    }

    fun decodeMessage(bytes: ByteArray): DnsMessageView {
        require(bytes.size >= 12) { "DNS 报文过短" }
        val reader = MessageReader(bytes)
        val id = reader.readU16()
        val flags = reader.readU16()
        val qdCount = reader.readU16()
        val anCount = reader.readU16()
        val nsCount = reader.readU16()
        val arCount = reader.readU16()
        val questions = buildList {
            repeat(qdCount) {
                val name = reader.readName()
                val type = reader.readU16()
                val clazz = reader.readU16()
                add("$name ${RecordType.fromCode(type)?.label ?: "TYPE$type"} IN($clazz)")
            }
        }
        val answers = reader.readRecords(anCount)
        val authorities = reader.readRecords(nsCount)
        val additionals = reader.readRecords(arCount).filter { it.type != "OPT" }
        val flagNames = buildList {
            if (flags and 0x8000 != 0) add("QR")
            if (flags and 0x0400 != 0) add("AA")
            if (flags and 0x0200 != 0) add("TC")
            if (flags and 0x0100 != 0) add("RD")
            if (flags and 0x0080 != 0) add("RA")
            if (flags and 0x0010 != 0) add("AD")
            if (flags and 0x0020 != 0) add("CD")
            add("OPCODE=${(flags shr 11) and 0xF}")
        }
        return DnsMessageView(
            id = id,
            rcode = rcodeName(flags and 0xF),
            flags = flagNames,
            questions = questions,
            answers = answers,
            authorities = authorities,
            additionals = additionals,
        )
    }

    fun normalizeDomain(domain: String): String {
        val trimmed = domain.trim().trimEnd('.').lowercase()
        require(trimmed.isNotEmpty()) { "域名不能为空" }
        require(!trimmed.contains(' ')) { "域名格式无效" }
        return trimmed
    }

    private fun encodeName(domain: String): ByteArray {
        val labels = domain.split('.').filter { it.isNotEmpty() }
        val size = labels.sumOf { 1 + it.length } + 1
        val buffer = ByteBuffer.allocate(size)
        for (label in labels) {
            val bytes = label.toByteArray(StandardCharsets.US_ASCII)
            require(bytes.size in 1..63) { "域名标签长度无效: $label" }
            buffer.put(bytes.size.toByte())
            buffer.put(bytes)
        }
        buffer.put(0)
        return buffer.array()
    }

    private class MessageReader(private val data: ByteArray) {
        var offset: Int = 0

        fun readU16(): Int {
            val value = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 2
            return value
        }

        fun readU32(): Int {
            val value = ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
            offset += 4
            return value
        }

        fun readBytes(length: Int): ByteArray {
            val slice = data.copyOfRange(offset, offset + length)
            offset += length
            return slice
        }

        fun readName(): String = readNameAt(offset, 0).also { result ->
            offset = result.second
        }.first

        private fun readNameAt(start: Int, depth: Int): Pair<String, Int> {
            require(depth < 20) { "压缩指针循环" }
            var cursor = start
            val labels = mutableListOf<String>()
            while (true) {
                require(cursor < data.size) { "域名截断" }
                val length = data[cursor].toInt() and 0xFF
                when {
                    length == 0 -> return labels.joinToString(".") to cursor + 1
                    length and 0xC0 == 0xC0 -> {
                        require(cursor + 1 < data.size) { "压缩指针截断" }
                        val pointer = ((length and 0x3F) shl 8) or (data[cursor + 1].toInt() and 0xFF)
                        val (suffix, _) = readNameAt(pointer, depth + 1)
                        if (suffix.isNotEmpty()) labels += suffix
                        return labels.joinToString(".") to cursor + 2
                    }
                    length and 0xC0 != 0 -> error("不支持的标签类型")
                    else -> {
                        cursor++
                        require(cursor + length <= data.size) { "标签截断" }
                        labels += String(data, cursor, length, StandardCharsets.UTF_8)
                        cursor += length
                    }
                }
            }
        }

        fun readRecords(count: Int): List<DnsRecord> = buildList {
            repeat(count) {
                val name = readName()
                val type = readU16()
                val clazz = readU16()
                val ttl = readU32()
                val rdLength = readU16()
                val rdata = readBytes(rdLength)
                val typeName = if (type == 41) "OPT" else RecordType.fromCode(type)?.label ?: "TYPE$type"
                add(
                    DnsRecord(
                        name = name.ifEmpty { "." },
                        type = typeName,
                        ttl = ttl,
                        data = decodeRdata(type, clazz, rdata),
                    ),
                )
            }
        }

        private fun decodeRdata(type: Int, clazz: Int, rdata: ByteArray): String {
            if (clazz != 1 && type != 41) return "CLASS $clazz ${rdata.toHex()}"
            return try {
                when (type) {
                    1 -> InetAddress.getByAddress(rdata).hostAddress ?: rdata.toHex()
                    28 -> InetAddress.getByAddress(rdata).hostAddress ?: rdata.toHex()
                    2, 5, 12 -> readEmbeddedName(rdata)
                    15 -> {
                        if (rdata.size < 3) rdata.toHex() else {
                            val preference = ((rdata[0].toInt() and 0xFF) shl 8) or (rdata[1].toInt() and 0xFF)
                            val exchange = readEmbeddedName(rdata, start = 2)
                            "$preference $exchange"
                        }
                    }
                    16 -> decodeTxt(rdata)
                    6 -> decodeSoa(rdata)
                    65, 64 -> decodeSvcb(rdata)
                    41 -> "EDNS payload=${clazz}"
                    else -> rdata.toHex()
                }
            } catch (_: Exception) {
                rdata.toHex()
            }
        }

        private fun readEmbeddedName(rdata: ByteArray, start: Int = 0): String {
            val combined = ByteArray(offset + rdata.size)
            System.arraycopy(data, 0, combined, 0, offset)
            System.arraycopy(rdata, 0, combined, offset, rdata.size)
            val nested = MessageReader(combined)
            return nested.readNameAt(offset + start, 0).first.ifEmpty { "." }
        }

        private fun decodeSoa(rdata: ByteArray): String {
            val combined = ByteArray(offset + rdata.size)
            System.arraycopy(data, 0, combined, 0, offset)
            System.arraycopy(rdata, 0, combined, offset, rdata.size)
            val nested = MessageReader(combined)
            val (mname, afterM) = nested.readNameAt(offset, 0)
            nested.offset = afterM
            val (rname, afterR) = nested.readNameAt(afterM, 0)
            nested.offset = afterR
            val serial = nested.readU32().toUInt()
            val refresh = nested.readU32()
            val retry = nested.readU32()
            val expire = nested.readU32()
            val minimum = nested.readU32()
            return "$mname $rname $serial $refresh $retry $expire $minimum"
        }

        private fun decodeSvcb(rdata: ByteArray): String {
            if (rdata.size < 3) return rdata.toHex()
            val priority = ((rdata[0].toInt() and 0xFF) shl 8) or (rdata[1].toInt() and 0xFF)
            val target = readEmbeddedName(rdata, start = 2)
            return "$priority $target"
        }
    }

    private fun decodeTxt(rdata: ByteArray): String {
        val parts = mutableListOf<String>()
        var i = 0
        while (i < rdata.size) {
            val length = rdata[i].toInt() and 0xFF
            i++
            if (i + length > rdata.size) break
            parts += String(rdata, i, length, StandardCharsets.UTF_8)
            i += length
        }
        return parts.joinToString(" ")
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
