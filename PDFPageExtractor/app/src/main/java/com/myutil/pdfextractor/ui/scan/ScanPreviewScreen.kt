package com.myutil.pdfextractor.ui.scan

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myutil.pdfextractor.ui.common.ExportResultDialog
import org.opencv.core.Point
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPreviewScreen(
    onBack: () -> Unit,
    viewModel: ScanPreviewViewModel = viewModel()
) {
    val originalBitmap by viewModel.originalBitmap.collectAsState()
    val correctedBitmap by viewModel.correctedBitmap.collectAsState()
    val cornerPoints by viewModel.cornerPoints.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val shareUri by viewModel.shareUri.collectAsState()

    val context = LocalContext.current

    var showSourcePicker by remember { mutableStateOf(true) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            showSourcePicker = false
            viewModel.loadImage(it)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            showSourcePicker = false
            viewModel.loadImage(cameraUri!!)
        }
    }

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("选择图片来源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                            cameraUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            cameraLauncher.launch(cameraUri!!)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("拍照", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("从相册选择", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onBack) { Text("取消") }
            }
        )
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri ->
        outputUri?.let { viewModel.saveToPermanentLocation(it) }
    }

    val isExportSuccess by viewModel.isExportSuccess.collectAsState()

    exportResult?.let { message ->
        val showShareSave = shareUri != null && isExportSuccess
        ExportResultDialog(
            message = message,
            onDismiss = { viewModel.clearExportResult() },
            onShare = if (showShareSave && shareUri != null) {
                {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享到"))
                }
            } else null,
            onSave = if (showShareSave) {
                { saveLauncher.launch("scanned_document.pdf") }
            } else null
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文档扫描") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.autoCorrect() },
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing
                        ) {
                            Text("自动矫正")
                        }
                        OutlinedButton(
                            onClick = { viewModel.toggleOriginal() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (viewModel.showOriginal) "查看矫正" else "原图对比")
                        }
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
                    Text("处理中...", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val displayBitmap = when {
                    viewModel.showOriginal -> originalBitmap
                    else -> correctedBitmap ?: originalBitmap
                }
                displayBitmap?.let { bitmap ->
                    val imageWidth = bitmap.width.toFloat()
                    val imageHeight = bitmap.height.toFloat()
                    var containerSize by remember { mutableStateOf(Size.Zero) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .onSizeChanged { containerSize = it.toSize() },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = if (viewModel.showOriginal) "原图" else "矫正后",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )

                        if (!viewModel.showOriginal && cornerPoints.isNotEmpty()) {
                            val containerWidth = containerSize.width
                            val containerHeight = containerSize.height

                            if (containerWidth > 0 && containerHeight > 0) {
                                val imageAspect = imageWidth / imageHeight
                                val containerAspect = containerWidth / containerHeight

                                val displayWidth: Float
                                val displayHeight: Float
                                if (imageAspect > containerAspect) {
                                    displayWidth = containerWidth
                                    displayHeight = containerWidth / imageAspect
                                } else {
                                    displayHeight = containerHeight
                                    displayWidth = containerHeight * imageAspect
                                }

                                val offsetX = (containerWidth - displayWidth) / 2f
                                val offsetY = (containerHeight - displayHeight) / 2f
                                val scale = imageWidth / displayWidth

                                cornerPoints.forEachIndexed { index, point ->
                                    val currentPoint by rememberUpdatedState(point)

                                    val displayPointX = offsetX + (point.x / imageWidth) * displayWidth
                                    val displayPointY = offsetY + (point.y / imageHeight) * displayHeight

                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset(displayPointX.roundToInt(), displayPointY.roundToInt()) }
                                            .size(24.dp)
                                            .pointerInput(index) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val p = currentPoint
                                                    viewModel.updateCorner(
                                                        index,
                                                        (p.x.toFloat() + dragAmount.x * scale).coerceIn(0f, imageWidth),
                                                        (p.y.toFloat() + dragAmount.y * scale).coerceIn(0f, imageHeight)
                                                    )
                                                }
                                            }
                                            .clip(CircleShape)
                                            .background(Color(0xFF6366F1))
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                } ?: Box(contentAlignment = Alignment.Center) {
                    Text("无法加载图片", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
