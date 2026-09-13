package com.vernai.document.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.vernai.core.common.result.VernAiResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages document persistence, Android Storage Access Framework (SAF) integration,
 * and secure Android Share Intents via [FileProvider].
 */
class DocumentExportManager(
    private val documentExporter: DocumentExporter = LocalDocumentExporter()
) {

    /**
     * Saves a local file to an Android Storage Access Framework (SAF) destination [Uri].
     * Compatible with Android Scoped Storage (API 29+) without requiring legacy storage permissions.
     */
    fun saveFileToSafUri(
        context: Context,
        sourceFile: File,
        destinationUri: Uri
    ): VernAiResult<Long> {
        return runCatching {
            val contentResolver = context.contentResolver
            var bytesWritten = 0L

            contentResolver.openOutputStream(destinationUri, "wt")?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    bytesWritten = copyStream(inputStream, outputStream)
                }
            } ?: throw IllegalStateException("Could not open output stream for URI: $destinationUri")

            VernAiResult.Success(bytesWritten)
        }.getOrElse { e ->
            VernAiResult.Error(e, "ఫైల్ భద్రపరచడం విఫలమైంది (Failed to save file via SAF: ${e.localizedMessage})")
        }
    }

    /**
     * Creates an Android Share [Intent] using [FileProvider] content URIs.
     * Enforces [Intent.FLAG_GRANT_READ_URI_PERMISSION] to allow external apps (WhatsApp, Gmail, Drive)
     * to access the document securely without exposing raw filesystem paths.
     */
    fun createShareIntent(
        context: Context,
        file: File,
        mimeType: String,
        shareTitle: String = "పత్రం పంపండి (Share Document)"
    ): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, shareTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Generates a unique cached file destination for intermediate export before sharing or saving.
     */
    fun createCacheExportFile(context: Context, prefix: String, format: ExportFormat): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanPrefix = prefix.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return File(exportDir, "${cleanPrefix}_$timeStamp.${format.extension}")
    }

    /**
     * Suggests a clean, descriptive filename for SAF file pickers.
     */
    fun suggestFileName(prefix: String, format: ExportFormat): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val cleanPrefix = prefix.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "${cleanPrefix}_$timeStamp.${format.extension}"
    }

    private fun copyStream(input: InputStream, output: OutputStream, bufferSize: Int = 8192): Long {
        var count = 0L
        val buffer = ByteArray(bufferSize)
        var n = input.read(buffer)
        while (n != -1) {
            output.write(buffer, 0, n)
            count += n
            n = input.read(buffer)
        }
        output.flush()
        return count
    }
}
