package com.myutil.pdfextractor.navigation

sealed class NavRoutes(val route: String) {
    data object FileList : NavRoutes("file_list")
    data object PageGrid : NavRoutes("page_grid/{uri}") {
        fun buildRoute(uri: String): String = "page_grid/$uri"
    }
}