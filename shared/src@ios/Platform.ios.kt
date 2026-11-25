package de.franzsw.ctx2cal

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun todayIso(): String {
    val cal = platform.Foundation.NSDate()
    val formatter = platform.Foundation.NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.locale = platform.Foundation.NSLocale(localeWithLanguageIdentifier = "en_US_POSIX")
    return formatter.stringFromDate(cal)
}

actual fun deviceTimeZoneId(): String =
    platform.Foundation.NSTimeZone.localTimeZone.name ?: "Unknown"
