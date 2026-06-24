package com.myutil.pdfextractor.ui.filelist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    onPdfSelected: (Uri) -> Unit,
    onScanClick: () -> Unit,
    viewModel: FileListViewModel = viewModel()
) {
    val pdfFiles by viewModel.pdfFiles.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.addPdfFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF提取") }
            )
        }
    ) { padding ->
        if (pdfFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EntryCard(
                        icon = "\uD83D\uDCC4",
                        title = "PDF 提取",
                        description = "选择 PDF → 选页 → 导出",
                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }
                    )
                    EntryCard(
                        icon = "\uD83D\uDCF7",
                        title = "文档扫描",
                        description = "拍照 → 矫正 → 导出 PDF",
                        onClick = onScanClick
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                            label = { Text("+ PDF") }
                        )
                        FilterChip(
                            selected = false,
                            onClick = onScanClick,
                            label = { Text("\uD83D\uDCF7 扫描") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "已添加的 PDF",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(pdfFiles) { pdf ->
                    PdfFileItem(
                        info = pdf,
                        onClick = { onPdfSelected(pdf.uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PdfFileItem(
    info: com.myutil.pdfextractor.data.model.PdfInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (info.size > 0) {
                            append(formatFileSize(info.size))
                            append(" · ")
                        }
                        append("${info.pageCount} 页")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val df = DecimalFormat("#.#")
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${df.format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${df.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
