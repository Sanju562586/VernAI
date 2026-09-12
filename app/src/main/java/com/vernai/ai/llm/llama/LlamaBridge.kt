package com.vernai.ai.llm.llama

import android.util.Log

/**
 * Direct JNI bridge interface to the native llama.cpp library.
 * Wraps C++ functions compiled for Android arm64-v8a and x86_64 architectures.
 */
class LlamaBridge {

    var isNativeLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("llama-android")
            isNativeLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            try {
                System.loadLibrary("llama")
                isNativeLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                isNativeLoaded = false
                Log.w("VernAI-LLM", "Native llama.cpp shared object (.so) not found on library path: ${e.message}")
            }
        }
    }

    /**
     * Loads GGUF model and initializes native llama_context pointer.
     * @return 64-bit native pointer to llama_context, or 0L on failure.
     */
    fun loadModel(
        modelPath: String,
        contextLength: Int,
        nThreads: Int,
        nBatch: Int,
        useMmap: Boolean
    ): Long {
        if (!isNativeLoaded) return 0L
        return try {
            nativeLoadModel(modelPath, contextLength, nThreads, nBatch, useMmap)
        } catch (e: Exception) {
            Log.e("VernAI-LLM", "nativeLoadModel invocation failed: ${e.message}")
            0L
        }
    }

    fun tokenize(contextPtr: Long, text: String): IntArray {
        if (!isNativeLoaded || contextPtr == 0L) return IntArray(0)
        return try {
            nativeTokenize(contextPtr, text)
        } catch (e: Exception) {
            IntArray(0)
        }
    }

    fun eval(contextPtr: Long, tokens: IntArray): Int {
        if (!isNativeLoaded || contextPtr == 0L) return -1
        return try {
            nativeEval(contextPtr, tokens)
        } catch (e: Exception) {
            -1
        }
    }

    fun sampleToken(
        contextPtr: Long,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ): Int {
        if (!isNativeLoaded || contextPtr == 0L) return -1
        return try {
            nativeSampleToken(contextPtr, temperature, topP, topK, repeatPenalty)
        } catch (e: Exception) {
            -1
        }
    }

    fun tokenToPiece(contextPtr: Long, token: Int): String {
        if (!isNativeLoaded || contextPtr == 0L) return ""
        return try {
            nativeTokenToPiece(contextPtr, token)
        } catch (e: Exception) {
            ""
        }
    }

    fun freeContext(contextPtr: Long) {
        if (!isNativeLoaded || contextPtr == 0L) return
        try {
            nativeFreeContext(contextPtr)
        } catch (e: Exception) {
            Log.e("VernAI-LLM", "Error freeing native llama context: ${e.message}")
        }
    }

    // Native C++ JNI declarations
    private external fun nativeLoadModel(
        modelPath: String,
        contextLength: Int,
        nThreads: Int,
        nBatch: Int,
        useMmap: Boolean
    ): Long

    private external fun nativeTokenize(contextPtr: Long, text: String): IntArray
    private external fun nativeEval(contextPtr: Long, tokens: IntArray): Int
    private external fun nativeSampleToken(
        contextPtr: Long,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ): Int
    private external fun nativeTokenToPiece(contextPtr: Long, token: Int): String
    private external fun nativeFreeContext(contextPtr: Long)
}
