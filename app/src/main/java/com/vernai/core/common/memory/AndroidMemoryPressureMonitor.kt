package com.vernai.core.common.memory

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android system memory pressure monitor implementing [MemoryPressureMonitor].
 * Observes ComponentCallbacks2 memory trim notifications and queries ActivityManager.MemoryInfo.
 */
class AndroidMemoryPressureMonitor(
    private val context: Context
) : MemoryPressureMonitor, ComponentCallbacks2 {

    private val _pressureLevel = MutableStateFlow(MemoryPressureLevel.NORMAL)
    override val pressureLevel: StateFlow<MemoryPressureLevel> = _pressureLevel.asStateFlow()

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    init {
        context.registerComponentCallbacks(this)
    }

    override fun getAvailableMemoryMb(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    override fun isSafeForLlmInference(requiredMemoryMb: Long): Boolean {
        if (_pressureLevel.value == MemoryPressureLevel.CRITICAL) {
            return false
        }
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        if (memInfo.lowMemory) {
            return false
        }
        // Enforce safety headroom of at least 350 MB for Android OS and UI process stability
        val availableMb = memInfo.availMem / (1024 * 1024)
        return availableMb >= (requiredMemoryMb + 350)
    }

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                _pressureLevel.value = MemoryPressureLevel.CRITICAL
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                _pressureLevel.value = MemoryPressureLevel.MODERATE
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // UI backgrounded, keep normal or moderate
            }
            else -> {
                _pressureLevel.value = MemoryPressureLevel.NORMAL
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op
    }

    override fun onLowMemory() {
        _pressureLevel.value = MemoryPressureLevel.CRITICAL
    }

    fun unregister() {
        try {
            context.unregisterComponentCallbacks(this)
        } catch (_: Exception) {
            // Ignore if already unregistered
        }
    }
}
