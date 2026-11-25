package com.example.ctx2cal.platform

/**
 * Holds the platform `Context` (as [Any], cast on Android). Set once from
 * `MainActivity` on Android; stays null on iOS.
 * Avoids depending on the `koin-android` artifact for `androidContext()`.
 */
object PlatformContextHolder {
    var value: Any? = null
}
