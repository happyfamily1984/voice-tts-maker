package com.happyfamily.voicetts.engine

import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16비트 모노 WAV 읽기/쓰기. */
object Wav {
    class Audio(val pcm: ShortArray, val sampleRate: Int)

    fun write(out: OutputStream, pcm: ShortArray, sampleRate: Int) {
        val bb = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(pcm.size * 2)
        bb.asShortBuffer().put(pcm)
        out.write(bb.array())
    }

    fun write(file: File, pcm: ShortArray, sampleRate: Int) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { write(it, pcm, sampleRate) }
        if (!tmp.renameTo(file)) { tmp.copyTo(file, true); tmp.delete() }
    }

    fun read(file: File): Audio = file.inputStream().buffered().use { read(it) }

    fun read(input: InputStream): Audio {
        val d = DataInputStream(input)
        fun i32(): Int { val b = ByteArray(4); d.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int }
        fun i16(): Int { val b = ByteArray(2); d.readFully(b); return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toInt() }
        fun tag(): String { val b = ByteArray(4); d.readFully(b); return String(b, Charsets.US_ASCII) }
        require(tag() == "RIFF") { "WAV 파일이 아닙니다" }
        i32(); require(tag() == "WAVE") { "WAV 파일이 아닙니다" }
        var rate = 0; var channels = 1; var bits = 16
        while (true) {
            val t = tag(); val len = i32()
            when (t) {
                "fmt " -> {
                    i16(); channels = i16(); rate = i32(); i32(); i16(); bits = i16()
                    if (len > 16) d.skipBytes(len - 16)
                }
                "data" -> {
                    require(bits == 16) { "16비트 WAV만 지원합니다" }
                    val b = ByteArray(len); d.readFully(b)
                    val all = ShortArray(len / 2)
                    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(all)
                    val pcm = if (channels <= 1) all else ShortArray(all.size / channels) { all[it * channels] }
                    return Audio(pcm, rate)
                }
                else -> d.skipBytes(len + (len and 1))
            }
        }
    }
}
