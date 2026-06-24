# Document Photo Scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add document photo scanning feature — take/pick photo → correct perspective + binarize → export as A4 PDF

**Architecture:** OpenCV handles image processing (edge detection, perspective warp, Otsu binarization). New ScanPreviewScreen + ScanPreviewViewModel host the scanning UI. Exports reuse existing PdfRepository PDF generation and SAF save/share flow.

**Tech Stack:** Kotlin, Jetpack Compose, OpenCV Android 4.5.3, Android PdfDocument

## Global Constraints

- Target SDK 36, min SDK 29
- All image processing must run on-device, no network calls
- Write to `data/` directory for new data-layer files
- Write to `ui/pagegrid/` directory for new screen files (since scan is a peer to pagegrid)
- Use `androidx.core.content.FileProvider` for sharing (already configured)
- Follow existing code style: no comments, concise code
- OpenCV via Maven: `com.quickbirdstudios:opencv:4.5.3.0`

---

### Task 1: Add OpenCV dependency and create ImageProcessor

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/myutil/pdfextractor/data/ImageProcessor.kt`

**Interfaces:**
- Consumes: `android.graphics.Bitmap`
- Produces: `ImageProcessor` class with `autoCorrect(bitmap: Bitmap): AutoCorrectResult?`, `warpPerspective(bitmap: Bitmap, corners: List<org.opencv.core.Point>, outputSize: org.opencv.core.Size): Bitmap`, `binarize(bitmap: Bitmap): Bitmap`

- [ ] **Step 1: Add OpenCV dependency to build.gradle.kts**

Add after the last `implementation` line:
```kotlin
implementation("com.quickbirdstudios:opencv:4.5.3.0")
```

- [ ] **Step 2: Create ImageProcessor.kt**

```kotlin
package com.myutil.pdfextractor.data

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.ArrayList

data class AutoCorrectResult(
    val correctedBitmap: Bitmap,
    val corners: List<org.opencv.core.Point>
)

class ImageProcessor {

    companion object {
        private var initialized = false
        fun ensureInitialized() {
            if (!initialized) {
                OpenCVLoader.initDebug()
                initialized = true
            }
        }
    }

    fun autoCorrect(bitmap: Bitmap): AutoCorrectResult? {
        ensureInitialized()
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 200.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var maxArea = 0.0
        var documentContour: MatOfPoint2f? = null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < maxArea) continue
            val contour2f = MatOfPoint2f(*contour.toArray())
            val approx = MatOfPoint2f()
            val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
            if (approx.toArray().size == 4) {
                maxArea = area
                documentContour = approx
            }
        }

        if (documentContour == null) return null

        val corners = documentContour.toArray().toList()
        val ordered = orderCorners(corners)
        val width = 595
        val height = 842
        val dstPoints = listOf(
            Point(0.0, 0.0),
            Point(width - 1.0, 0.0),
            Point(width - 1.0, height - 1.0),
            Point(0.0, height - 1.0)
        )

        val srcMat = MatOfPoint2f(*ordered.toTypedArray())
        val dstMat = MatOfPoint2f(*dstPoints.toTypedArray())
        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)

        val warped = Mat()
        Imgproc.warpPerspective(src, warped, transform, Size(width.toDouble(), height.toDouble()))

        val binarized = binarize(warped)

        val outBitmap = Bitmap.createBitmap(binarized.cols(), binarized.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(binarized, outBitmap)

        src.release()
        gray.release()
        blurred.release()
        edges.release()
        warped.release()
        binarized.release()

        return AutoCorrectResult(correctedBitmap = outBitmap, corners = ordered)
    }

    fun warpPerspective(bitmap: Bitmap, corners: List<org.opencv.core.Point>, outputSize: org.opencv.core.Size): Bitmap {
        ensureInitialized()
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val ordered = orderCorners(corners)
        val dstPoints = listOf(
            Point(0.0, 0.0),
            Point(outputSize.width - 1.0, 0.0),
            Point(outputSize.width - 1.0, outputSize.height - 1.0),
            Point(0.0, outputSize.height - 1.0)
        )

        val srcMat = MatOfPoint2f(*ordered.toTypedArray())
        val dstMat = MatOfPoint2f(*dstPoints.toTypedArray())
        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)

        val warped = Mat()
        Imgproc.warpPerspective(src, warped, transform, outputSize)

        val out = binarize(warped)

        val outBitmap = Bitmap.createBitmap(out.cols(), out.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(out, outBitmap)

        src.release()
        warped.release()
        out.release()

        return outBitmap
    }

    private fun binarize(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_OTSU)
        val result = Mat()
        Imgproc.cvtColor(binary, result, Imgproc.COLOR_GRAY2RGBA)
        gray.release()
        binary.release()
        return result
    }

    private fun orderCorners(corners: List<org.opencv.core.Point>): List<org.opencv.core.Point> {
        val sorted = corners.sortedBy { it.y }
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.drop(2).sortedByDescending { it.x }
        return listOf(top[0], top[1], bottom[0], bottom[1])
    }
}
```

- [ ] **Step 3: Build and verify**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/myutil/pdfextractor/data/ImageProcessor.kt
git commit -m "feat: add OpenCV dependency and ImageProcessor for document scan"
```

---

### Task 2: Add createPdfFromBitmap to PdfRepository

**Files:**
- Modify: `app/src/main/java/com/myutil/pdfextractor/data/PdfRepository.kt`

**Interfaces:**
- Consumes: `android.graphics.Bitmap`, `java.io.OutputStream`
- Produces: `PdfRepository.createPdfFromBitmap(bitmap: Bitmap, outputStream: OutputStream)`

- [ ] **Step 1: Add createPdfFromBitmap method**

Add after the existing `extractPages` methods:

```kotlin
fun createPdfFromBitmap(bitmap: Bitmap, outputStream: OutputStream) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val scale = minOf(595f / bitmap.width, 842f / bitmap.height)
    val left = (595 - bitmap.width * scale) / 2f
    val top = (842 - bitmap.height * scale) / 2f
    canvas.drawBitmap(bitmap, left, top, null)
    pdfDocument.finishPage(page)
    pdfDocument.writeTo(outputStream)
    pdfDocument.close()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/myutil/pdfextractor/data/PdfRepository.kt
git commit -m "feat: add createPdfFromBitmap to PdfRepository"
```

---

### Task 3: Add Scan route to NavRoutes

**Files:**
- Modify: `app/src/main/java/com/myutil/pdfextractor/navigation/NavRoutes.kt`

- [ ] **Step 1: Add Scan route**

Add after the `PageGrid` object:

```kotlin
data object Scan : NavRoutes("scan")
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/myutil/pdfextractor/navigation/NavRoutes.kt
git commit -m "feat: add Scan navigation route"
```

---

### Task 4: Update FileListScreen with two-card entry

**Files:**
- Modify: `app/src/main/java/com/myutil/pdfextractor/ui/filelist/FileListScreen.kt`

- [ ] **Step 1: Rewrite FileListScreen**

Remove the existing FAB. Replace empty state with two entry cards. For the non-empty state, show PDF list at top + floating action row at bottom.

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/myutil/pdfextractor/ui/filelist/FileListScreen.kt
git commit -m "feat: add two-card entry (PDF/scan) to FileListScreen"
```

---

### Task 5: Create ScanPreviewScreen + ScanPreviewViewModel

**Files:**
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/scan/ScanPreviewViewModel.kt`
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/scan/ScanPreviewScreen.kt`

**Interfaces:**
- Consumes: `ImageProcessor`, `PdfRepository`
- Produces: `ScanPreviewViewModel` with `loadImage(uri: Uri)`, `autoCorrect()`, `updateCorner(index: Int, x: Float, y: Float)`, `exportToCache()`, `saveToPermanentLocation(outputUri: Uri)`, state flows for UI

- [ ] **Step 1: Create ScanPreviewViewModel**

```kotlin
package com.myutil.pdfextractor.ui.scan

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myutil.pdfextractor.data.ImageProcessor
import com.myutil.pdfextractor.data.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Point
import org.opencv.core.Size
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ScanPreviewViewModel(application: Application) : AndroidViewModel(application) {
    private val imageProcessor = ImageProcessor()
    private val repository = PdfRepository(application)
    private var exportedCacheFile: File? = null

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    private val _correctedBitmap = MutableStateFlow<Bitmap?>(null)
    val correctedBitmap: StateFlow<Bitmap?> = _correctedBitmap.asStateFlow()

    private val _cornerPoints = MutableStateFlow<List<Point>>(emptyList())
    val cornerPoints: StateFlow<List<Point>> = _cornerPoints.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _shareUri = MutableStateFlow<Uri?>(null)
    val shareUri: StateFlow<Uri?> = _shareUri.asStateFlow()

    var showOriginal by mutableStateOf(false)
        private set

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                    inputStream?.use { BitmapFactory.decodeStream(it) }
                } ?: return@launch
                _originalBitmap.value = bitmap
                runCorrection(bitmap)
            } catch (e: Exception) {
                _exportResult.value = "加载图片失败: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun autoCorrect() {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val bitmap = _originalBitmap.value ?: return@launch
                runCorrection(bitmap)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun runCorrection(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val result = imageProcessor.autoCorrect(bitmap)
        if (result != null) {
            _correctedBitmap.value = result.correctedBitmap
            _cornerPoints.value = result.corners
        } else {
            _exportResult.value = "未自动检测到文档边缘，请手动调整或选择其他图片"
            _correctedBitmap.value = bitmap
        }
    }

    fun updateCorner(index: Int, x: Float, y: Float) {
        viewModelScope.launch {
            val currentCorners = _cornerPoints.value.toMutableList()
            if (index !in currentCorners.indices) return@launch
            currentCorners[index] = Point(x.toDouble(), y.toDouble())
            _cornerPoints.value = currentCorners

            val bitmap = _originalBitmap.value ?: return@launch
            _isProcessing.value = true
            try {
                val corrected = withContext(Dispatchers.IO) {
                    imageProcessor.warpPerspective(bitmap, currentCorners, Size(595.0, 842.0))
                }
                _correctedBitmap.value = corrected
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun toggleOriginal() {
        showOriginal = !showOriginal
    }

    fun exportToCache() {
        viewModelScope.launch {
            val bitmap = _correctedBitmap.value ?: return@launch
            _isProcessing.value = true
            try {
                val cacheFile = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val file = File(app.cacheDir, "scan_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(file).use { outputStream ->
                        repository.createPdfFromBitmap(bitmap, outputStream)
                    }
                    file
                }
                exportedCacheFile = cacheFile
                val authority = "${getApplication<Application>().packageName}.fileprovider"
                _shareUri.value = FileProvider.getUriForFile(getApplication(), authority, cacheFile)
                _exportResult.value = "导出成功"
            } catch (e: Exception) {
                _exportResult.value = "导出失败: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun saveToPermanentLocation(outputUri: Uri) {
        viewModelScope.launch {
            val cacheFile = exportedCacheFile ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(cacheFile).use { input ->
                        getApplication<Application>().contentResolver.openOutputStream(outputUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                _exportResult.value = "已保存"
            } catch (e: Exception) {
                _exportResult.value = "保存失败: ${e.message}"
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _originalBitmap.value?.recycle()
        _correctedBitmap.value?.recycle()
    }
}
```

- [ ] **Step 2: Create ScanPreviewScreen**

```kotlin
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myutil.pdfextractor.ui.common.ExportResultDialog
import org.opencv.core.Point

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

    exportResult?.let { message ->
        val isExportSuccess = shareUri != null && message == "导出成功"
        ExportResultDialog(
            message = message,
            onDismiss = { viewModel.clearExportResult() },
            onShare = if (isExportSuccess && shareUri != null) {
                {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享到"))
                }
            } else null,
            onSave = if (isExportSuccess) {
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
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
                            val scaleX = 1f / imageWidth
                            val scaleY = 1f / imageHeight
                            cornerPoints.forEachIndexed { index, point ->
                                val xFrac = (point.x / imageWidth)
                                val yFrac = (point.y / imageHeight)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(index) {
                                            detectDragGestures { _, dragAmount ->
                                                val dx = dragAmount.x * scaleX
                                                val dy = dragAmount.y * scaleY
                                                viewModel.updateCorner(
                                                    index,
                                                    (point.x + dx).toFloat().coerceIn(0f, imageWidth),
                                                    (point.y + dy).toFloat().coerceIn(0f, imageHeight)
                                                )
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                translationX = (point.x * scaleX).toFloat()
                                                translationY = (point.y * scaleY).toFloat()
                                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
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
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myutil/pdfextractor/ui/scan/
git commit -m "feat: add ScanPreviewScreen and ScanPreviewViewModel"
```

---

### Task 6: Wire Scan navigation in PDFExtractorApp

**Files:**
- Modify: `app/src/main/java/com/myutil/pdfextractor/PDFExtractorApp.kt`
- Modify: `app/src/main/java/com/myutil/pdfextractor/navigation/NavRoutes.kt`

**Note:** ScanPreviewScreen handles its own image picking (camera/gallery dialog) internally, so the scan route has no URI parameter.

- [ ] **Step 1: Add Scan route to NavRoutes**

Already done in Task 3 — ensure `data object Scan : NavRoutes("scan")` is present.

- [ ] **Step 2: Update PDFExtractorApp**

Add import:
```kotlin
import com.myutil.pdfextractor.ui.scan.ScanPreviewScreen
```

Add scan composable after the PageGrid block:
```kotlin
composable(NavRoutes.Scan.route) {
    ScanPreviewScreen(
        onBack = { navController.popBackStack() }
    )
}
```

Update FileListScreen call to pass `onScanClick`:
```kotlin
FileListScreen(
    onPdfSelected = { uri ->
        navController.navigate(NavRoutes.PageGrid.buildRoute(uri.toString()))
    },
    onScanClick = {
        navController.navigate(NavRoutes.Scan.route)
    }
)
```

Also update FileListScreen import — the old imports are unchanged, just add `onScanClick`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/myutil/pdfextractor/PDFExtractorApp.kt
git commit -m "feat: wire scan navigation in PDFExtractorApp"
```

---

### Task 7: Build, install and verify on device

**Files:**
- No code changes

- [ ] **Step 1: Build and install**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/Users/denghh/Library/Android/sdk"
./gradlew assembleDebug
$ANDROID_HOME/platform-tools/adb -s 192.168.31.201:33469 install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: BUILD SUCCESSFUL, Success

- [ ] **Step 2: Verify functionality**
  1. Open app → two entry cards visible: "PDF提取" and "文档扫描"
  2. Tap "文档扫描" → photo source picker appears
  3. Pick a photo → auto-correction runs → corrected preview shown
  4. Toggle "原图对比" to verify difference
  5. Tap "导出为 PDF" → success dialog with 分享/保存
  6. Save to device → verify the PDF opens correctly

- [ ] **Step 3: Commit final build**

```bash
git add -A && git commit -m "feat: complete document scan feature"
```
