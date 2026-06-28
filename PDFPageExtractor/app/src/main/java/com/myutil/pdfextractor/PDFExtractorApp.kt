package com.myutil.pdfextractor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myutil.pdfextractor.navigation.NavRoutes
import com.myutil.pdfextractor.ui.filelist.FileListScreen
import com.myutil.pdfextractor.ui.pagegrid.PageGridScreen
import com.myutil.pdfextractor.ui.scan.ScanPreviewScreen
import com.myutil.pdfextractor.ui.collage.CollagePendingUris
import com.myutil.pdfextractor.ui.collage.CollageScreen
import com.myutil.pdfextractor.ui.ocr.OcrScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun PDFExtractorApp(
    sharedUri: Uri? = null,
    startDestination: String = NavRoutes.FileList.route
) {
    val navController = rememberNavController()
    var hasNavigatedForShare by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun copyToCache(uri: Uri): Uri {
        val cacheDir = File(context.cacheDir, "pdfs")
        cacheDir.mkdirs()
        val dest = File(cacheDir, "pdf_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(dest)
    }

    LaunchedEffect(sharedUri) {
        if (sharedUri != null && !hasNavigatedForShare) {
            hasNavigatedForShare = true
            val cached = withContext(Dispatchers.IO) { copyToCache(sharedUri) }
            navController.navigate(NavRoutes.PageGrid.buildRoute(cached.toString()))
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.FileList.route) {
            FileListScreen(
                onPdfSelected = { uri ->
                    scope.launch {
                        val cached = withContext(Dispatchers.IO) { copyToCache(uri) }
                        navController.navigate(NavRoutes.PageGrid.buildRoute(cached.toString()))
                    }
                },
                onScanSelected = { uri ->
                    scope.launch {
                        navController.navigate(NavRoutes.Scan.buildRoute(uri.toString()))
                    }
                },
                onCollageSelected = { uris ->
                    CollagePendingUris.value = uris
                    navController.navigate(NavRoutes.Collage.route)
                },
                onOcrClick = { navController.navigate(NavRoutes.Ocr.route) }
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
        composable(
            NavRoutes.Scan.route,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uri = URLDecoder.decode(
                backStackEntry.arguments?.getString("uri") ?: return@composable,
                StandardCharsets.UTF_8.toString()
            )
            ScanPreviewScreen(
                imageUri = Uri.parse(uri),
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.Collage.route) {
            CollageScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.Ocr.route) {
            OcrScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
