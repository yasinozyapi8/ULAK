package com.ulak.tv.data.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight MPEG-TS PSI analyzer used only when Media3 renders video but
 * cannot see an audio track. It reads PAT/PMT tables directly from the stream
 * so we can distinguish "the source has no audio PID" from
 * "an audio PID exists but Media3 did not expose it".
 */
object TsPsiAnalyzer {

    data class ElementaryStream(
        val pid: Int,
        val streamType: Int,
        val codec: String,
        val kind: Kind
    ) {
        enum class Kind { VIDEO, AUDIO, OTHER }
    }

    data class Analysis(
        val sourceKind: String,
        val pmtPid: Int?,
        val videoStreams: List<ElementaryStream>,
        val audioStreams: List<ElementaryStream>,
        val otherStreams: List<ElementaryStream>,
        val packetsScanned: Int,
        val note: String
    ) {
        val firstVideo: ElementaryStream? get() = videoStreams.firstOrNull()
        val firstAudio: ElementaryStream? get() = audioStreams.firstOrNull()
    }

    suspend fun analyze(url: String): Result<Analysis> = withContext(Dispatchers.IO) {
        runCatching { analyzeInternal(url) }
    }

    private fun analyzeInternal(inputUrl: String): Analysis {
        val resolved = resolveToTransportStream(inputUrl, depth = 0)
        val bytes = downloadPrefix(resolved.url, MAX_TS_BYTES)
        val syncOffset = findTsSyncOffset(bytes)
            ?: return Analysis(
                sourceKind = resolved.kind,
                pmtPid = null,
                videoStreams = emptyList(),
                audioStreams = emptyList(),
                otherStreams = emptyList(),
                packetsScanned = 0,
                note = "MPEG-TS paket senkronu (0x47) bulunamadı"
            )

        var pmtPid: Int? = null
        var pmtStreams: List<ElementaryStream>? = null
        var packets = 0
        val assemblers = mutableMapOf<Int, PsiAssembler>()
        assemblers[0] = PsiAssembler()

        var pos = syncOffset
        while (pos + TS_PACKET_SIZE <= bytes.size) {
            if ((bytes[pos].toInt() and 0xFF) != 0x47) {
                pos++
                continue
            }
            packets++

            val b1 = bytes[pos + 1].toInt() and 0xFF
            val b2 = bytes[pos + 2].toInt() and 0xFF
            val b3 = bytes[pos + 3].toInt() and 0xFF
            val payloadUnitStart = (b1 and 0x40) != 0
            val pid = ((b1 and 0x1F) shl 8) or b2
            val adaptationControl = (b3 ushr 4) and 0x03

            if (adaptationControl == 1 || adaptationControl == 3) {
                var payloadOffset = pos + 4
                if (adaptationControl == 3) {
                    if (payloadOffset >= pos + TS_PACKET_SIZE) {
                        pos += TS_PACKET_SIZE
                        continue
                    }
                    val adaptationLength = bytes[payloadOffset].toInt() and 0xFF
                    payloadOffset += 1 + adaptationLength
                }
                val packetEnd = pos + TS_PACKET_SIZE
                if (payloadOffset < packetEnd) {
                    val assembler = assemblers[pid]
                    if (assembler != null) {
                        val sections = assembler.feed(bytes, payloadOffset, packetEnd, payloadUnitStart)
                        for (section in sections) {
                            if (pid == 0 && section.isNotEmpty() && (section[0].toInt() and 0xFF) == 0x00) {
                                val discovered = parsePat(section)
                                if (discovered != null && discovered != pmtPid) {
                                    pmtPid = discovered
                                    assemblers[discovered] = PsiAssembler()
                                }
                            } else if (pmtPid != null && pid == pmtPid && section.isNotEmpty() && (section[0].toInt() and 0xFF) == 0x02) {
                                pmtStreams = parsePmt(section)
                                if (!pmtStreams.isNullOrEmpty()) break
                            }
                        }
                    }
                }
            }

            if (!pmtStreams.isNullOrEmpty()) break
            pos += TS_PACKET_SIZE
        }

        val streams = pmtStreams.orEmpty()
        val video = streams.filter { it.kind == ElementaryStream.Kind.VIDEO }
        val audio = streams.filter { it.kind == ElementaryStream.Kind.AUDIO }
        val other = streams.filter { it.kind == ElementaryStream.Kind.OTHER }

        val note = when {
            pmtPid == null -> "PAT bulundu ancak PMT PID çözülemedi"
            streams.isEmpty() -> "PMT PID bulundu fakat akış bileşenleri çözülemedi"
            audio.isNotEmpty() -> "PMT içinde ses PID'si var; Media3 ses göstermiyorsa demux/codec tarafı incelenmeli"
            else -> "PMT içinde ses PID'si bulunamadı; bu yayın yolunun kendisi sessiz görünüyor"
        }

        return Analysis(
            sourceKind = resolved.kind,
            pmtPid = pmtPid,
            videoStreams = video,
            audioStreams = audio,
            otherStreams = other,
            packetsScanned = packets,
            note = note
        )
    }

    private data class ResolvedSource(val url: String, val kind: String)

    private fun resolveToTransportStream(url: String, depth: Int): ResolvedSource {
        if (depth >= 3) return ResolvedSource(url, "MPEG-TS")
        val lower = url.lowercase()
        if (!lower.contains(".m3u8")) return ResolvedSource(url, "MPEG-TS")

        val text = downloadText(url, MAX_PLAYLIST_BYTES)
        if (!text.trimStart().startsWith("#EXTM3U")) {
            return ResolvedSource(url, "MPEG-TS")
        }

        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val isMasterPlaylist = lines.any { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
        val firstUri = lines.firstOrNull { !it.startsWith("#") }
            ?: error("HLS listesinde oynatılabilir alt liste/segment bulunamadı")

        return if (isMasterPlaylist) {
            resolveToTransportStream(resolveUrl(url, firstUri), depth + 1)
        } else {
            ResolvedSource(resolveUrl(url, firstUri), "HLS → MPEG-TS segment")
        }
    }

    private fun resolveUrl(baseUrl: String, child: String): String = URL(URL(baseUrl), child).toString()

    private fun downloadText(url: String, maxBytes: Int): String =
        downloadPrefix(url, maxBytes).toString(Charsets.UTF_8)

    private fun downloadPrefix(url: String, maxBytes: Int): ByteArray {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; Android TV) ULAK/0.3.2")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Range", "bytes=0-${maxBytes - 1}")
            }
            val code = connection.responseCode
            if (code !in 200..299) error("PSI analizi HTTP $code")
            val out = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (total < maxBytes) {
                    val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
            }
            out.toByteArray()
        } finally {
            connection?.disconnect()
        }
    }

    private fun findTsSyncOffset(bytes: ByteArray): Int? {
        val maxStart = minOf(bytes.size - TS_PACKET_SIZE * 4, TS_PACKET_SIZE * 8)
        if (maxStart < 0) return null
        for (offset in 0..maxStart) {
            if ((bytes[offset].toInt() and 0xFF) == 0x47 &&
                (bytes[offset + TS_PACKET_SIZE].toInt() and 0xFF) == 0x47 &&
                (bytes[offset + TS_PACKET_SIZE * 2].toInt() and 0xFF) == 0x47 &&
                (bytes[offset + TS_PACKET_SIZE * 3].toInt() and 0xFF) == 0x47
            ) return offset
        }
        return null
    }

    private fun parsePat(section: ByteArray): Int? {
        if (section.size < 12 || (section[0].toInt() and 0xFF) != 0x00) return null
        val sectionLength = ((section[1].toInt() and 0x0F) shl 8) or (section[2].toInt() and 0xFF)
        val sectionEnd = minOf(section.size, 3 + sectionLength)
        var p = 8
        val entriesEnd = sectionEnd - 4 // CRC32
        while (p + 4 <= entriesEnd) {
            val programNumber = ((section[p].toInt() and 0xFF) shl 8) or (section[p + 1].toInt() and 0xFF)
            val pid = ((section[p + 2].toInt() and 0x1F) shl 8) or (section[p + 3].toInt() and 0xFF)
            if (programNumber != 0) return pid
            p += 4
        }
        return null
    }

    private fun parsePmt(section: ByteArray): List<ElementaryStream> {
        if (section.size < 16 || (section[0].toInt() and 0xFF) != 0x02) return emptyList()
        val sectionLength = ((section[1].toInt() and 0x0F) shl 8) or (section[2].toInt() and 0xFF)
        val sectionEnd = minOf(section.size, 3 + sectionLength)
        val programInfoLength = ((section[10].toInt() and 0x0F) shl 8) or (section[11].toInt() and 0xFF)
        var p = 12 + programInfoLength
        val entriesEnd = sectionEnd - 4
        val out = mutableListOf<ElementaryStream>()

        while (p + 5 <= entriesEnd) {
            val streamType = section[p].toInt() and 0xFF
            val elementaryPid = ((section[p + 1].toInt() and 0x1F) shl 8) or (section[p + 2].toInt() and 0xFF)
            val esInfoLength = ((section[p + 3].toInt() and 0x0F) shl 8) or (section[p + 4].toInt() and 0xFF)
            val descriptorStart = p + 5
            val descriptorEnd = minOf(descriptorStart + esInfoLength, entriesEnd)
            val descriptorBytes = if (descriptorStart < descriptorEnd) section.copyOfRange(descriptorStart, descriptorEnd) else ByteArray(0)
            val (codec, kind) = classifyStream(streamType, descriptorBytes)
            out += ElementaryStream(elementaryPid, streamType, codec, kind)
            p = descriptorStart + esInfoLength
        }
        return out
    }

    private fun classifyStream(type: Int, descriptors: ByteArray): Pair<String, ElementaryStream.Kind> {
        return when (type) {
            0x01 -> "MPEG-1 Video" to ElementaryStream.Kind.VIDEO
            0x02 -> "MPEG-2 Video" to ElementaryStream.Kind.VIDEO
            0x1B -> "AVC / H.264" to ElementaryStream.Kind.VIDEO
            0x24 -> "HEVC / H.265" to ElementaryStream.Kind.VIDEO
            0x03 -> "MPEG-1 Audio" to ElementaryStream.Kind.AUDIO
            0x04 -> "MPEG-2 Audio" to ElementaryStream.Kind.AUDIO
            0x0F -> "AAC (ADTS)" to ElementaryStream.Kind.AUDIO
            0x11 -> "AAC (LATM)" to ElementaryStream.Kind.AUDIO
            0x81 -> "AC-3" to ElementaryStream.Kind.AUDIO
            0x87 -> "E-AC-3" to ElementaryStream.Kind.AUDIO
            0x06 -> classifyPrivateStream(descriptors)
            else -> "0x${type.toString(16).uppercase().padStart(2, '0')}" to ElementaryStream.Kind.OTHER
        }
    }

    private fun classifyPrivateStream(descriptors: ByteArray): Pair<String, ElementaryStream.Kind> {
        var p = 0
        while (p + 2 <= descriptors.size) {
            val tag = descriptors[p].toInt() and 0xFF
            val len = descriptors[p + 1].toInt() and 0xFF
            val end = minOf(p + 2 + len, descriptors.size)
            when (tag) {
                0x6A -> return "AC-3 (private)" to ElementaryStream.Kind.AUDIO
                0x7A -> return "E-AC-3 (private)" to ElementaryStream.Kind.AUDIO
                0x7C -> return "AAC (private)" to ElementaryStream.Kind.AUDIO
                0x05 -> {
                    val reg = descriptors.copyOfRange(p + 2, end).toString(Charsets.ISO_8859_1).uppercase()
                    if (reg.contains("AC-3") || reg.contains("AC3")) return "AC-3 (registration)" to ElementaryStream.Kind.AUDIO
                    if (reg.contains("EAC3") || reg.contains("EC-3")) return "E-AC-3 (registration)" to ElementaryStream.Kind.AUDIO
                }
            }
            p = end
        }
        return "Private PES" to ElementaryStream.Kind.OTHER
    }

    /** Reassembles PSI sections that may span multiple 188-byte TS packets. */
    private class PsiAssembler {
        private val buffer = ByteArrayOutputStream()

        fun feed(packet: ByteArray, start: Int, end: Int, payloadUnitStart: Boolean): List<ByteArray> {
            val completed = mutableListOf<ByteArray>()
            var cursor = start

            if (payloadUnitStart) {
                if (cursor >= end) return completed
                val pointer = packet[cursor].toInt() and 0xFF
                cursor++
                if (pointer > 0 && buffer.size() > 0) {
                    val take = minOf(pointer, end - cursor)
                    buffer.write(packet, cursor, take)
                    cursor += take
                    extractCompleted(completed)
                } else {
                    cursor = minOf(cursor + pointer, end)
                }
                if (buffer.size() > 0) buffer.reset()
            }

            if (cursor < end) {
                buffer.write(packet, cursor, end - cursor)
                extractCompleted(completed)
            }
            return completed
        }

        private fun extractCompleted(out: MutableList<ByteArray>) {
            while (true) {
                val data = buffer.toByteArray()
                if (data.isEmpty() || (data[0].toInt() and 0xFF) == 0xFF) {
                    buffer.reset()
                    return
                }
                if (data.size < 3) return
                val sectionLength = ((data[1].toInt() and 0x0F) shl 8) or (data[2].toInt() and 0xFF)
                val total = 3 + sectionLength
                if (total <= 3 || total > 4096) {
                    buffer.reset()
                    return
                }
                if (data.size < total) return
                out += data.copyOfRange(0, total)
                buffer.reset()
                if (data.size > total) buffer.write(data, total, data.size - total)
            }
        }
    }

    private const val TS_PACKET_SIZE = 188
    private const val MAX_TS_BYTES = 2 * 1024 * 1024
    private const val MAX_PLAYLIST_BYTES = 512 * 1024
}
