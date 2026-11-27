package com.messagate.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.messagate.app.data.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Export/import entry points backed by the system file picker (SAF). */
class BackupLaunchers(
    val export: () -> Unit,
    val import: () -> Unit
)

@Composable
fun rememberBackupLaunchers(
    prefsManager: PreferencesManager,
    onImported: () -> Unit
): BackupLaunchers {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val payload = prefsManager.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }.orEmpty()
                val result = prefsManager.importBackupJson(raw)
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                if (result.success) onImported()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    return remember(exportLauncher, importLauncher) {
        BackupLaunchers(
            export = {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportLauncher.launch("messagate-backup-$stamp.json")
            },
            import = {
                importLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream")
                )
            }
        )
    }
}
