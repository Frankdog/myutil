package com.myutil.pdfextractor.ui.collage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CollageViewModel : ViewModel() {
    private val _selectedImages = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImages: StateFlow<List<Uri>> = _selectedImages.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private var cachedFile: File? = null

    fun refreshPreview(context: Context) {
        val uris = _selectedImages.value
        if (uris.isEmpty()) {
            _previewBitmap.value = null
            return
        }
        viewModelScope.launch {
            _previewBitmap.value = withContext(Dispatchers.IO) {
                createStitchBitmap(context, uris)
            }
        }
    }

    fun setImages(context: Context, uris: List<Uri>) {
        _selectedImages.value = uris
        refreshPreview(context)
    }

    fun addImages(uris: List<Uri>) {
        _selectedImages.value = _selectedImages.value + uris
    }

    fun removeImage(index: Int) {
        val images = _selectedImages.value.toMutableList()
        if (index in images.indices) {
            images.removeAt(index)
            _selectedImages.value = images
        }
    }

    fun swapImages(index1: Int, index2: Int) {
        val images = _selectedImages.value.toMutableList()
        if (index1 in images.indices && index2 in images.indices) {
            val temp = images[index1]
            images[index1] = images[index2]
            images[index2] = temp
            _selectedImages.value = images
        }
    }

    fun exportAsImage(context: Context, onResult: (error: String?) -> Unit) {
        val images = _selectedImages.value
        if (images.isEmpty()) {
            onResult("请先选择图片")
            return
        }
        _isExporting.value = true
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { createStitchBitmap(context, images) }
                if (bitmap == null) {
                    withContext(Dispatchers.Main) { onResult("拼接失败") }
                    return@launch
                }
                cachedFile = withContext(Dispatchers.IO) {
                    clearCache()
                    saveImageToGallery(context, bitmap)
                    saveBitmapToCache(context, bitmap)
                }
                bitmap.recycle()
                withContext(Dispatchers.Main) { onResult(null) }
            } catch (e: Exception) {
                Log.e(TAG, "exportAsImage failed", e)
                withContext(Dispatchers.Main) { onResult("导出失败: ${e.message}") }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportAsPdf(context: Context, onResult: (error: String?) -> Unit) {
        val images = _selectedImages.value
        if (images.isEmpty()) {
            onResult("请先选择图片")
            return
        }
        _isExporting.value = true
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    clearCache()
                    val pdfFile = createPdf(context, images) ?: return@withContext null
                    saveFileToDownloads(context, pdfFile)
                    pdfFile
                }
                if (file == null) {
                    withContext(Dispatchers.Main) { onResult("PDF生成失败") }
                    return@launch
                }
                cachedFile = file
                withContext(Dispatchers.Main) { onResult(null) }
            } catch (e: Exception) {
                Log.e(TAG, "exportAsPdf failed", e)
                withContext(Dispatchers.Main) { onResult("导出失败: ${e.message}") }
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun shareExportedFile(context: Context) {
        val file = cachedFile ?: return
        val mimeType = if (file.name.endsWith(".pdf")) "application/pdf" else "image/jpeg"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享"))
    }

    private fun clearCache() {
        cachedFile?.let { file ->
            if (file.name.startsWith("stitch_") && !file.delete()) {
                Log.w(TAG, "Failed to delete cached file: ${file.name}")
            }
        }
        cachedFile = null
    }

    private fun createPdf(context: Context, images: List<Uri>): File? {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1

        for (uri in images) {
            val bitmap = decodeBitmap(context, uri, pageWidth, pageHeight) ?: continue
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum++).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val scale = minOf(
                pageWidth.toFloat() / bitmap.width,
                pageHeight.toFloat() / bitmap.height
            )
            val drawW = (bitmap.width * scale).toInt()
            val drawH = (bitmap.height * scale).toInt()
            val dx = (pageWidth - drawW) / 2
            val dy = (pageHeight - drawH) / 2
            canvas.drawBitmap(bitmap, null, android.graphics.Rect(dx, dy, dx + drawW, dy + drawH), null)
            document.finishPage(page)
            bitmap.recycle()
        }

        val file = File(context.cacheDir, "stitch_${System.currentTimeMillis()}.pdf")
        try {
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return file
    }

    private fun createStitchBitmap(context: Context, images: List<Uri>): Bitmap? {
        val count = images.size
        if (count == 0) return null
        val outputWidth = 1080
        val heights = IntArray(count)
        val bitmaps = arrayOfNulls<Bitmap>(count)
        for (i in 0 until count) {
            val uri = images[i] ?: continue
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input, null, opts)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read dimensions for image $i", e)
            }
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                val h = (outputWidth.toLong() * opts.outHeight / opts.outWidth).toInt()
                heights[i] = h
                val sample = calculateSampleSize(opts.outWidth, opts.outHeight, outputWidth, h)
                val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        bitmaps[i] = android.graphics.BitmapFactory.decodeStream(input, null, decodeOpts)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode image $i", e)
                }
            } else {
                heights[i] = outputWidth
            }
        }
        val totalHeight = heights.sum().coerceAtLeast(1)
        val result = Bitmap.createBitmap(outputWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { isAntiAlias = true }
        var y = 0
        for (i in 0 until count) {
            val bm = bitmaps[i] ?: continue
            val scaled = Bitmap.createScaledBitmap(bm, outputWidth, heights[i], true)
            canvas.drawBitmap(scaled, 0f, y.toFloat(), paint)
            if (scaled != bm) scaled.recycle()
            bm.recycle()
            y += heights[i]
        }
        return result
    }

    private fun decodeBitmap(context: Context, uri: Uri, maxW: Int, maxH: Int): Bitmap? {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, opts)
            }
        } catch (e: Exception) { return null }
        val sample = calculateSampleSize(opts.outWidth, opts.outHeight, maxW, maxH)
        val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, decodeOpts)
            }
        } catch (e: Exception) { null }
    }

    private fun calculateSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        if (srcWidth > reqWidth || srcHeight > reqHeight) {
            val halfWidth = srcWidth / 2
            val halfHeight = srcHeight / 2
            while (halfWidth / sampleSize > reqWidth && halfHeight / sampleSize > reqHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun saveImageToGallery(context: Context, bitmap: Bitmap) {
        val filename = "stitch_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PDFExtractor")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            FileOutputStream(File(dir, filename)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
    }

    private fun saveFileToDownloads(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PDFExtractor")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            file.copyTo(File(dir, file.name), overwrite = true)
        }
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): File? {
        val file = File(context.cacheDir, "stitch_${System.currentTimeMillis()}.jpg")
        return try {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            file
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save bitmap to cache", e)
            null
        }
    }

    companion object {
        private const val TAG = "CollageViewModel"
    }
}
