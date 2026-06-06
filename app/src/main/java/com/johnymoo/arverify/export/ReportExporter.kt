package com.johnymoo.arverify.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.johnymoo.arverify.model.CapabilityReport
import java.io.File

/** Writes the report to the app's external files dir and shares files via the system chooser. */
class ReportExporter(private val context: Context) {

    fun exportsDir(): File =
        File(context.getExternalFilesDir(null), "exports").apply { if (!exists()) mkdirs() }

    fun writeReport(report: CapabilityReport): List<File> {
        val dir = exportsDir()
        val json = File(dir, "compat_report.json").apply { writeText(report.toJson()) }
        val txt = File(dir, "compat_report.txt").apply { writeText(report.toReadableText()) }
        return listOf(json, txt)
    }

    fun share(files: List<File>, title: String) {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
