package com.myutil.pdfextractor

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myutil.pdfextractor.navigation.NavRoutes
import com.myutil.pdfextractor.ui.filelist.FileListScreen
import com.myutil.pdfextractor.ui.pagegrid.PageGridScreen

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
        composable(
            NavRoutes.PageGrid.route,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
            PageGridScreen(
                uri = uri,
                onBack = { navController.popBackStack() }
            )
        }
    }
}