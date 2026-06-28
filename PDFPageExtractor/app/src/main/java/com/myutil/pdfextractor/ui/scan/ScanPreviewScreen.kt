package com.myutil.pdfextractor.ui.scan

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myutil.pdfextractor.ui.common.ExportResultDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPreviewScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    viewModel: ScanPreviewViewModel = viewModel()
) {
    val correctedBitmap by viewModel.correctedBitmap.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val shareUri by viewModel.shareUri.collectAsState()
    val context = LocalContext.current

    // 首次进入自动加载图片
    LaunchedEffect(imageUri) {
        viewModel.loadImage(imageUri)
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri ->
        outputUri?.let { viewModel.saveToPermanentLocation(it) }
    }

    val isExportSuccess by viewModel.isExportSuccess.collectAsState()

    exportResult?.let {
        ExportResultDialog(
            onDismiss = { viewModel.clearExportResult() },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享到"))
            },
            onSave = { saveLauncher.launch("scanned_document.pdf") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("文档扫描", fontWeight = FontWeight.SemiBold)
                        Text(
                            "拍照或相册 → 打印 PDF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = viewModel.removeWritingEnabled,
                            onClick = { viewModel.toggleRemoveWriting() },
                            label = { Text("去笔迹") },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { viewModel.exportToCache() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = correctedBitmap != null && !isProcessing
                    ) {
                        Text("导出为 PDF")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("处理中…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                correctedBitmap?.let { bitmap ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "处理后",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                } ?: Box(contentAlignment = Alignment.Center) {
                    Text("无法加载图片", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
