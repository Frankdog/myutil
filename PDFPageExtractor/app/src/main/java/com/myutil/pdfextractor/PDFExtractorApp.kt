package com.myutil.pdfextractor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myutil.pdfextractor.navigation.NavRoutes
import com.myutil.pdfextractor.ui.filelist.FileListScreen
import com.myutil.pdfextractor.ui.filelist.FileListViewModel

@Composable
fun PDFExtractorApp(startDestination: String = NavRoutes.FileList.route) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.FileList.route) {
            val viewModel: FileListViewModel = viewModel()
            val navigateUri by viewModel.navigateToPageGrid.collectAsState()

            androidx.compose.runtime.LaunchedEffect(navigateUri) {
                navigateUri?.let { uri ->
                    viewModel.onNavigatedToPageGrid()
                    navController.navigate(NavRoutes.PageGrid.buildRoute(uri.toString()))
                }
            }

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