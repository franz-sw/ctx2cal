package de.franzsw.ctx2cal.core.data.llm

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.StatFs
import de.franzsw.ctx2cal.core.domain.model.DeviceCapabilities
import de.franzsw.ctx2cal.core.domain.ports.DeviceCapabilitiesProvider
import de.franzsw.ctx2cal.core.domain.repository.SettingsRepository
import java.io.File

/**
 * Probes Android hardware capabilities (ABI, RAM, internal/external free storage,
 * SoC/board/SDK, Vulkan, and GLES renderer string) plus cached [SettingsRepository.isGpuUnsupported].
 */
class AndroidDeviceCapabilitiesProvider(
    private val context: Context,
    private val settings: SettingsRepository,
) : DeviceCapabilitiesProvider {

    private val cachedGpuRenderer: String by lazy { queryGlesRendererSafely() }

    private val cachedHasVulkan: Boolean by lazy {
        runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        }.getOrDefault(false)
    }

    private val cachedTotalRamMb: Long by lazy {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024L * 1024L)
        }.getOrDefault(0L)
    }

    override fun getCapabilities(): DeviceCapabilities {
        val internalFree = availableBytes(context.filesDir)
        val externalFree = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { availableBytes(it) }
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else ""

        return DeviceCapabilities(
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            totalRamMb = cachedTotalRamMb,
            internalFreeBytes = internalFree,
            externalFreeBytes = externalFree,
            sdkInt = Build.VERSION.SDK_INT,
            socModel = socModel,
            hardware = Build.HARDWARE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            gpuRenderer = cachedGpuRenderer,
            hasVulkan = cachedHasVulkan,
            gpuUnsupportedCached = settings.isGpuUnsupported(),
        )
    }

    private fun availableBytes(dir: File): Long = runCatching {
        if (!dir.exists()) dir.mkdirs()
        StatFs(dir.absolutePath).availableBytes
    }.getOrDefault(0L)

    private fun queryGlesRendererSafely(): String = runCatching {
        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return@runCatching ""
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return@runCatching ""

        val prevContext = EGL14.eglGetCurrentContext()
        val prevDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val prevRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
            return@runCatching ""
        }
        val config = configs[0] ?: return@runCatching ""

        val pbufferAttribs = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE,
        )
        val surface: EGLSurface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
        if (surface == EGL14.EGL_NO_SURFACE) return@runCatching ""

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val eglContext: EGLContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroySurface(display, surface)
            return@runCatching ""
        }

        try {
            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return@runCatching ""
            GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        } finally {
            // Restore previous surface/context without terminating the shared default EGL display.
            EGL14.eglMakeCurrent(display, prevDraw, prevRead, prevContext)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
        }
    }.getOrDefault("")
}
