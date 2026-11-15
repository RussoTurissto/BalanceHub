package com.github.russoturissto.buttonapp.app

/** Applies local volume reduction to standard 16-bit PCM WAV sound data. */
internal object WavSoundGain {
    fun attenuate(source: ByteArray, percent: Int): ByteArray {
        val gain = percent.coerceIn(0, 100)
        if (gain == 100 || source.size < 44) return source
        fun u16(at: Int): Int = (source[at].toInt() and 255) or
            ((source[at + 1].toInt() and 255) shl 8)
        fun u32(at: Int): Long = (u16(at).toLong() and 65535L) or
            ((u16(at + 2).toLong() and 65535L) shl 16)
        fun tag(at: Int, word: String): Boolean = word.indices.all {
            (source[at + it].toInt() and 255) == word[it].code
        }
        if (!tag(0, "RIFF") || !tag(8, "WAVE")) return source
        var offset = 12
        var pcm = false
        var beginning = -1
        var ending = -1
        while (offset + 8 <= source.size) {
            val chunk = u32(offset + 4)
            if (chunk > source.size.toLong() - offset - 8) return source
            val start = offset + 8
            val end = start + chunk.toInt()
            if (tag(offset, "fmt ") && chunk >= 16L) {
                pcm = u16(start) == 1 && u16(start + 14) == 16 &&
                    u16(start + 2) in 1..2
            }
            if (tag(offset, "data")) {
                beginning = start
                ending = end
            }
            offset = end + (chunk.toInt() and 1)
        }
        if (!pcm || beginning < 0 || ending < 0) return source
        val result = source.copyOf()
        var index = beginning
        while (index + 1 < ending) {
            val signed = ((source[index].toInt() and 255) or
                (source[index + 1].toInt() shl 8)).toShort().toInt()
            val adjusted = signed * gain / 100
            result[index] = adjusted.toByte()
            result[index + 1] = (adjusted shr 8).toByte()
            index += 2
        }
        return result
    }
}
