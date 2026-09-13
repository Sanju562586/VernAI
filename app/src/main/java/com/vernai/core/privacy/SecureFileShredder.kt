package com.vernai.core.privacy

import com.vernai.core.common.result.VernAiResult
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Secure File Shredder for VernAI.
 * Overwrites sensitive files (voice buffers, draft letters, exported files)
 * with cryptographic patterns and zeros before deletion, preventing
 * flash memory forensic recovery.
 */
object SecureFileShredder {

    private val secureRandom = SecureRandom()
    private const val BUFFER_SIZE = 4096

    /**
     * Securely shreds a single file by overwriting its contents with zeros,
     * flushing the physical flash storage buffer, truncating the file to 0 bytes,
     * and deleting the file entry from the filesystem.
     *
     * @param file The file to securely shred.
     * @param passes The number of overwrite passes (default: 2 - one random pass, one zero pass).
     * @return VernAiResult indicating success or descriptive bilingual error.
     */
    fun shredFile(file: File, passes: Int = 2): VernAiResult<Unit> {
        if (!file.exists()) {
            return VernAiResult.Success(Unit)
        }

        if (!file.isFile) {
            return VernAiResult.Error(
                IllegalArgumentException("Path is not a regular file: ${file.absolutePath}"),
                "నిర్దేశించిన మార్గం సాధారణ ఫైల్ కాదు (Target path is not a file: ${file.name})"
            )
        }

        return try {
            val length = file.length()
            if (length > 0) {
                // Ensure write permissions
                if (!file.canWrite()) {
                    file.setWritable(true)
                }

                RandomAccessFile(file, "rws").use { raf ->
                    val buffer = ByteArray(BUFFER_SIZE)

                    for (pass in 0 until passes) {
                        raf.seek(0)
                        var remaining = length

                        val isFinalPass = pass == passes - 1
                        while (remaining > 0) {
                            val chunkSize = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
                            if (isFinalPass) {
                                // Final pass: all zeros
                                buffer.fill(0.toByte(), 0, chunkSize)
                            } else {
                                // Intermediate pass: cryptographically secure pseudorandom bytes
                                secureRandom.nextBytes(buffer)
                            }
                            raf.write(buffer, 0, chunkSize)
                            remaining -= chunkSize
                        }
                        raf.fd.sync()
                    }

                    // Truncate file size to 0
                    raf.setLength(0)
                    raf.fd.sync()
                }
            }

            val deleted = file.delete()
            if (deleted || !file.exists()) {
                VernAiResult.Success(Unit)
            } else {
                VernAiResult.Error(
                    IllegalStateException("Failed to delete file after overwrite: ${file.name}"),
                    "ఫైల్ పూర్తిగా తొలగించబడలేదు (Could not unlink shredded file: ${file.name})"
                )
            }
        } catch (e: Exception) {
            VernAiResult.Error(
                e,
                "ఫైల్ సురక్షిత తొలగింపు విఫలమైంది: ${file.name} (${e.localizedMessage})"
            )
        }
    }

    /**
     * Recursively shreds all files inside a directory and deletes the directory itself.
     *
     * @param directory The directory containing sensitive user files to shred.
     * @return VernAiResult indicating success or descriptive bilingual error.
     */
    fun shredDirectory(directory: File): VernAiResult<Unit> {
        if (!directory.exists()) {
            return VernAiResult.Success(Unit)
        }

        if (!directory.isDirectory) {
            return shredFile(directory)
        }

        return try {
            val children = directory.listFiles()
            if (children != null) {
                for (child in children) {
                    if (child.isDirectory) {
                        val subResult = shredDirectory(child)
                        if (subResult is VernAiResult.Error) {
                            return subResult
                        }
                    } else {
                        val fileResult = shredFile(child)
                        if (fileResult is VernAiResult.Error) {
                            return fileResult
                        }
                    }
                }
            }

            val dirDeleted = directory.delete()
            if (dirDeleted || !directory.exists()) {
                VernAiResult.Success(Unit)
            } else {
                VernAiResult.Error(
                    IllegalStateException("Failed to delete directory: ${directory.name}"),
                    "డైరెక్టరీ తొలగింపు విఫలమైంది (Could not delete directory: ${directory.name})"
                )
            }
        } catch (e: Exception) {
            VernAiResult.Error(
                e,
                "డైరెక్టరీ సురక్షిత తొలగింపు విఫలమైంది: ${directory.name} (${e.localizedMessage})"
            )
        }
    }
}
