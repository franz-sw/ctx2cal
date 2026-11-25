package de.franzsw.ctx2cal

import de.franzsw.ctx2cal.core.domain.download.DownloadErrorMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadErrorMapperTest {

    @Test
    fun `constants mirror android DownloadManager values`() {
        assertEquals(1000, DownloadErrorMapper.ERROR_UNKNOWN)
        assertEquals(1001, DownloadErrorMapper.ERROR_FILE_ERROR)
        assertEquals(1002, DownloadErrorMapper.ERROR_UNHANDLED_HTTP_CODE)
        assertEquals(1004, DownloadErrorMapper.ERROR_HTTP_DATA_ERROR)
        assertEquals(1005, DownloadErrorMapper.ERROR_TOO_MANY_REDIRECTS)
        assertEquals(1006, DownloadErrorMapper.ERROR_INSUFFICIENT_SPACE)
        assertEquals(1007, DownloadErrorMapper.ERROR_DEVICE_NOT_FOUND)
        assertEquals(1008, DownloadErrorMapper.ERROR_CANNOT_RESUME)
        assertEquals(1, DownloadErrorMapper.PAUSED_WAITING_TO_RETRY)
        assertEquals(2, DownloadErrorMapper.PAUSED_WAITING_FOR_NETWORK)
        assertEquals(3, DownloadErrorMapper.PAUSED_QUEUED_FOR_WIFI)
        assertEquals(4, DownloadErrorMapper.PAUSED_UNKNOWN)
    }

    @Test
    fun `error messages for storage reasons`() {
        assertEquals(
            "Not enough free storage space to download the model.",
            DownloadErrorMapper.errorMessage(1006),
        )
        assertEquals(
            "Storage device not available.",
            DownloadErrorMapper.errorMessage(1007),
        )
        assertEquals(
            "Could not write the downloaded file to storage.",
            DownloadErrorMapper.errorMessage(1001),
        )
    }

    @Test
    fun `error messages for network reasons`() {
        val networkMsg = "Network/server error while downloading the model."
        assertEquals(networkMsg, DownloadErrorMapper.errorMessage(1004))
        assertEquals(networkMsg, DownloadErrorMapper.errorMessage(1002))
        assertEquals(
            "Too many redirects from the model server.",
            DownloadErrorMapper.errorMessage(1005),
        )
        assertEquals(
            "Download interrupted and could not resume. Please retry.",
            DownloadErrorMapper.errorMessage(1008),
        )
    }

    @Test
    fun `error messages for http codes and unknown`() {
        assertEquals(
            "Server returned HTTP 404 while downloading the model.",
            DownloadErrorMapper.errorMessage(404),
        )
        assertEquals(
            "Server returned HTTP 500 while downloading the model.",
            DownloadErrorMapper.errorMessage(500),
        )
        assertEquals(
            "Model download failed. Check your connection and try again.",
            DownloadErrorMapper.errorMessage(1000),
        )
        assertEquals(
            "Model download failed. Check your connection and try again.",
            DownloadErrorMapper.errorMessage(0),
        )
    }

    @Test
    fun `pause messages`() {
        assertEquals(
            "Waiting for network connection…",
            DownloadErrorMapper.pauseMessage(2),
        )
        assertEquals(
            "Waiting for Wi-Fi…",
            DownloadErrorMapper.pauseMessage(3),
        )
        assertEquals(
            "Connection interrupted, retrying…",
            DownloadErrorMapper.pauseMessage(1),
        )
        assertEquals(
            "Download paused…",
            DownloadErrorMapper.pauseMessage(4),
        )
        assertEquals(
            "Download paused…",
            DownloadErrorMapper.pauseMessage(0),
        )
    }

    @Test
    fun `truncated size is incomplete`() {
        assertTrue(DownloadErrorMapper.isIncompleteDownload(actualSize = 100_000_000L, expectedSize = 380_000_000L))
        assertTrue(DownloadErrorMapper.isIncompleteDownload(actualSize = 0L, expectedSize = 380_000_000L))
        assertFalse(DownloadErrorMapper.isIncompleteDownload(actualSize = 300_000_000L, expectedSize = 380_000_000L))
        assertFalse(DownloadErrorMapper.isIncompleteDownload(actualSize = 190_000_000L, expectedSize = 380_000_000L))
        assertTrue(DownloadErrorMapper.isIncompleteDownload(actualSize = 189_999_999L, expectedSize = 380_000_000L))
        assertFalse(DownloadErrorMapper.isIncompleteDownload(actualSize = 0L, expectedSize = null))
        assertFalse(DownloadErrorMapper.isIncompleteDownload(actualSize = 0L, expectedSize = 0L))
    }

    @Test
    fun `incomplete message formats megabyes`() {
        assertEquals(
            "Downloaded model looks incomplete (100 MB). Please retry.",
            DownloadErrorMapper.incompleteMessage(100_900_000L),
        )
        assertEquals(
            "Downloaded model looks incomplete (0 MB). Please retry.",
            DownloadErrorMapper.incompleteMessage(0L),
        )
    }
}
