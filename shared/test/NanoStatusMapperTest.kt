package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.llm.NanoStatusMapper
import de.franzsw.ctx2cal.core.domain.model.ModelStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NanoStatusMapperTest {

    @Test
    fun `available status maps to NanoAvailable`() {
        assertEquals(
            ModelStatus.NanoAvailable(null),
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_AVAILABLE, baseModelName = null),
        )
        assertEquals(
            ModelStatus.NanoAvailable("v3"),
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_AVAILABLE, baseModelName = "nano-v3"),
        )
        assertTrue(NanoStatusMapper.isNanoReady(NanoStatusMapper.FEATURE_AVAILABLE, baseModelName = null))
    }

    @Test
    fun `downloadable with non-blank baseModelName maps to NanoAvailable`() {
        assertEquals(
            ModelStatus.NanoAvailable("v3"),
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_DOWNLOADABLE, baseModelName = "nano-v3"),
        )
        assertTrue(NanoStatusMapper.isNanoReady(NanoStatusMapper.FEATURE_DOWNLOADABLE, baseModelName = "nano-v3"))
    }

    @Test
    fun `downloading with non-blank baseModelName maps to NanoAvailable`() {
        assertEquals(
            ModelStatus.NanoAvailable("v3"),
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_DOWNLOADING, baseModelName = "nano-v3"),
        )
        assertTrue(NanoStatusMapper.isNanoReady(NanoStatusMapper.FEATURE_DOWNLOADING, baseModelName = "nano-v3"))
    }

    @Test
    fun `downloadable with null or blank baseModelName maps to NanoDownloadable`() {
        assertEquals(
            ModelStatus.NanoDownloadable,
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_DOWNLOADABLE, baseModelName = null),
        )
        assertEquals(
            ModelStatus.NanoDownloadable,
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_DOWNLOADABLE, baseModelName = "   "),
        )
        assertFalse(NanoStatusMapper.isNanoReady(NanoStatusMapper.FEATURE_DOWNLOADABLE, baseModelName = null))
        assertFalse(NanoStatusMapper.isNanoReady(NanoStatusMapper.FEATURE_DOWNLOADABLE, baseModelName = ""))
    }

    @Test
    fun `downloading with null or blank baseModelName maps to NanoDownloading`() {
        assertEquals(
            ModelStatus.NanoDownloading,
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_DOWNLOADING, baseModelName = null),
        )
        assertEquals(
            ModelStatus.NanoDownloading,
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_DOWNLOADING, baseModelName = ""),
        )
        assertFalse(NanoStatusMapper.isNanoReady(NanoStatusMapper.FEATURE_DOWNLOADING, baseModelName = null))
    }

    @Test
    fun `unavailable status maps to NanoUnavailable even if baseModelName is present`() {
        assertEquals(
            ModelStatus.NanoUnavailable,
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_UNAVAILABLE, baseModelName = null),
        )
        assertEquals(
            ModelStatus.NanoUnavailable,
            NanoStatusMapper.mapNanoStatus(NanoStatusMapper.FEATURE_UNAVAILABLE, baseModelName = "nano-v3"),
        )
        assertFalse(NanoStatusMapper.isNanoReady(NanoStatusMapper.FEATURE_UNAVAILABLE, baseModelName = "nano-v3"))
    }

    @Test
    fun `parseNanoVersion extracts version and formats displayName`() {
        assertEquals("v3", NanoStatusMapper.parseNanoVersion("nano-v3"))
        assertEquals("v2", NanoStatusMapper.parseNanoVersion("gemini-nano-v2"))
        assertEquals("v3.1", NanoStatusMapper.parseNanoVersion("nano_v3.1"))
        assertEquals("v4", NanoStatusMapper.parseNanoVersion("v4"))
        assertEquals("v3", NanoStatusMapper.parseNanoVersion("nano-3"))
        assertEquals("v3", NanoStatusMapper.parseNanoVersion("3"))
        assertEquals("custom", NanoStatusMapper.parseNanoVersion("custom"))
        assertEquals(null, NanoStatusMapper.parseNanoVersion(null))
        assertEquals(null, NanoStatusMapper.parseNanoVersion("   "))

        assertEquals("Gemini Nano v3", ModelStatus.NanoAvailable("v3").displayName)
        assertEquals("Gemini Nano", ModelStatus.NanoAvailable(null).displayName)
    }
}
