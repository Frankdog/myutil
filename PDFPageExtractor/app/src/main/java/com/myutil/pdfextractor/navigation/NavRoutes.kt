package com.myutil.pdfextractor.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class NavRoutes(val route: String) {
    data object FileList : NavRoutes("file_list")
    data object PageGrid : NavRoutes("page_grid/{uri}") {
        fun buildRoute(uri: String): String =
            "page_grid/${URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())}"
    }
    data object Scan : NavRoutes("scan/{uri}") {
        fun buildRoute(uri: String): String =
            "scan/${URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())}"
    }
    data object Collage : NavRoutes("collage")
    data object Ocr : NavRoutes("ocr")
}
