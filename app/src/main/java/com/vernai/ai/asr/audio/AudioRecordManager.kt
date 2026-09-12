package com.vernai.ai.asr.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.vernai.core.common.dispatchers.DefaultVernAiDispatchers
import com.vernai.core.common.dispatchers.VernAiDispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Continuous 16kHz PCM audio capture engine.
 * Records audio using Android's AudioRecord API with zero cloud/network telemetry.
 */
class AudioRecordManager(
    private val context: Context,
    private val config: AudioRecordConfig = AudioRecordConfig(),
    private val dispatchers: VernAiDispatchers = DefaultVernAiDispatchers()
) {

    /**
     * Checks whether the application holds the runtime RECORD_AUDIO permission.
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Emits a continuous stream of [AudioFrame] instances captured from the device microphone.
     * Throws [SecurityException] if RECORD_AUDIO permission is missing.
     * Throws [IllegalStateException] if the audio hardware cannot be initialized.
     */
    fun startCaptureStream(): Flow<AudioFrame> = callbackFlow {
        if (!hasRecordPermission()) {
            close(SecurityException("RECORD_AUDIO permission not granted"))
            return@callbackFlow
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.audioFormat
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            close(IllegalStateException("AudioRecord hardware buffer configuration unsupported on this device"))
            return@callbackFlow
        }

        val bufferSize = max(minBufferSize, config.bytesPerChunk * 4)

        // Prefer VOICE_RECOGNITION to engage platform hardware acoustic echo cancellation & noise suppression
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                bufferSize
            )
        } catch (_: SecurityException) {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                bufferSize
            )
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            close(IllegalStateException("AudioRecord initialization failed (state != STATE_INITIALIZED)"))
            return@callbackFlow
        }

        try {
            audioRecord.startRecording()
        } catch (e: Exception) {
            audioRecord.release()
            close(IllegalStateException("Failed to start audio recording: ${e.message}", e))
            return@callbackFlow
        }

        val byteBuffer = ByteArray(config.bytesPerChunk)
        val shortBuffer = ShortArray(config.samplesPerChunk)

        // Non-blocking capture loop running on dedicated ASR dispatcher
        val readerJob = kotlinx.coroutines.CoroutineScope(dispatchers.asrInference).launch {
            try {
                while (isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = audioRecord.read(byteBuffer, 0, config.bytesPerChunk)

                    if (bytesRead > 0) {
                        ByteBuffer.wrap(byteBuffer, 0, bytesRead)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuffer, 0, bytesRead / 2)

                        val numSamples = bytesRead / 2
                        val normalized = FloatArray(numSamples)
                        var sumSquares = 0.0

                        for (i in 0 until numSamples) {
                            val sample = shortBuffer[i] / 32768.0f
                            normalized[i] = sample
                            sumSquares += (sample * sample)
                        }

                        val rms = sqrt(sumSquares / numSamples)
                        // Decibel formula mapped roughly to 0..95 dB SPL range
                        val db = if (rms > 1e-5) {
                            (20.0 * log10(rms) + 90.0).toFloat().coerceIn(0f, 95f)
                        } else {
                            0f
                        }

                        val isSpeech = db >= config.speechSilenceThresholdDb

                        val frame = AudioFrame(
                            pcmBytes = byteBuffer.copyOf(bytesRead),
                            normalizedSamples = normalized,
                            decibels = db,
                            isSpeech = isSpeech,
                            timestampMs = System.currentTimeMillis()
                        )

                        trySend(frame)
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION || bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        break
                    }
                }
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose {
            readerJob.cancel()
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (_: Exception) {
                // Ignore cleanup errors during teardown
            } finally {
                audioRecord.release()
            }
        }
    }.flowOn(dispatchers.asrInference)
}
