package com.vernai.document.export

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Native offline printing manager for Gram Panchayat and village administrative offices.
 * Connects directly to local thermal, inkjet, laser, or Wi-Fi Direct / USB-OTG printers
 * using the Android Print Spooler with zero cloud or internet dependencies.
 */
object PanchayatPrintManager {

    /**
     * Sends a local PDF file to the Android Print Spooler.
     *
     * @param context Host Android Context
     * @param pdfFile The generated local PDF file to print
     * @param jobName Title displayed in the print spooler queue
     * @return true if printing was dispatched, false otherwise
     */
    fun printPdf(
        context: Context,
        pdfFile: File,
        jobName: String = "VernAI_Panchayat_Letter"
    ): Boolean {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return false
        }

        return try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return false

            val adapter = PdfPrintDocumentAdapter(pdfFile)
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("vernai_300dpi", "A4 Document", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print(jobName, adapter, printAttributes)
            true
        } catch (e: Throwable) {
            false
        }
    }
}

/**
 * Concrete [PrintDocumentAdapter] that streams a pre-generated PDF file
 * directly into the print spooler's file descriptor without re-rendering.
 */
class PdfPrintDocumentAdapter(private val pdfFile: File) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(pdfFile.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("Null destination file descriptor")
            return
        }

        var input: FileInputStream? = null
        var output: FileOutputStream? = null

        try {
            input = FileInputStream(pdfFile)
            output = FileOutputStream(destination.fileDescriptor)

            val buffer = ByteArray(16384)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } >= 0) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                output.write(buffer, 0, bytesRead)
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        } finally {
            try {
                input?.close()
                output?.close()
            } catch (_: Exception) {
            }
        }
    }
}
