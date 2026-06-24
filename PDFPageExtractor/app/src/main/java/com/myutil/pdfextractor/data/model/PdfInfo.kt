package com.myutil.pdfextractor.data.model

import android.net.Uri

data class PdfInfo(
    val name: String,
    val size: Long,
    val pageCount: Int,
    val uri: Uri
)