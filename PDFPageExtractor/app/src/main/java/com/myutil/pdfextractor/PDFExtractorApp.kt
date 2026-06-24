package com.myutil.pdfextractor

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myutil.pdfextractor.navigation.NavRoutes
import com.myutil.pdfextractor.ui.filelist.FileListScreen
import com.myutil.pdfextractor.ui.filelist.FileListViewModel
import com.myutil.pdfextractor.ui.pagegrid.PageGridScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun PDFExtractorApp(
    sharedUri: Uri? = null,
    startDestination: String = NavRoutes.FileList.route
) {
    val navController = rememberNavController()
    var hasNavigatedForShare by remember { mutableStateOf(false) }

    LaunchedEffect(sharedUri) {
        if (sharedUri != null && !hasNavigatedForShare) {
            hasNavigatedForShare = true
            navController.navigate(NavRoutes.PageGrid.buildRoute(sharedUri.toString()))
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.FileList.route) {
            val viewModel: FileListViewModel = viewModel()

            LaunchedEffect(sharedUri) {
                sharedUri?.let { viewModel.addPdfFile(it) }
            }

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
            val uri = URLDecoder.decode(
                backStackEntry.arguments?.getString("uri") ?: return@composable,
                StandardCharsets.UTF_8.toString()
            )
            PageGridScreen(
                uri = uri,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
