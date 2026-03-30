package com.webwrap.app.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * OfflinePageViewer — Dialog that lists saved .mht web archives.
 * User can open them in a new browser tab or delete them.
 * Files stored in Downloads/WebWrap_Offline/ directory.
 */
@Composable
fun OfflinePageViewer(
    onDismiss: () -> Unit,
    onOpenFile: (String) -> Unit,
    onDeleteFile: (File) -> Unit = {}
) {
    // Load saved files from Downloads/WebWrap_Offline/
    var files by remember { mutableStateOf(loadOfflineFiles()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SaveAlt, null, tint = Color(0xFF66BB6A))
                Spacer(Modifier.width(8.dp))
                Text("Saved Pages", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (files.isEmpty()) {
                Text(
                    "No saved pages yet.\nUse 'Save Offline' to download pages.",
                    color = Color(0xFF888888), fontSize = 14.sp
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(files) { file ->
                        OfflineFileItem(
                            file = file,
                            onOpen = {
                                onOpenFile("file://${file.absolutePath}")
                                onDismiss()
                            },
                            onDelete = {
                                file.delete()
                                files = loadOfflineFiles()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF4FC3F7))
            }
        }
    )
}

/** Single offline file row with open and delete actions */
@Composable
private fun OfflineFileItem(file: File, onOpen: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF252538),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Article, null,
                tint = Color(0xFF66BB6A), modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.nameWithoutExtension.replace("_", " "),
                    color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${dateFormat.format(Date(file.lastModified()))} • ${formatFileSize(file.length())}",
                    color = Color(0xFF888888), fontSize = 11.sp
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF1E1E2E),
            title = { Text("Delete?", color = Color.White) },
            text = { Text("Delete '${file.nameWithoutExtension}'?", color = Color(0xFF888888)) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        )
    }
}

/** Load .mht files from WebWrap_Offline directory */
private fun loadOfflineFiles(): List<File> {
    val dir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "WebWrap_Offline"
    )
    return if (dir.exists()) {
        dir.listFiles()?.filter { it.extension == "mht" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    } else emptyList()
}

/** Format file size to human readable string */
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
}