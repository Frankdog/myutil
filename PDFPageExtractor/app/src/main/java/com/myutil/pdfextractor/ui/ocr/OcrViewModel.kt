package com.myutil.pdfextractor.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OcrViewModel : ViewModel() {
    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    fun recognize(context: Context, uri: Uri) {
        _isProcessing.value = true
        _error.value = null
        _recognizedText.value = null
        _bitmap.value = null

        viewModelScope.launch {
            try {
                val bm = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: return@withContext null
                    val orientation = try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            ExifInterface(stream).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                        }
                    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    }
                    if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != null) {
                        Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
                    } else {
                        input
                    }
                }
                if (bm == null) {
                    _error.value = "无法解码图片"
                    _isProcessing.value = false
                    return@launch
                }
                _bitmap.value = bm

                val image = InputImage.fromBitmap(bm, 0)
                val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                val result = withContext(Dispatchers.IO) { Tasks.await(recognizer.process(image)) }
                val text = result.text
                if (text.isBlank()) {
                    _recognizedText.value = "未识别到文字"
                } else {
                    _recognizedText.value = text
                }
            } catch (e: Exception) {
                _error.value = "识别失败: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun reset() {
        _recognizedText.value = null
        _isProcessing.value = false
        _error.value = null
        _bitmap.value?.recycle()
        _bitmap.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _bitmap.value?.recycle()
    }
}
