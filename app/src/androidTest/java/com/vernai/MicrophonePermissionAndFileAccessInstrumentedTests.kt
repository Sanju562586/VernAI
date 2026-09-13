package com.vernai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.vernai.ai.asr.audio.AudioRecordManager
import com.vernai.core.common.result.VernAiResult
import com.vernai.core.privacy.SecureFileShredder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Android Instrumented Tests executing on live device / emulator.
 * Verifies runtime microphone permission handling, hardware AudioRecord buffer configuration,
 * Android FileProvider scoped URI generation, and private internal storage access.
 */
@RunWith(AndroidJUnit4::class)
class MicrophonePermissionAndFileAccessInstrumentedTests {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val context: Context = ApplicationProvider.getApplicationContext()

    // =========================================================================
    // 1. Microphone Permission & Hardware AudioRecord Tests
    // =========================================================================

    @Test
    fun instrumented_hasMicrophonePermission_whenGrantedByRule() {
        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        assertEquals("RECORD_AUDIO permission must be GRANTED", PackageManager.PERMISSION_GRANTED, permission)

        val audioManager = AudioRecordManager(context)
        assertTrue("AudioRecordManager must report true when permission is held", audioManager.hasRecordPermission())
    }

    @Test
    fun instrumented_audioRecordMinBufferSize_isSupportedByHardware() {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        assertTrue(
            "Hardware must return valid buffer size (> 0), got: $minBufferSize",
            minBufferSize > 0 && minBufferSize != AudioRecord.ERROR && minBufferSize != AudioRecord.ERROR_BAD_VALUE
        )
    }

    // =========================================================================
    // 2. App-Private File Access & Sandbox Tests
    // =========================================================================

    @Test
    fun instrumented_appPrivateStorage_isIsolatedAndWritable() {
        val filesDir = context.filesDir
        assertNotNull("FilesDir must not be null", filesDir)
        assertTrue("FilesDir must exist", filesDir.exists())
        assertTrue("FilesDir must be a directory", filesDir.isDirectory)
        assertTrue("FilesDir must be writable", filesDir.canWrite())

        val testFile = File(filesDir, "test_sandbox_doc.txt")
        testFile.writeText("VernAI On-Device Private Data", StandardCharsets.UTF_8)

        assertTrue("File must exist in app private sandbox", testFile.exists())
        assertEquals("VernAI On-Device Private Data", testFile.readText())

        // Cleanup
        testFile.delete()
        assertFalse(testFile.exists())
    }

    @Test
    fun instrumented_fileProvider_generatesValidContentUriForExports() {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val testExportFile = File(exportDir, "instrumented_letter.pdf")
        testExportFile.writeText("%PDF-1.4\nVernAI Formal Letter PDF\n%%EOF", StandardCharsets.UTF_8)
        assertTrue(testExportFile.exists())

        // Generate content URI via FileProvider
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, testExportFile)

        assertNotNull("Generated content URI must not be null", contentUri)
        assertEquals("Content URI scheme must be 'content'", "content", contentUri.scheme)
        assertEquals("Authority must match app fileprovider", authority, contentUri.authority)

        // Verify that ContentResolver can open and read the stream from the generated URI
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(contentUri)
        assertNotNull("ContentResolver must be able to open stream for URI", inputStream)

        val streamContent = inputStream!!.bufferedReader().readText()
        assertTrue("Stream must contain exported file bytes", streamContent.contains("%PDF-1.4"))
        inputStream.close()

        // Clean up
        testExportFile.delete()
    }

    @Test
    fun instrumented_secureFileShredder_shredsOnLiveAndroidFileSystem() {
        val sensitiveVoiceFile = File(context.cacheDir, "instrumented_audio_buffer.pcm")
        sensitiveVoiceFile.writeText("SENSITIVE_PCM_LIVE_VOICE_BUFFER", StandardCharsets.UTF_8)
        assertTrue(sensitiveVoiceFile.exists())

        val result = SecureFileShredder.shredFile(sensitiveVoiceFile)

        assertTrue("Shredder must return Success on Android filesystem", result is VernAiResult.Success)
        assertFalse("Sensitive voice buffer must be completely unlinked", sensitiveVoiceFile.exists())
    }
}
