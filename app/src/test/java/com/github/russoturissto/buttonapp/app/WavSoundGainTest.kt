package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WavSoundGainTest {
    private fun wav(samples: ShortArray): ByteArray {
        val result = ByteArray(44 + samples.size * 2)
        fun string(offset: Int, s: String) { s.forEachIndexed { i, c -> result[offset + i] = c.code.toByte() } }
        fun number(offset: Int, value: Int, count: Int) {
            repeat(count) { result[offset + it] = (value ushr (8 * it)).toByte() }
        }
        string(0, "RIFF")
        number(4, result.size - 8, 4)
        string(8, "WAVE")
        string(12, "fmt ")
        number(16, 16, 4)
        number(20, 1, 2)
        number(22, 1, 2)
        number(24, 22050, 4)
        number(28, 44100, 4)
        number(32, 2, 2)
        number(34, 16, 2)
        string(36, "data")
        number(40, samples.size * 2, 4)
        samples.forEachIndexed { i, sample -> number(44 + i * 2, sample.toInt(), 2) }
        return result
    }

    private fun read(bytes: ByteArray, offset: Int): Short =
        ((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort()

    @Test fun gainReducesSamplesAndRetainsHeader() {
        val input = wav(shortArrayOf(16000, -16000, 1000, 0))
        val result = WavSoundGain.attenuate(input, 25)
        assertEquals(4000, read(result, 44).toInt())
        assertEquals(-4000, read(result, 46).toInt())
        assertEquals(250, read(result, 48).toInt())
        assertEquals(0, read(result, 50).toInt())
        assertEquals(input.size, result.size)
        assertEquals(input.sliceArray(0 until 44).toList(), result.sliceArray(0 until 44).toList())
    }
    @Test fun fullGainKeepsOriginal() {
        val input = wav(shortArrayOf(100, 200))
        assertSame(input, WavSoundGain.attenuate(input, 100))
    }
    @Test fun zeroGainDoesNotAlterHeader() {
        val input = wav(shortArrayOf(24000, -24000))
        val output = WavSoundGain.attenuate(input, 0)
        assertEquals(0, read(output, 44).toInt())
        assertEquals(0, read(output, 46).toInt())
        assertEquals(input.sliceArray(0 until 44).toList(), output.sliceArray(0 until 44).toList())
    }
}
