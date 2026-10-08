package de.goork.songflip.ui.components

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import de.goork.songflip.R
import de.goork.songflip.core.engine.PlaylistExporter
import de.goork.songflip.core.model.PlaylistTrackItem
import java.io.File

object PlaylistShareHelper {

    private const val TAG = "PlaylistShareHelper"

    fun shareM3u8(context: Context, title: String, tracks: List<PlaylistTrackItem>) {
        try {
            val content = PlaylistExporter.exportToM3u8(title, tracks)
            val cleanName = sanitizeFilename(title.ifBlank { "playlist" }) + ".m3u8"
            val file = writeExportFile(context, cleanName, content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/x-mpegurl"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.playlist_export_m3u8)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share M3U8: ${e.message}", e)
            Toast.makeText(context, context.getString(R.string.playlist_error_desc), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareCsv(context: Context, title: String, tracks: List<PlaylistTrackItem>) {
        try {
            val content = PlaylistExporter.exportToCsv(title, tracks)
            val cleanName = sanitizeFilename(title.ifBlank { "playlist" }) + ".csv"
            val file = writeExportFile(context, cleanName, content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.playlist_export_csv)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share CSV: ${e.message}", e)
            Toast.makeText(context, context.getString(R.string.playlist_error_desc), Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeExportFile(context: Context, filename: String, content: String): File {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, filename)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun sanitizeFilename(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(50)
    }
}
