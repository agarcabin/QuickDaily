package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorThumbnailPolicyTest {
    @Test
    fun selectsPlatformThumbnailOnlyFromApi29() {
        assertEquals(EditorThumbnailStrategy.StreamDecode, EditorThumbnailPolicy.strategy(26))
        assertEquals(EditorThumbnailStrategy.StreamDecode, EditorThumbnailPolicy.strategy(28))
        assertEquals(EditorThumbnailStrategy.PlatformThumbnail, EditorThumbnailPolicy.strategy(29))
        assertEquals(EditorThumbnailStrategy.PlatformThumbnail, EditorThumbnailPolicy.strategy(35))
    }

    @Test
    fun choosesBoundedPowerOfTwoSamplingForLegacyDecode() {
        assertEquals(1, EditorThumbnailPolicy.sampleSize(0, 0, 120))
        assertEquals(1, EditorThumbnailPolicy.sampleSize(120, 120, 120))
        assertEquals(2, EditorThumbnailPolicy.sampleSize(240, 120, 120))
        assertEquals(8, EditorThumbnailPolicy.sampleSize(500, 500, 120))
        assertTrue(EditorThumbnailPolicy.sampleSize(4000, 2000, 120) > 1)
    }
}
