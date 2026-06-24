package com.myutil.pdfextractor.ui.pagegrid

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myutil.pdfextractor.data.PdfDocumentHandle
import com.myutil.pdfextractor.data.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class PageItem(
    val index: Int,
    val label: String,
    val bitmap: Bitmap? = null,
    val isSelected: Boolean = false
)

class PageGridViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)
    private var documentHandle: PdfDocumentHandle? = null
    private var exportedCacheFile: File? = null

    private val _pages = MutableStateFlow<List<PageItem>>(emptyList())
    val pages: StateFlow<List<PageItem>> = _pages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _shareUri = MutableStateFlow<Uri?>(null)
    val shareUri: StateFlow<Uri?> = _shareUri.asStateFlow()

    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    var pageRangeInput by mutableStateOf("")
        private set

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            recycleAllBitmaps()
            exportedCacheFile = null
            _shareUri.value = null
            try {
                val handle = withContext(Dispatchers.IO) { repository.loadPdf(uri) }
                documentHandle = handle
                val count = repository.getPageCount(handle)

                val thumbWidth = 300
                val thumbHeight = 400

                val items = (0 until count).map { index ->
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            repository.renderPage(handle, index, thumbWidth, thumbHeight)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    PageItem(index = index, label = "第${index + 1}页", bitmap = bitmap)
                }
                _pages.value = items
            } catch (e: Exception) {
                _exportResult.value = "打开PDF失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePage(index: Int) {
        _pages.value = _pages.value.map {
            if (it.index == index) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun updatePageRangeInput(input: String) {
        pageRangeInput = input
    }

    fun applyPageRangeInput() {
        val selected = parsePageRange(pageRangeInput, _pages.value.size)
        _pages.value = _pages.value.map {
            it.copy(isSelected = it.index in selected)
        }
    }

    fun exportToCache() {
        viewModelScope.launch {
            val handle = documentHandle ?: return@launch
            val selectedPages = _pages.value.filter { it.isSelected }.map { it.index }.toSet()
            if (selectedPages.isEmpty()) {
                _exportResult.value = "请先选择要导出的页面"
                return@launch
            }
            _selectedCount.value = selectedPages.size
            try {
                val cacheFile = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val file = File(app.cacheDir, "export_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(file).use { outputStream ->
                        repository.extractPages(handle, selectedPages, outputStream)
                    }
                    file
                }
                exportedCacheFile = cacheFile
                val authority = "${getApplication<Application>().packageName}.fileprovider"
                _shareUri.value = FileProvider.getUriForFile(getApplication(), authority, cacheFile)
                _exportResult.value = "导出成功"
            } catch (e: Exception) {
                _exportResult.value = "导出失败: ${e.message}"
            }
        }
    }

    fun saveToPermanentLocation(outputUri: Uri) {
        viewModelScope.launch {
            val cacheFile = exportedCacheFile ?: return@launch
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = FileInputStream(cacheFile)
                    val outputStream = getApplication<Application>().contentResolver.openOutputStream(outputUri)
                    inputStream.use { input ->
                        outputStream?.use { output ->
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

    private fun recycleAllBitmaps() {
        _pages.value.forEach { it.bitmap?.recycle() }
    }

    override fun onCleared() {
        super.onCleared()
        recycleAllBitmaps()
        documentHandle?.let { repository.closeDocument(it) }
    }
}

internal fun parsePageRange(input: String, maxPage: Int): Set<Int> {
    if (input.isBlank()) return emptySet()
    val result = mutableSetOf<Int>()
    val parts = input.split(",")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            if (range.size == 2) {
                val start = range[0].trim().toIntOrNull() ?: continue
                val end = range[1].trim().toIntOrNull() ?: continue
                val actualStart = minOf(start, end)
                val actualEnd = maxOf(start, end)
                (actualStart - 1 until actualEnd).forEach { if (it in 0 until maxPage) result.add(it) }
            }
        } else {
            val page = trimmed.toIntOrNull()
            if (page != null && page - 1 in 0 until maxPage) result.add(page - 1)
        }
    }
    return result
}
