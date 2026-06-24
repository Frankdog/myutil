package com.myutil.pdfextractor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.RenderParams
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import java.io.OutputStream

class PdfDocumentHandle internal constructor(
    internal val renderer: PdfRenderer,
    internal val fd: ParcelFileDescriptor
)

class PdfRepository(private val context: Context) {

    fun loadPdf(uri: Uri): PdfDocumentHandle {
        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open file: $uri")
        val renderer = PdfRenderer(fd)
        return PdfDocumentHandle(renderer, fd)
    }

    fun getPageCount(handle: PdfDocumentHandle): Int {
        return handle.renderer.pageCount
    }

    fun renderPage(handle: PdfDocumentHandle, index: Int, width: Int, height: Int): Bitmap {
        val page = handle.renderer.openPage(index)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val scaleX = width.toFloat() / page.width
        val scaleY = height.toFloat() / page.height
        val scale = minOf(scaleX, scaleY)
        val matrix = android.graphics.Matrix()
        matrix.setScale(scale, scale)
        page.render(bitmap, null, matrix, RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).build())
        page.close()
        return bitmap
    }

    fun extractPages(handle: PdfDocumentHandle, pages: Set<Int>, outputStream: OutputStream) {
        val sortedPages = pages.sorted()
        val newDocument = PdfDocument()
        val pageCount = handle.renderer.pageCount

        sortedPages.forEachIndexed { outputIndex, pageIndex ->
            if (pageIndex !in 0 until pageCount) return@forEachIndexed
            val srcPage = handle.renderer.openPage(pageIndex)
            val width = srcPage.width * 2
            val height = srcPage.height * 2

            val pageInfo = PdfDocument.PageInfo.Builder(width, height, outputIndex + 1).create()
            val page = newDocument.startPage(pageInfo)
            val canvas = page.canvas
            val scaleX = width.toFloat() / srcPage.width
            val scaleY = height.toFloat() / srcPage.height
            val matrix = android.graphics.Matrix()
            matrix.setScale(scaleX, scaleY)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            srcPage.render(bitmap, null, matrix, RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).build())
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()
            newDocument.finishPage(page)
            srcPage.close()
        }

        newDocument.writeTo(outputStream)
        newDocument.close()
    }

    fun extractPages(handle: PdfDocumentHandle, pages: Set<Int>, outputUri: Uri) {
        val outputFd = context.contentResolver.openFileDescriptor(outputUri, "w")
            ?: throw IllegalArgumentException("Cannot open output: $outputUri")
        try {
            extractPages(handle, pages, FileOutputStream(outputFd.fileDescriptor))
        } finally {
            outputFd.close()
        }
    }

    fun closeDocument(handle: PdfDocumentHandle) {
        handle.renderer.close()
        handle.fd.close()
    }
}