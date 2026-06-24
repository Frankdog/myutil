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
        val src = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)

            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 200.0)

            val contours = ArrayList<MatOfPoint>()
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

            warped.release()
            binarized.release()

            return AutoCorrectResult(correctedBitmap = outBitmap, corners = ordered)
        } finally {
            src.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
        }
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
