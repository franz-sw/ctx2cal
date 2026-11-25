package de.franzsw.ctx2cal.core.domain.download

/**
 * Pure-Kotlin mapping for DownloadManager reason codes and the model-integrity gate.
 *
 * Int constants mirror android.app.DownloadManager values so shared tests can
 * run without an Android runtime; Android delegates with its own constants, which
 * are numerically identical.
 */
object DownloadErrorMapper {

    const val ERROR_UNKNOWN = 1000
    const val ERROR_FILE_ERROR = 1001
    const val ERROR_UNHANDLED_HTTP_CODE = 1002
    const val ERROR_HTTP_DATA_ERROR = 1004
    const val ERROR_TOO_MANY_REDIRECTS = 1005
    const val ERROR_INSUFFICIENT_SPACE = 1006
    const val ERROR_DEVICE_NOT_FOUND = 1007
    const val ERROR_CANNOT_RESUME = 1008

    const val PAUSED_WAITING_TO_RETRY = 1
    const val PAUSED_WAITING_FOR_NETWORK = 2
    const val PAUSED_QUEUED_FOR_WIFI = 3
    const val PAUSED_UNKNOWN = 4

    fun errorMessage(reason: Int): String = when (reason) {
        ERROR_INSUFFICIENT_SPACE -> "Not enough free storage space to download the model."
        ERROR_HTTP_DATA_ERROR,
        ERROR_UNHANDLED_HTTP_CODE -> "Network/server error while downloading the model."
        ERROR_TOO_MANY_REDIRECTS -> "Too many redirects from the model server."
        ERROR_CANNOT_RESUME -> "Download interrupted and could not resume. Please retry."
        ERROR_DEVICE_NOT_FOUND -> "Storage device not available."
        ERROR_FILE_ERROR -> "Could not write the downloaded file to storage."
        in 400..599 -> "Server returned HTTP $reason while downloading the model."
        else -> "Model download failed. Check your connection and try again."
    }

    fun pauseMessage(reason: Int): String = when (reason) {
        PAUSED_WAITING_FOR_NETWORK -> "Waiting for network connection…"
        PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi…"
        PAUSED_WAITING_TO_RETRY -> "Connection interrupted, retrying…"
        else -> "Download paused…"
    }

    /** True when [actualSize] is far below [expectedSize] and must not be marked ready. */
    fun isIncompleteDownload(actualSize: Long, expectedSize: Long?): Boolean {
        if (expectedSize == null) return false
        return actualSize < expectedSize * 0.5
    }

    fun incompleteMessage(actualSize: Long): String =
        "Downloaded model looks incomplete (${actualSize / 1_000_000} MB). Please retry."
}
