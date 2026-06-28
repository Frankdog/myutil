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
        @Synchronized
        fun ensureInitialized() {
            if (!initialized) {
                OpenCVLoader.initDebug()
                initialized = true
            }
        }
    }

    fun autoCorrect(bitmap: Bitmap): AutoCorrectResult? {
        ensureInitialized()
        android.util.Log.d("ImgProc", "input bitmap: ${bitmap.width}x${bitmap.height} config=${bitmap.config}")
        val argb = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            android.util.Log.d("ImgProc", "converting to ARGB_8888")
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else bitmap
        val src = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        try {
            Utils.bitmapToMat(argb, src)
            android.util.Log.d("ImgProc", "src Mat: ${src.cols()}x${src.rows()} channels=${src.channels()}")
            if (argb !== bitmap) argb.recycle()

            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGRA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(blurred, edges, 30.0, 100.0)

            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            android.util.Log.d("ImgProc", "total contours found=${contours.size}")

            val imgW = src.cols()
            val imgH = src.rows()
            val margin = (minOf(imgW, imgH) * 0.02).toInt().coerceAtLeast(5)
            var bestArea = 0.0
            var documentContour: MatOfPoint2f? = null

            // Try multiple epsilon values for robustness
            val epsilons = listOf(0.01, 0.02, 0.03, 0.05, 0.08)

            for (epsilonFactor in epsilons) {
                for (contour in contours) {
                    val area = Imgproc.contourArea(contour)
                    if (area < imgW * imgH * 0.01) continue // skip tiny contours
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val approx = MatOfPoint2f()
                    val epsilon = epsilonFactor * Imgproc.arcLength(contour2f, true)
                    Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
                    contour2f.release()
                    if (approx.toArray().size != 4) {
                        approx.release()
                        continue
                    }
                    val points = approx.toArray()
                    val touchesEdge = points.any { pt ->
                        pt.x <= margin || pt.y <= margin ||
                        pt.x >= imgW - margin || pt.y >= imgH - margin
                    }
                    if (touchesEdge) {
                        approx.release()
                        continue
                    }
                    if (area > bestArea) {
                        bestArea = area
                        documentContour?.release()
                        documentContour = approx
                    } else {
                        approx.release()
                    }
                }
                if (documentContour != null) {
                    android.util.Log.d("ImgProc", "found doc contour at epsilon=$epsilonFactor area=$bestArea")
                    break
                }
            }

            // Fallback: use largest 4-point contour (even if touches edge)
            if (documentContour == null) {
                android.util.Log.d("ImgProc", "fallback: using largest 4-point contour (touching edge OK)")
                for (contour in contours) {
                    val area = Imgproc.contourArea(contour)
                    if (area < bestArea) continue
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val approx = MatOfPoint2f()
                    val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
                    Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
                    contour2f.release()
                    if (approx.toArray().size == 4) {
                        bestArea = area
                        documentContour?.release()
                        documentContour = approx
                    } else { approx.release() }
                }
            }

            if (documentContour == null) return null

            val corners = documentContour.toArray().toList()
            documentContour.release()
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

            // Debug: skip binarize, show raw warp result
            val outBitmap = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, outBitmap)

            warped.release()

            return AutoCorrectResult(correctedBitmap = outBitmap, corners = ordered)
        } finally {
            src.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
        }
    }

    fun removeHandwriting(bitmap: Bitmap, context: android.content.Context? = null): Bitmap {
        return removeHandwritingByColor(bitmap)
    }

    private fun removeHandwritingByColor(bitmap: Bitmap): Bitmap {
        ensureInitialized()
        val argb = if (bitmap.config != Bitmap.Config.ARGB_8888)
            bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val src = Mat()
        Utils.bitmapToMat(argb, src)
        if (argb !== bitmap) argb.recycle()

        // Only remove colored ink handwriting (blue/red/green)
        val hsv = Mat()
        Imgproc.cvtColor(src, hsv, Imgproc.COLOR_RGB2HSV)

        val maskColor = Mat.zeros(src.size(), CvType.CV_8UC1)

        // Blue ink
        val maskBlue = Mat()
        Core.inRange(hsv, Scalar(100.0, 40.0, 40.0), Scalar(140.0, 255.0, 255.0), maskBlue)
        Core.bitwise_or(maskColor, maskBlue, maskColor)
        maskBlue.release()

        // Red ink
        val maskRed1 = Mat()
        val maskRed2 = Mat()
        Core.inRange(hsv, Scalar(0.0, 40.0, 40.0), Scalar(10.0, 255.0, 255.0), maskRed1)
        Core.inRange(hsv, Scalar(160.0, 40.0, 40.0), Scalar(180.0, 255.0, 255.0), maskRed2)
        Core.bitwise_or(maskColor, maskRed1, maskColor)
        Core.bitwise_or(maskColor, maskRed2, maskColor)
        maskRed1.release()
        maskRed2.release()

        // Green ink
        val maskGreen = Mat()
        Core.inRange(hsv, Scalar(40.0, 40.0, 40.0), Scalar(80.0, 255.0, 255.0), maskGreen)
        Core.bitwise_or(maskColor, maskGreen, maskColor)
        maskGreen.release()
        hsv.release()

        // Dilate mask to cover edges
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(maskColor, maskColor, kernel)
        kernel.release()

        // Inpaint using the Gaussian-blurred background as fill
        val bg = Mat()
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
        val blurSize = ((minOf(gray.cols(), gray.rows()) / 6) or 1).coerceIn(51, 501)
        Imgproc.GaussianBlur(gray, bg, Size(blurSize.toDouble(), blurSize.toDouble()), 0.0)

        val result = Mat()
        src.copyTo(result)
        bg.copyTo(result, maskColor)

        val outBitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(result, outBitmap)

        src.release()
        gray.release()
        bg.release()
        maskColor.release()
        result.release()
        return outBitmap
    }

    fun toPrintReady(bitmap: Bitmap): Bitmap {
        ensureInitialized()
        val argb = if (bitmap.config != Bitmap.Config.ARGB_8888)
            bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val src = Mat()
        Utils.bitmapToMat(argb, src)
        if (argb !== bitmap) argb.recycle()

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGRA2GRAY)

        // 1. 提取光照背景（大模糊，只保留光照，不保留文字）
        val bg = Mat()
        val blurSize = ((minOf(gray.cols(), gray.rows()) / 6) or 1).coerceIn(51, 501)
        Imgproc.GaussianBlur(gray, bg, Size(blurSize.toDouble(), blurSize.toDouble()), 0.0)

        // 2. 光照校正：原图 / 背景 * 255
        val grayF = Mat()
        val bgF = Mat()
        gray.convertTo(grayF, CvType.CV_32F)
        bg.convertTo(bgF, CvType.CV_32F)
        bg.release()
        Core.divide(grayF, bgF, grayF, 255.0)
        grayF.convertTo(gray, CvType.CV_8UC1)
        grayF.release()
        bgF.release()

        // 3. 轻微高斯去噪
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)

        // 4. Otsu 二值化
        val binary = Mat()
        Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_OTSU)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)
        kernel.release()

        val outBitmap = Bitmap.createBitmap(binary.cols(), binary.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(binary, outBitmap)

        src.release()
        gray.release()
        blurred.release()
        binary.release()
        return outBitmap
    }

    fun warpPerspective(bitmap: Bitmap, corners: List<org.opencv.core.Point>, outputSize: org.opencv.core.Size): Bitmap {
        ensureInitialized()
        val argb = if (bitmap.config != Bitmap.Config.ARGB_8888)
            bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val src = Mat()
        Utils.bitmapToMat(argb, src)
        if (argb !== bitmap) argb.recycle()

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
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGRA2GRAY)
        val binary = Mat()
        Imgproc.adaptiveThreshold(gray, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 21, 2.0)
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
