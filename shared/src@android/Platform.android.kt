package com.example.ctx2cal

import android.os.Build
import java.time.LocalDate
import java.util.TimeZone

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun todayIso(): String = LocalDate.now().toString()

actual fun deviceTimeZoneId(): String = TimeZone.getDefault().id
