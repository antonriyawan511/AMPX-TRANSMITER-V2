package com.example.amplifierpro

import com.example.amplifierpro.audio.EqProcessor
import com.example.amplifierpro.ui.EqualizerFragment
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Unit test murni-JVM untuk logika pemetaan EQ (tanpa android.*).
 *
 * Yang dikunci di sini adalah inti perbaikan sesi:
 *  - setiap grid band (5/7/10/15) adalah SUBSET dari grid 30-band, sehingga
 *    round-trip "save band apa pun -> 30 -> load band apa pun" bersifat LOSSLESS;
 *  - interpolasi gain memakai skala logaritmik dan bernilai tepat di titik grid;
 *  - kurva flat tetap flat (mencegah bug "slot kosong = 0" yang membuat kurva tenggelam).
 *
 * CATATAN: hanya fungsi statis murni yang dipanggil. setBandCount()/updateChannelL/R()
 * TIDAK boleh dipanggil di JVM test karena memakai android.util.Log.
 */
class EqMappingTest {

    /** Gain acak deterministik dengan nilai ekstrem (0 dan 24) agar error interpolasi terlihat. */
    private fun randomGains(n: Int, seed: Long): IntArray {
        val rnd = Random(seed)
        val out = IntArray(n)
        for (i in 0 until n) out[i] = rnd.nextInt(25) // 0..24, 12 = flat
        if (n > 0) out[0] = 0
        if (n > 1) out[n - 1] = 24
        return out
    }

    // ---------------------------------------------------------------- 1. tabel grid

    @Test
    fun freqsForBandCount_returnsExpectedTables() {
        assertSame(EqProcessor.FREQS_5, EqProcessor.getFreqsForBandCount(5))
        assertSame(EqProcessor.FREQS_7, EqProcessor.getFreqsForBandCount(7))
        assertSame(EqProcessor.FREQS_10, EqProcessor.getFreqsForBandCount(10))
        assertSame(EqProcessor.FREQS_15, EqProcessor.getFreqsForBandCount(15))
        assertSame(EqProcessor.FREQS_30, EqProcessor.getFreqsForBandCount(30))

        // jumlah elemen tabel harus konsisten dengan namanya
        assertEquals(5, EqProcessor.getFreqsForBandCount(5).size)
        assertEquals(7, EqProcessor.getFreqsForBandCount(7).size)
        assertEquals(10, EqProcessor.getFreqsForBandCount(10).size)
        assertEquals(15, EqProcessor.getFreqsForBandCount(15).size)
        assertEquals(30, EqProcessor.getFreqsForBandCount(30).size)

        // count tak dikenal -> fallback grid 30-band
        assertSame(EqProcessor.FREQS_30, EqProcessor.getFreqsForBandCount(42))
        assertSame(EqProcessor.FREQS_30, EqProcessor.getFreqsForBandCount(0))
    }

    /**
     * INVARIANT INTI: grid 5/7/10/15 harus subset tepat dari grid 30-band.
     * Inilah alasan matematis mengapa round-trip remap bersifat lossless.
     */
    @Test
    fun allBandGrids_areSubsetOf30Grid() {
        val g30 = EqProcessor.FREQS_30
        for (count in intArrayOf(5, 7, 10, 15)) {
            val g = EqProcessor.getFreqsForBandCount(count)
            for (f in g) {
                assertTrue(
                    "freq $f Hz (grid $count-band) tidak ada di grid 30-band",
                    g30.any { it == f }
                )
            }
        }
    }

    // ---------------------------------------------------------------- 2. remapGains

    @Test
    fun remap_sameCount_returnsSameInstance() {
        val src = randomGains(10, 1L)
        assertSame(src, EqProcessor.remapGains(src, 10))
        assertSame(src, EqProcessor.remapGains(src, 10))
    }

    @Test
    fun remap_handlesNullAndEmpty() {
        assertEquals(null, EqProcessor.remapGains(null, 30))
        val empty = IntArray(0)
        assertSame(empty, EqProcessor.remapGains(empty, 30))
    }

    @Test
    fun remap_roundTrip_isLossless_forAllBandCounts() {
        for (count in intArrayOf(5, 7, 10, 15)) {
            val original = randomGains(count, 100L + count)
            val to30 = EqProcessor.remapGains(original, 30)
            assertEquals(30, to30.size)
            val back = EqProcessor.remapGains(to30, count)
            assertArrayEquals(
                "round-trip $count -> 30 -> $count harus identik",
                original, back
            )
        }
    }

    @Test
    fun remap_isLossless_5_to_30_to_5() {
        val original = intArrayOf(0, 12, 24, 6, 18)
        val back = EqProcessor.remapGains(EqProcessor.remapGains(original, 30), 5)
        assertArrayEquals(original, back)
    }

    @Test
    fun remap_isLossless_15_to_30_to_15() {
        val original = randomGains(15, 15L)
        val back = EqProcessor.remapGains(EqProcessor.remapGains(original, 30), 15)
        assertArrayEquals(original, back)
    }

    @Test
    fun remap_isLossless_10_and_7() {
        val ten = randomGains(10, 10L)
        assertArrayEquals(ten, EqProcessor.remapGains(EqProcessor.remapGains(ten, 30), 10))

        val seven = randomGains(7, 7L)
        assertArrayEquals(seven, EqProcessor.remapGains(EqProcessor.remapGains(seven, 30), 7))
    }

    @Test
    fun remap_30to5_picksGridPoints() {
        val src30 = randomGains(30, 30L)
        val out5 = EqProcessor.remapGains(src30, 5)
        assertEquals(5, out5.size)

        val g5 = EqProcessor.FREQS_5
        val g30 = EqProcessor.FREQS_30
        for (j in g5.indices) {
            val idxIn30 = g30.indexOfFirst { it == g5[j] }
            assertTrue("grid 5-band ${g5[j]}Hz harus ada di grid 30-band", idxIn30 >= 0)
            assertEquals(
                "nilai 30->5 di ${g5[j]}Hz harus persis nilai grid 30-band",
                src30[idxIn30], out5[j]
            )
        }
    }

    @Test
    fun remap_30to5_isLosslessOnThoseGridPoints() {
        // nilai yang diambil 30->5 harus tetap utuh bila dimapping balik 5->30 di titik grid 5-band
        val src30 = randomGains(30, 31L)
        val out5 = EqProcessor.remapGains(src30, 5)
        val back30 = EqProcessor.remapGains(out5, 30)
        val g5 = EqProcessor.FREQS_5
        val g30 = EqProcessor.FREQS_30
        for (j in g5.indices) {
            val idxIn30 = g30.indexOfFirst { it == g5[j] }
            assertEquals(src30[idxIn30], back30[idxIn30])
        }
    }

    // ---------------------------------------------------------------- 3. interpolateGains

    @Test
    fun interpolateGains_isLosslessAtGridPoints() {
        for (count in intArrayOf(5, 7, 10, 15)) {
            val srcF = EqProcessor.getFreqsForBandCount(count)
            val srcG = randomGains(count, 200L + count).map { it.toFloat() }.toFloatArray()
            val tgt = EqualizerFragment.interpolateGains(srcF, srcG, EqProcessor.FREQS_30)
            assertEquals(30, tgt.size)

            // setiap titik grid sumber harus terbaca persis (lossless)
            for (i in srcF.indices) {
                val idxIn30 = EqProcessor.FREQS_30.indexOfFirst { it == srcF[i] }
                assertTrue("grid ${srcF[i]}Hz harus ada di grid 30-band", idxIn30 >= 0)
                assertEquals(
                    "gain di ${srcF[i]}Hz harus tepat untuk grid $count-band",
                    srcG[i], tgt[idxIn30], 0.0f
                )
            }

            // dan remap balik lewat interpolateGains harus mengembalikan nilai asli
            val back = EqualizerFragment.interpolateGains(EqProcessor.FREQS_30, tgt, srcF)
            for (i in srcF.indices) {
                assertEquals("round-trip interpolate $count-band", srcG[i], back[i], 0.0f)
            }
        }
    }

    @Test
    fun interpolateGains_flat_staysFlat() {
        val flat5 = FloatArray(5) { 12f }
        val out = EqualizerFragment.interpolateGains(EqProcessor.FREQS_5, flat5, EqProcessor.FREQS_30)
        for (v in out) {
            assertEquals("kurva flat harus tetap flat 12 (tidak tenggelam ke 0)", 12f, v, 0.0f)
        }
    }

    @Test
    fun interpolateGains_singlePointSource_fillsTarget() {
        val out = EqualizerFragment.interpolateGains(
            floatArrayOf(1000f), floatArrayOf(7f), EqProcessor.FREQS_30
        )
        assertEquals(30, out.size)
        for (v in out) assertEquals(7f, v, 0.0f)
    }

    @Test
    fun interpolateGains_emptySource_returnsZeroArrayOfTargetSize() {
        val out = EqualizerFragment.interpolateGains(FloatArray(0), FloatArray(0), EqProcessor.FREQS_30)
        assertEquals(30, out.size)
        for (v in out) assertEquals(0f, v, 0.0f)
    }

    // ---------------------------------------------------------------- 4. konsistensi tabel UI vs DSP

    @Test
    fun uiGridTables_matchDspGridTables() {
        assertArrayEquals(EqProcessor.FREQS_30, EqualizerFragment.FREQS_30, 0.0f)
        assertArrayEquals(EqProcessor.FREQS_15, EqualizerFragment.FREQS_15, 0.0f)
        assertArrayEquals(EqProcessor.FREQS_10, EqualizerFragment.FREQS_10, 0.0f)
        assertArrayEquals(EqProcessor.FREQS_7, EqualizerFragment.FREQS_7, 0.0f)
        assertArrayEquals(EqProcessor.FREQS_5, EqualizerFragment.FREQS_5, 0.0f)
    }

    @Test
    fun maxBands_coversAllGrids() {
        assertTrue(EqProcessor.MAX_BANDS >= EqProcessor.FREQS_30.size)
        for (count in intArrayOf(5, 7, 10, 15, 30)) {
            assertTrue(EqProcessor.getFreqsForBandCount(count).size <= EqProcessor.MAX_BANDS)
        }
    }
}
