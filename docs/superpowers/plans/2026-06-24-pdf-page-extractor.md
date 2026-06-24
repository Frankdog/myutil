# PDF Page Extractor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app that reads PDFs, shows page thumbnails, and exports selected pages as a new PDF.

**Architecture:** Single-Activity MVVM with Jetpack Compose. MuPDF handles both rendering and PDF write. SAF (Storage Access Framework) for file pick/save. Intent filter for share-sheet integration.

**Tech Stack:** Kotlin, Jetpack Compose, MuPDF (AGPL), Gradle KTS, Compose Navigation

## Global Constraints

- Min SDK: 29 (Android 10)
- Target SDK: 36
- Package: `com.myutil.pdfextractor`
- Single Activity, Compose Navigation
- Uses Android's built-in PdfRenderer + PdfDocument (no external PDF libs)
- SAF for all file I/O (no raw file path access)
- Dark/light theme support

---

### Task 1: Project Scaffolding

**Files:**
- Create: `PDFPageExtractor/settings.gradle.kts`
- Create: `PDFPageExtractor/build.gradle.kts`
- Create: `PDFPageExtractor/gradle.properties`
- Create: `PDFPageExtractor/gradle/wrapper/gradle-wrapper.properties`
- Create: `PDFPageExtractor/app/build.gradle.kts`
- Create: `PDFPageExtractor/app/src/main/AndroidManifest.xml`
- Create: `PDFPageExtractor/app/src/main/res/values/strings.xml`
- Create: `PDFPageExtractor/app/src/main/res/values/themes.xml`
- Create: `PDFPageExtractor/app/src/main/res/values/colors.xml`
- Create: `PDFPageExtractor/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `PDFPageExtractor/app/proguard-rules.pro`
- Create: `PDFPageExtractor/local.properties` (will need SDK path)

**Interfaces:**
- Consumes: (none — first task)
- Produces: Gradle project that compiles with empty MainActivity

- [ ] **Step 1: Create project root build.gradle.kts**

```kotlin
// PDFPageExtractor/build.gradle.kts
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

- [ ] **Step 2: Create settings.gradle.kts**

```kotlin
// PDFPageExtractor/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PDFPageExtractor"
include(":app")
```

- [ ] **Step 3: Create gradle.properties**

```properties
# PDFPageExtractor/gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create gradle wrapper properties**

```properties
# PDFPageExtractor/gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5: Create app/build.gradle.kts**

```kotlin
// PDFPageExtractor/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.myutil.pdfextractor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myutil.pdfextractor"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    // MuPDF removed - using Android's built-in PdfRenderer + PdfDocument
    // PdfRenderer (API 21+) for rendering page thumbnails
    // PdfDocument (API 19+) for writing extracted pages
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 6: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PDFPageExtractor">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PDFPageExtractor">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/pdf" />
            </intent-filter>

        </activity>

    </application>

</manifest>
```

- [ ] **Step 7: Create resource files**

```xml
<!-- res/values/strings.xml -->
<resources>
    <string name="app_name">PDF提取</string>
</resources>
```

```xml
<!-- res/values/themes.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PDFPageExtractor" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

```xml
<!-- res/values/colors.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

- [ ] **Step 8: Create temp MainActivity placeholder**

```kotlin
package com.myutil.pdfextractor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { }
    }
}
```

- [ ] **Step 9: Create proguard-rules.pro (empty placeholder)**

```
# PDFPageExtractor/app/proguard-rules.pro
# Add project specific ProGuard rules here.
-keep class com.artifex.** { *; }
```

- [ ] **Step 10: Verify project compiles**

Run: `cd PDFPageExtractor && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 2: Theme & Navigation Shell

**Files:**
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/theme/Color.kt`
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/theme/Type.kt`
- Create: `app/src/main/java/com/myutil/pdfextractor/navigation/NavRoutes.kt`
- Modify: `app/src/main/java/com/myutil/pdfextractor/MainActivity.kt` (full implementation)
- Create: `app/src/main/java/com/myutil/pdfextractor/PDFExtractorApp.kt`

**Interfaces:**
- Consumes: (none)
- Produces: `NavRoutes` sealed class, `PDFExtractorApp` composable, Theme composable

- [ ] **Step 1: Create Color.kt**

```kotlin
package com.myutil.pdfextractor.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

- [ ] **Step 2: Create Type.kt**

```kotlin
package com.myutil.pdfextractor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: Create Theme.kt**

```kotlin
package com.myutil.pdfextractor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun PDFExtractorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 4: Create NavRoutes.kt**

```kotlin
package com.myutil.pdfextractor.navigation

sealed class NavRoutes(val route: String) {
    data object FileList : NavRoutes("file_list")
    data object PageGrid : NavRoutes("page_grid/{uri}") {
        fun buildRoute(uri: String): String = "page_grid/$uri"
    }
}
```

- [ ] **Step 5: Create PDFExtractorApp.kt (NavHost shell with placeholder screens)**

```kotlin
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
```

- [ ] **Step 6: Implement MainActivity.kt**

```kotlin
package com.myutil.pdfextractor

import android.content.Intent
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
```

- [ ] **Step 7: Verify compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 3: Data Layer — PdfRepository

**Files:**
- Create: `app/src/main/java/com/myutil/pdfextractor/data/model/PdfInfo.kt`
- Create: `app/src/main/java/com/myutil/pdfextractor/data/PdfRepository.kt`

**Interfaces:**
- Consumes: Android Context (provided by application)
- Produces:
  - `data class PdfInfo(name: String, size: Long, pageCount: Int, uri: Uri)`
  - `class PdfRepository(context: Context)`
    - `fun loadPdf(uri: Uri): PdfDocumentHandle` — opens PDF via PdfRenderer
    - `fun renderPage(handle: PdfDocumentHandle, index: Int, width: Int, height: Int): Bitmap`
    - `fun getPageCount(handle: PdfDocumentHandle): Int`
    - `fun extractPages(handle: PdfDocumentHandle, pages: Set<Int>, outputUri: Uri)`
    - `fun closeDocument(handle: PdfDocumentHandle)`

Uses Android's built-in APIs:
- `android.graphics.pdf.PdfRenderer` (API 21+) for rendering page thumbnails
- `android.graphics.pdf.PdfDocument` (API 19+) for writing extracted pages

- [ ] **Step 1: Create PdfInfo.kt**

```kotlin
package com.myutil.pdfextractor.data.model

import android.net.Uri

data class PdfInfo(
    val name: String,
    val size: Long,
    val pageCount: Int,
    val uri: Uri
)
```

- [ ] **Step 2: Create PdfRepository.kt**

```kotlin
package com.myutil.pdfextractor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream

class PdfDocumentHandle internal constructor(
    internal val renderer: PdfRenderer,
    internal val fd: ParcelFileDescriptor
)

class PdfRepository(private val context: Context) {

    fun loadPdf(uri: Uri): PdfDocumentHandle {
        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open file: $uri")
        val renderer = PdfRenderer(fd)
        return PdfDocumentHandle(renderer, fd)
    }

    fun getPageCount(handle: PdfDocumentHandle): Int {
        return handle.renderer.pageCount
    }

    fun renderPage(handle: PdfDocumentHandle, index: Int, width: Int, height: Int): Bitmap {
        val page = handle.renderer.openPage(index)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val scaleX = width.toFloat() / page.width
        val scaleY = height.toFloat() / page.height
        val scale = minOf(scaleX, scaleY)
        val matrix = android.graphics.Matrix()
        matrix.setScale(scale, scale)
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    fun extractPages(handle: PdfDocumentHandle, pages: Set<Int>, outputUri: Uri) {
        val outputFd = context.contentResolver.openFileDescriptor(outputUri, "w")
            ?: throw IllegalArgumentException("Cannot open output: $outputUri")
        try {
            val outputStream = FileOutputStream(outputFd.fileDescriptor)
            val sortedPages = pages.sorted()
            val newDocument = PdfDocument()

            for (pageIndex in sortedPages) {
                val srcPage = handle.renderer.openPage(pageIndex)
                val width = srcPage.width * 2  // 2x for reasonable quality
                val height = srcPage.height * 2

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, sortedPages.indexOf(pageIndex) + 1).create()
                val page = newDocument.startPage(pageInfo)
                val canvas = page.canvas
                val scaleX = width.toFloat() / srcPage.width
                val scaleY = height.toFloat() / srcPage.height
                val matrix = android.graphics.Matrix()
                matrix.setScale(scaleX, scaleY)
                srcPage.render(canvas, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                newDocument.finishPage(page)
                srcPage.close()
            }

            newDocument.writeTo(outputStream)
            newDocument.close()
        } finally {
            outputFd.close()
        }
    }

    fun closeDocument(handle: PdfDocumentHandle) {
        handle.renderer.close()
        handle.fd.close()
    }
}
```

- [ ] **Step 3: Verify compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 4: File List Screen

**Files:**
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/filelist/FileListViewModel.kt`
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/filelist/FileListScreen.kt`
- Modify: `app/src/main/java/com/myutil/pdfextractor/PDFExtractorApp.kt` (wire FileListScreen)

**Interfaces:**
- Consumes: `PdfRepository`, `Uri` for file open
- Produces: `FileListViewModel` with `pdfFiles: StateFlow<List<PdfInfo>>`, `onPdfSelected(Uri)`, `onOpenFilePicker()`

- [ ] **Step 1: Create FileListViewModel.kt**

```kotlin
package com.myutil.pdfextractor.ui.filelist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myutil.pdfextractor.data.PdfRepository
import com.myutil.pdfextractor.data.model.PdfInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)

    private val _pdfFiles = MutableStateFlow<List<PdfInfo>>(emptyList())
    val pdfFiles: StateFlow<List<PdfInfo>> = _pdfFiles.asStateFlow()

    private val _navigateToPageGrid = MutableStateFlow<Uri?>(null)
    val navigateToPageGrid: StateFlow<Uri?> = _navigateToPageGrid.asStateFlow()

    fun addPdfFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val handle = repository.loadPdf(uri)
                val pageCount = repository.getPageCount(handle)
                repository.closeDocument(handle)

                val cursor = getApplication<Application>().contentResolver.query(
                    uri, null, null, null, null
                )
                val name = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) it.getString(nameIdx) else uri.lastPathSegment ?: "Unknown"
                    } else uri.lastPathSegment ?: "Unknown"
                } ?: uri.lastPathSegment ?: "Unknown"

                val size = cursor?.use {
                    if (it.moveToFirst()) {
                        val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIdx >= 0) it.getLong(sizeIdx) else -1L
                    } else -1L
                } ?: -1L

                val info = PdfInfo(name = name, size = size, pageCount = pageCount, uri = uri)
                val current = _pdfFiles.value.toMutableList()
                if (current.none { it.uri == uri }) {
                    current.add(0, info)
                    _pdfFiles.value = current
                }
            } catch (e: Exception) {
                // silently ignore corrupt PDFs
            }
        }
    }

    fun onPdfSelected(uri: Uri) {
        _navigateToPageGrid.value = uri
    }

    fun onNavigatedToPageGrid() {
        _navigateToPageGrid.value = null
    }
}
```

- [ ] **Step 2: Create FileListScreen.kt**

```kotlin
package com.myutil.pdfextractor.ui.filelist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    onPdfSelected: (Uri) -> Unit,
    viewModel: FileListViewModel = viewModel()
) {
    val pdfFiles by viewModel.pdfFiles.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.addPdfFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF提取") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                filePickerLauncher.launch(arrayOf("application/pdf"))
            }) {
                Icon(Icons.Default.Add, contentDescription = "添加PDF")
            }
        }
    ) { padding ->
        if (pdfFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "点击 + 添加PDF文件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pdfFiles) { pdf ->
                    PdfFileItem(
                        info = pdf,
                        onClick = { onPdfSelected(pdf.uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfFileItem(
    info: com.myutil.pdfextractor.data.model.PdfInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (info.size > 0) {
                            append(formatFileSize(info.size))
                            append(" · ")
                        }
                        append("${info.pageCount} 页")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val df = DecimalFormat("#.#")
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${df.format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${df.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
```

- [ ] **Step 3: Wire FileListScreen into PDFExtractorApp.kt**

```kotlin
// In PDFExtractorApp.kt, replace FileList placeholder:
composable(NavRoutes.FileList.route) {
    val viewModel: FileListViewModel = viewModel()
    val navigateUri by viewModel.navigateToPageGrid.collectAsState()

    LaunchedEffect(navigateUri) {
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
```

- [ ] **Step 4: Verify compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 5: Page Grid Screen (Preview + Export)

**Files:**
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/pagegrid/PageGridViewModel.kt`
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/pagegrid/PageGridScreen.kt`
- Modify: `app/src/main/java/com/myutil/pdfextractor/PDFExtractorApp.kt` (wire PageGridScreen)

**Interfaces:**
- Consumes: `PdfRepository`, `Uri` from navigation argument
- Produces: Page grid with selectable thumbnails, page-range input, export button

- [ ] **Step 1: Create PageGridViewModel.kt**

```kotlin
package com.myutil.pdfextractor.ui.pagegrid

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myutil.pdfextractor.data.PdfDocumentHandle
import com.myutil.pdfextractor.data.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PageItem(
    val index: Int,
    val label: String,
    val bitmap: Bitmap? = null,
    val isSelected: Boolean = false
)

class PageGridViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)
    private var documentHandle: PdfDocumentHandle? = null

    private val _pages = MutableStateFlow<List<PageItem>>(emptyList())
    val pages: StateFlow<List<PageItem>> = _pages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    var pageRangeInput by mutableStateOf("")
        private set

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val handle = withContext(Dispatchers.IO) { repository.loadPdf(uri) }
                documentHandle = handle
                val count = repository.getPageCount(handle)

                // Calculate grid thumbnail size based on screen density
                val thumbWidth = 300
                val thumbHeight = 400

                val items = (0 until count).map { index ->
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            repository.renderPage(handle, index, thumbWidth, thumbHeight)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    PageItem(index = index, label = "第${index + 1}页", bitmap = bitmap)
                }
                _pages.value = items
            } catch (e: Exception) {
                _exportResult.value = "打开PDF失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePage(index: Int) {
        _pages.value = _pages.value.map {
            if (it.index == index) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun updatePageRangeInput(input: String) {
        pageRangeInput = input
    }

    fun applyPageRangeInput() {
        val selected = parsePageRange(pageRangeInput, _pages.value.size)
        _pages.value = _pages.value.map {
            it.copy(isSelected = it.index in selected)
        }
    }

    fun exportSelected(outputUri: Uri) {
        viewModelScope.launch {
            val handle = documentHandle ?: return@launch
            val selectedPages = _pages.value.filter { it.isSelected }.map { it.index }.toSet()
            if (selectedPages.isEmpty()) {
                _exportResult.value = "请先选择要导出的页面"
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    repository.extractPages(handle, selectedPages, outputUri)
                }
                _exportResult.value = "导出成功"
            } catch (e: Exception) {
                _exportResult.value = "导出失败: ${e.message}"
            }
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        documentHandle?.let { repository.closeDocument(it) }
    }
}

internal fun parsePageRange(input: String, maxPage: Int): Set<Int> {
    if (input.isBlank()) return emptySet()
    val result = mutableSetOf<Int>()
    val parts = input.split(",")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            if (range.size == 2) {
                val start = range[0].trim().toIntOrNull() ?: continue
                val end = range[1].trim().toIntOrNull() ?: continue
                (start - 1 until end).forEach { if (it in 0 until maxPage) result.add(it) }
            }
        } else {
            val page = trimmed.toIntOrNull()
            if (page != null && page - 1 in 0 until maxPage) result.add(page - 1)
        }
    }
    return result
}
```

- [ ] **Step 2: Create PageGridScreen.kt**

```kotlin
package com.myutil.pdfextractor.ui.pagegrid

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myutil.pdfextractor.data.PdfRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageGridScreen(
    uri: String,
    onBack: () -> Unit,
    viewModel: PageGridViewModel = viewModel()
) {
    val pages by viewModel.pages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()

    LaunchedEffect(uri) {
        viewModel.loadPdf(Uri.parse(uri))
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri ->
        outputUri?.let { viewModel.exportSelected(it) }
    }

    LaunchedEffect(exportResult) {
        exportResult?.let {
            viewModel.clearExportResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择页面") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = viewModel.pageRangeInput,
                            onValueChange = { viewModel.updatePageRangeInput(it) },
                            label = { Text("页码 (如 1,3,5-8)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { viewModel.applyPageRangeInput() }) {
                            Text("应用")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            saveLauncher.launch("extracted_pages.pdf")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pages.any { it.isSelected }
                    ) {
                        Text("导出选中 (${pages.count { it.isSelected }} 页)")
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pages, key = { it.index }) { page ->
                    PageThumbnail(
                        page = page,
                        onClick = { viewModel.togglePage(page.index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageThumbnail(
    page: PageItem,
    onClick: () -> Unit
) {
    val borderColor = if (page.isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    val borderWidth = if (page.isSelected) 3.dp else 1.dp

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (page.bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = page.bitmap.asImageBitmap(),
                    contentDescription = page.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }
            if (page.isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }
        Text(
            text = page.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
```

- [ ] **Step 3: Wire PageGridScreen into PDFExtractorApp.kt**

```kotlin
// Replace PageGrid placeholder in PDFExtractorApp.kt:
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
```

- [ ] **Step 4: Add required imports to PDFExtractorApp.kt**

```kotlin
// Add at top of PDFExtractorApp.kt:
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.myutil.pdfextractor.ui.filelist.FileListScreen
import com.myutil.pdfextractor.ui.pagegrid.PageGridScreen
```

- [ ] **Step 5: Verify compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 6: Share Intent Handling

**Files:**
- Modify: `app/src/main/java/com/myutil/pdfextractor/MainActivity.kt` (handle ACTION_SEND)

**Interfaces:**
- Consumes: Share Intent from Android system
- Produces: Auto-navigates to FileList with shared PDF added (or directly to PageGrid)

- [ ] **Step 1: Update MainActivity.kt with share intent handling**

```kotlin
package com.myutil.pdfextractor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myutil.pdfextractor.ui.theme.PDFExtractorTheme

class MainActivity : ComponentActivity() {

    var sharedUri by mutableStateOf<Uri?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            PDFExtractorTheme {
                PDFExtractorApp(sharedUri = sharedUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "application/pdf") {
                    sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_VIEW -> {
                sharedUri = intent.data
            }
        }
    }
}
```

- [ ] **Step 2: Update PDFExtractorApp.kt to accept sharedUri**

```kotlin
@Composable
fun PDFExtractorApp(
    sharedUri: Uri? = null,
    startDestination: String = NavRoutes.FileList.route
) {
    val navController = rememberNavController()

    LaunchedEffect(sharedUri) {
        sharedUri?.let { uri ->
            navController.navigate(NavRoutes.PageGrid.buildRoute(uri.toString()))
        }
    }

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
```

- [ ] **Step 3: Verify compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 7: Final Polish & Edge Cases

**Files:**
- Modify: `app/src/main/java/com/myutil/pdfextractor/ui/pagegrid/PageGridViewModel.kt` (export result dialog)
- Create: `app/src/main/java/com/myutil/pdfextractor/ui/common/ExportResultDialog.kt`
- Minor fixes across files

**Interfaces:**
- Consumes: all existing components
- Produces: error toast/dialog, empty state refinements

- [ ] **Step 1: Create ExportResultDialog.kt**

```kotlin
package com.myutil.pdfextractor.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ExportResultDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (message.contains("成功")) "完成" else "提示")
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
```

- [ ] **Step 2: Add dialog to PageGridScreen**

In PageGridScreen.kt, add after Scaffold block or inside it:
```kotlin
exportResult?.let { message ->
    ExportResultDialog(
        message = message,
        onDismiss = { viewModel.clearExportResult() }
    )
}
```

Also add the import:
```kotlin
import com.myutil.pdfextractor.ui.common.ExportResultDialog
```

- [ ] **Step 3: Final compilation check**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 8: Generate Gradle Wrapper

**Files:**
- (auto-generated by gradle wrapper)

- [ ] **Step 1: Generate wrapper**

Run: `gradle wrapper --gradle-version 8.9` (or `./gradlew` if already exists)

If `gradle` is not installed locally, download the wrapper manually:
```bash
cd PDFPageExtractor
mkdir -p gradle/wrapper
curl -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar
```

- [ ] **Step 2: Verify full build**

Run: `cd PDFPageExtractor && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL
