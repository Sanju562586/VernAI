package com.vernai.document.export

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.vernai.core.common.result.VernAiResult
import java.io.File

/**
 * Capability descriptor for the host device's Office Kit environment.
 */
data class OfficeKitCapabilities(
    val hasDirectOfficeViewer: Boolean,
    val hasEasySharePcTransfer: Boolean,
    val isVivoOrIqooDevice: Boolean
)

/**
 * Clean, standard bridge for interoperating with iQOO / Vivo Office Kit and cross-device workflows.
 *
 * Strict Integration Guidelines:
 * 1. Zero proprietary reverse-engineering: Uses official Android Intent contracts.
 * 2. Device Agnostic: Dynamically detects iQOO/vivo packages; safely falls back to standard
 *    Android intent choosers and Storage Access Framework on other OEM devices.
 * 3. Offline Perimeter: Operates 100% on-device without cloud dependencies.
 */
class IqooOfficeKitBridge(
    private val exportManager: DocumentExportManager = DocumentExportManager()
) {

    companion object {
        const val PKG_VIVO_OFFICE = "com.vivo.office"
        const val PKG_VIVO_EASYSHARE = "com.vivo.easyshare"
        const val PKG_VIVO_PC_SUITE = "com.vivo.assistant"
        const val PKG_WPS_VIVO_OEM = "cn.wps.moffice_eng"

        val OFFICE_KIT_PACKAGES = listOf(
            PKG_VIVO_OFFICE,
            PKG_VIVO_EASYSHARE,
            PKG_VIVO_PC_SUITE,
            PKG_WPS_VIVO_OEM
        )
    }

    /**
     * Checks if the device has an accessible iQOO / vivo Office Kit component installed.
     */
    fun isOfficeKitAvailable(context: Context): Boolean {
        val pm = context.packageManager
        return OFFICE_KIT_PACKAGES.any { pkg -> isPackageInstalled(pm, pkg) }
    }

    /**
     * Inspects which specific Office Kit capability is present.
     */
    fun getOfficeKitCapabilities(context: Context): OfficeKitCapabilities {
        val pm = context.packageManager
        return OfficeKitCapabilities(
            hasDirectOfficeViewer = isPackageInstalled(pm, PKG_VIVO_OFFICE) || isPackageInstalled(pm, PKG_WPS_VIVO_OEM),
            hasEasySharePcTransfer = isPackageInstalled(pm, PKG_VIVO_EASYSHARE),
            isVivoOrIqooDevice = Build.MANUFACTURER.contains("vivo", ignoreCase = true) ||
                    Build.MANUFACTURER.contains("iQOO", ignoreCase = true) ||
                    Build.BRAND.contains("vivo", ignoreCase = true) ||
                    Build.BRAND.contains("iQOO", ignoreCase = true)
        )
    }

    /**
     * Opens an exported document in iQOO Office Kit or standard system viewer.
     */
    fun openDocument(
        context: Context,
        file: File,
        mimeType: String
    ): VernAiResult<Intent> {
        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pm = context.packageManager
            val targetPkg = when {
                isPackageInstalled(pm, PKG_VIVO_OFFICE) -> PKG_VIVO_OFFICE
                isPackageInstalled(pm, PKG_WPS_VIVO_OEM) -> PKG_WPS_VIVO_OEM
                else -> null
            }

            if (targetPkg != null) {
                viewIntent.setPackage(targetPkg)
            }

            val finalIntent = if (targetPkg != null && isIntentResolvable(pm, viewIntent)) {
                viewIntent
            } else {
                Intent.createChooser(viewIntent, "పత్రం తెరవండి (Open Document)").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            VernAiResult.Success(finalIntent)
        }.getOrElse { e ->
            VernAiResult.Error(e, "పత్రం తెరవడం విఫలమైంది (Failed to open document: ${e.localizedMessage})")
        }
    }

    /**
     * Transfers an exported document to PC via iQOO Office Kit / EasyShare workflow,
     * or standard Android Share Sheet if on another device.
     */
    fun createTransferIntent(
        context: Context,
        file: File,
        mimeType: String,
        title: String = "iQOO Office Kit / PC బదిలీ"
    ): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)
        val pm = context.packageManager

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return if (isPackageInstalled(pm, PKG_VIVO_EASYSHARE)) {
            val easyShareIntent = Intent(sendIntent).setPackage(PKG_VIVO_EASYSHARE)
            if (isIntentResolvable(pm, easyShareIntent)) {
                easyShareIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            } else {
                exportManager.createShareIntent(context, file, mimeType, title)
            }
        } else {
            exportManager.createShareIntent(context, file, mimeType, title)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isIntentResolvable(pm: PackageManager, intent: Intent): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L)).isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0).isNotEmpty()
        }
    }
}
