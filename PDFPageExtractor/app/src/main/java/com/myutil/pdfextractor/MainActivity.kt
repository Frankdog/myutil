package com.myutil.pdfextractor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.myutil.pdfextractor.ui.theme.PDFExtractorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUri = intent?.data
        setContent {
            PDFExtractorTheme {
                PDFExtractorApp()
            }
        }
    }
}