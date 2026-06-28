package com.myutil.pdfextractor.ui.collage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CollageScreen(
    onBack: () -> Unit,
    viewModel: CollageViewModel = viewModel()
) {
    val selectedImages by viewModel.selectedImages.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val context = LocalContext.current
    var showFormatDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
            viewModel.refreshPreview(context)
        }
    }

    LaunchedEffect(Unit) {
        if (CollagePendingUris.value.isNotEmpty()) {
            viewModel.setImages(context, CollagePendingUris.value)
            CollagePendingUris.value = emptyList()
        }
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            containerColor = Color.White,
            title = { Text("选择导出格式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showFormatDialog = false
                            viewModel.exportAsImage(context) { err ->
                                if (err != null) errorMessage = err
                                else showResultDialog = true
                            }
                        },
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存为图片", modifier = Modifier.padding(16.dp))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showFormatDialog = false
                            viewModel.exportAsPdf(context) { err ->
                                if (err != null) errorMessage = err
                                else showResultDialog = true
                            }
                        },
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存为PDF", modifier = Modifier.padding(16.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFormatDialog = false }) { Text("取消") }
            }
        )
    }

    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("导出完成") },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "文件已保存到本地",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    TextButton(
                        onClick = {
                            showResultDialog = false
                            viewModel.shareExportedFile(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("分享")
                    }
                }
            },
            confirmButton = {}
        )
    }

    errorMessage?.let {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("导出失败") },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("确定") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, "取消", tint = Color.Black, modifier = Modifier.size(28.dp))
                }
                Row {
                    if (selectedImages.isNotEmpty()) {
                        TextButton(onClick = { showFormatDialog = true }) {
                            Text("保存", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImages.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color.Black.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Text("选择图片进行垂直拼接", style = MaterialTheme.typography.titleMedium, color = Color.Black.copy(alpha = 0.5f))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                            Text("选择图片")
                        }
                    }
                } else {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "拼接预览",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = Color(0xFFFFD700))
                    }
                }
            }
        }

        if (isExporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFFD700))
                    Spacer(Modifier.height(16.dp))
                    Text("正在导出...", color = Color.White)
                }
            }
        }
    }
}


