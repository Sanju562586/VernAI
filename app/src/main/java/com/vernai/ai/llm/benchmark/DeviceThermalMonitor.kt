package com.vernai.ai.llm.benchmark

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File

/**
 * Diagnostics collector for device battery, thermal status, and hardware telemetry during AI workloads.
 */
class DeviceThermalMonitor(private val context: Context? = null) {

    /**
     * Snapshot of thermal, electrical, and CPU states.
     */
    data class TelemetrySnapshot(
        val batteryTempCelsius: Float,
        val batteryLevelPercent: Int,
        val batteryVoltageMv: Int,
        val thermalStatus: String,
        val isThermalThrottling: Boolean,
        val cpuFrequencySummary: String,
        val memoryFreeMb: Long,
        val timestampMs: Long = System.currentTimeMillis()
    )

    fun captureSnapshot(): TelemetrySnapshot {
        // 1. Battery Telemetry
        val batteryIntent = context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTempCelsius = if (rawTemp > 0) rawTemp / 10.0f else 31.0f // Standard ambient fallback

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 85

        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4200) ?: 4200

        // 2. Android OS Thermal Status (API 29+)
        var thermalStatusStr = "NONE (Nominal)"
        var isThrottling = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context != null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val status = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            thermalStatusStr = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE (Nominal)"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT (Minor warming)"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE (Throttling possible)"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE (Throttling active)"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL (Severe mitigation)"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY (Thermal shutdown risk)"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "UNKNOWN ($status)"
            }
            isThrottling = status >= PowerManager.THERMAL_STATUS_MODERATE
        }

        // 3. CPU Clock Telemetry
        val cpuSummary = readCpuClocks()

        // 4. Free Memory Telemetry
        val runtime = Runtime.getRuntime()
        val freeMemoryMb = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)

        return TelemetrySnapshot(
            batteryTempCelsius = batteryTempCelsius,
            batteryLevelPercent = batteryPct,
            batteryVoltageMv = voltageMv,
            thermalStatus = thermalStatusStr,
            isThermalThrottling = isThrottling,
            cpuFrequencySummary = cpuSummary,
            memoryFreeMb = freeMemoryMb
        )
    }

    private fun readCpuClocks(): String {
        return try {
            val cpu0Cur = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            val cpu4Cur = File("/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq")
            val cpu7Cur = File("/sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq")

            val silverFreq = if (cpu0Cur.exists()) "${cpu0Cur.readText().trim().toInt() / 1000}MHz" else "Silver"
            val goldFreq = if (cpu4Cur.exists()) "${cpu4Cur.readText().trim().toInt() / 1000}MHz" else "Gold"
            val primeFreq = if (cpu7Cur.exists()) "${cpu7Cur.readText().trim().toInt() / 1000}MHz" else "Prime"

            "$silverFreq / $goldFreq / $primeFreq"
        } catch (_: Exception) {
            "${Runtime.getRuntime().availableProcessors()} Cores (Kryo)"
        }
    }
}
