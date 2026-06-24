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
