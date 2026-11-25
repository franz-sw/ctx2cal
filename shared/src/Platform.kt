package com.example.ctx2cal

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** Today's date as ISO-8601 (`YYYY-MM-DD`) in the device timezone, for prompt injection. */
expect fun todayIso(): String

/** Device timezone id (e.g. `Europe/Berlin`), for prompt injection. */
expect fun deviceTimeZoneId(): String
