package com.vernai.ai.llm.benchmark

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Execution backends for on-device LLM inference on Qualcomm Snapdragon and ARM Android devices.
 */
enum class ExecutionBackend(
    val displayName: String,
    val description: String,
    val isProductionReady: Boolean
) {
    /**
     * Highly optimized CPU SIMD execution utilizing ARM NEON vector extensions
     * (fp16 arithmetic + dot-product int8 instructions) on Snapdragon Kryo performance cores.
     */
    CPU_NEON(
        displayName = "CPU (ARM NEON SIMD)",
        description = "Pinned to Qualcomm Kryo Gold performance cores. 100% stable, zero driver fragmentation, universal ARM64 compatibility.",
        isProductionReady = true
    ),

    /**
     * Mobile GPU compute offload via Vulkan 1.3 compute shaders targeting Qualcomm Adreno 7xx/6xx GPUs.
     * Subject to shader warmup compilation overhead and mobile unified memory bandwidth contention.
     */
    GPU_VULKAN(
        displayName = "GPU (Adreno Vulkan)",
        description = "Matrix multiplication offload to Qualcomm Adreno GPU shaders. Falls back automatically to CPU NEON if driver initialization fails.",
        isProductionReady = false
    ),

    /**
     * Qualcomm Hexagon NPU execution via Qualcomm AI Engine Direct (QNN / HTP).
     * NOTE: GGUF models are NOT natively compatible with Hexagon NPU. NPU execution requires
     * ahead-of-time graph serialization for specific HTP hardware revisions (HTP v69/v73/v75).
     */
    NPU_QUALCOMM_QNN(
        displayName = "NPU (Qualcomm Hexagon HTP via QNN)",
        description = "Qualcomm Hexagon Tensor Processor. Requires pre-compiled proprietary QNN serialized context binaries. Incompatible with direct GGUF format.",
        isProductionReady = false
    );

    companion object {
        /**
         * Inspects device hardware and runtime capabilities to determine available execution backends.
         */
        fun detectBackendSupport(context: Context? = null): Map<ExecutionBackend, BackendSupportInfo> {
            val results = mutableMapOf<ExecutionBackend, BackendSupportInfo>()

            // 1. CPU NEON - Always available on 64-bit ARM Android
            val isArm64 = Build.SUPPORTED_ABIS?.any { it.contains("arm64") } ?: true
            results[CPU_NEON] = BackendSupportInfo(
                backend = CPU_NEON,
                isSupported = isArm64,
                details = "Supported: ARM64-v8a NEON with FP16/DotProd vectorization (${Runtime.getRuntime().availableProcessors()} logical cores)."
            )

            // 2. GPU Vulkan - Check PackageManager for FEATURE_VULKAN_HARDWARE_LEVEL
            val hasVulkan = context?.packageManager?.hasSystemFeature("android.hardware.vulkan.version") ?: true
            val hasVulkanCompute = context?.packageManager?.hasSystemFeature("android.hardware.vulkan.compute") ?: true
            val hardwareName = Build.HARDWARE?.lowercase() ?: "snapdragon"
            val isSnapdragon = hardwareName.contains("qcom") || hardwareName.contains("qualcomm") || hardwareName.contains("taro") || hardwareName.contains("kalama") || hardwareName.contains("pineapple") || hardwareName.contains("snapdragon")

            results[GPU_VULKAN] = BackendSupportInfo(
                backend = GPU_VULKAN,
                isSupported = hasVulkan && hasVulkanCompute,
                details = if (hasVulkan) {
                    "Vulkan 1.3 compute available on ${Build.HARDWARE ?: "Adreno GPU"}. Experimental Adreno shader acceleration with automatic CPU fallback."
                } else {
                    "Vulkan compute feature not reported by Android Package Manager."
                }
            )

            // 3. NPU Qualcomm QNN - Check for Qualcomm SoC and proprietary QNN libraries
            results[NPU_QUALCOMM_QNN] = BackendSupportInfo(
                backend = NPU_QUALCOMM_QNN,
                isSupported = false, // GGUF runtime cannot directly execute on Hexagon NPU
                details = buildString {
                    append("Unsupported for GGUF: Qualcomm Hexagon HTP requires proprietary QNN serialized graph compilation ")
                    append("(HTP v69/v73/v75 target locked) rather than runtime GGUF interpreter. ")
                    if (isSnapdragon) {
                        append("Qualcomm SoC detected (${Build.HARDWARE ?: "Qualcomm"}), but GGUF format requires CPU/GPU compute.")
                    } else {
                        append("Non-Qualcomm SoC detected.")
                    }
                }
            )

            return results
        }
    }
}

/**
 * Diagnostic data describing backend availability and runtime constraints.
 */
data class BackendSupportInfo(
    val backend: ExecutionBackend,
    val isSupported: Boolean,
    val details: String
)
