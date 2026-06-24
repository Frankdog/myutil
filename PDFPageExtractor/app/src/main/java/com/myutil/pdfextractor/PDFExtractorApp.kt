package com.myutil.pdfextractor

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myutil.pdfextractor.navigation.NavRoutes

@Composable
fun PDFExtractorApp(startDestination: String = NavRoutes.FileList.route) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.FileList.route) {
            // Placeholder - will be replaced in Task 3
        }
        composable(NavRoutes.PageGrid.route) { backStackEntry ->
            // Placeholder - will be replaced in Task 4
        }
    }
}