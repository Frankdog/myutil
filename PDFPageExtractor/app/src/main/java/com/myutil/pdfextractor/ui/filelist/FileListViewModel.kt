package com.myutil.pdfextractor.ui.filelist

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myutil.pdfextractor.data.PdfRepository
import com.myutil.pdfextractor.data.model.PdfInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)
    private val cacheDir = File(application.cacheDir, "pdfs")

    init { cacheDir.mkdirs() }

    private val _pdfFiles = MutableStateFlow<List<PdfInfo>>(emptyList())
    val pdfFiles: StateFlow<List<PdfInfo>> = _pdfFiles.asStateFlow()

    private val _openFilePicker = MutableSharedFlow<Unit>()
    val openFilePicker: SharedFlow<Unit> = _openFilePicker.asSharedFlow()

    fun addPdfFile(uri: Uri) {
        viewModelScope.launch {
            try {
                // Copy PDF to internal cache to avoid permission issues (MIUI FileProvider)
                val cachedUri = withContext(Dispatchers.IO) { copyToCache(uri) }

                val handle = repository.loadPdf(cachedUri)
                val pageCount = repository.getPageCount(handle)
                repository.closeDocument(handle)

                var name = uri.lastPathSegment ?: "Unknown"
                var size = -1L
                getApplication<Application>().contentResolver.query(
                    uri, null, null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        name = if (nameIdx >= 0) cursor.getString(nameIdx) else name
                        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else size
                    }
                }

                val info = PdfInfo(name = name, size = size, pageCount = pageCount, uri = cachedUri)
                val current = _pdfFiles.value.toMutableList()
                if (current.none { it.uri == cachedUri }) {
                    current.add(0, info)
                    _pdfFiles.value = current
                }
            } catch (e: Exception) {
                // silently ignore
            }
        }
    }

    private fun copyToCache(uri: Uri): Uri {
        val app = getApplication<Application>()
        val name = "pdf_${System.currentTimeMillis()}.pdf"
        val dest = File(cacheDir, name)
        app.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot read URI: $uri")
        return Uri.fromFile(dest)
    }

    fun onOpenFilePicker() {
        viewModelScope.launch {
            _openFilePicker.emit(Unit)
        }
    }
}
