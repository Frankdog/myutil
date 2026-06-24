package com.myutil.pdfextractor

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myutil.pdfextractor.navigation.NavRoutes
import com.myutil.pdfextractor.ui.filelist.FileListScreen

@Composable
fun PDFExtractorApp(startDestination: String = NavRoutes.FileList.route) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.FileList.route) {
            FileListScreen(
                onPdfSelected = { uri ->
                    navController.navigate(NavRoutes.PageGrid.buildRoute(uri.toString()))
                }
            )
        }
        composable(NavRoutes.PageGrid.route) { backStackEntry ->
            // Placeholder - will be replaced in Task 5
        }
    }
}