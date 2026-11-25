package com.example.ctx2cal

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformAndroidTest {
    @Test
    fun `platform name is reported`() {
        assertTrue(getPlatform().name.isNotBlank())
    }
}
