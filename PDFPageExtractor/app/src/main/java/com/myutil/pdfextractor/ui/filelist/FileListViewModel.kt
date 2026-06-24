package com.myutil.pdfextractor.ui.filelist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myutil.pdfextractor.data.PdfRepository
import com.myutil.pdfextractor.data.model.PdfInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)

    private val _pdfFiles = MutableStateFlow<List<PdfInfo>>(emptyList())
    val pdfFiles: StateFlow<List<PdfInfo>> = _pdfFiles.asStateFlow()

    private val _navigateToPageGrid = MutableStateFlow<Uri?>(null)
    val navigateToPageGrid: StateFlow<Uri?> = _navigateToPageGrid.asStateFlow()

    fun addPdfFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val handle = repository.loadPdf(uri)
                val pageCount = repository.getPageCount(handle)
                repository.closeDocument(handle)

                val cursor = getApplication<Application>().contentResolver.query(
                    uri, null, null, null, null
                )
                val name = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) it.getString(nameIdx) else uri.lastPathSegment ?: "Unknown"
                    } else uri.lastPathSegment ?: "Unknown"
                } ?: uri.lastPathSegment ?: "Unknown"

                val size = cursor?.use {
                    if (it.moveToFirst()) {
                        val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIdx >= 0) it.getLong(sizeIdx) else -1L
                    } else -1L
                } ?: -1L

                val info = PdfInfo(name = name, size = size, pageCount = pageCount, uri = uri)
                val current = _pdfFiles.value.toMutableList()
                if (current.none { it.uri == uri }) {
                    current.add(0, info)
                    _pdfFiles.value = current
                }
            } catch (e: Exception) {
                // silently ignore corrupt PDFs
            }
        }
    }

    fun onPdfSelected(uri: Uri) {
        _navigateToPageGrid.value = uri
    }

    fun onNavigatedToPageGrid() {
        _navigateToPageGrid.value = null
    }
}
