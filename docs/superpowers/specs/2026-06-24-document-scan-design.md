# Document Photo Scanner

## Overview

Add a document scanning feature to the PDF Page Extractor app. Users take or pick a photo of a document page, the app automatically corrects perspective distortion, removes color casts, binarizes to pure black-and-white, and exports the result as a PDF suitable for A4 printing.

## Entry Point

The main screen (`FileListScreen`) presents two entry cards:

- **PDF 提取** (existing): opens SAF picker → page grid → export selected pages
- **文档扫描** (new): opens camera/gallery picker → preview/edit → export as PDF

Cards are displayed in a vertical stack on the home screen, replacing the current single-button layout. Tapping a card navigates to the corresponding flow.

## Image Processing Pipeline

Uses OpenCV Android SDK for all image processing operations.

### Steps

1. **Load**: Decode the selected/captured image as a `Mat` (OpenCV matrix)
2. **Preprocess**: Convert to grayscale, apply Gaussian blur
3. **Edge detection**: Canny edge detection
4. **Contour finding**: `findContours` → find the largest 4-point contour (document boundary)
5. **Perspective correction**: `getPerspectiveTransform` + `warpPerspective` to map the detected quadrilateral to an A4-proportioned rectangle
6. **Color correction**: Convert to grayscale, apply histogram equalization to normalize lighting
7. **Binarization**: Otsu's thresholding (`THRESH_OTSU`) to produce pure black text on pure white background
8. **Output**: Convert the `Mat` back to a `Bitmap` for PDF generation

### Fallback

If auto-detection fails (no 4-point contour found), fall back to the full image with skew correction only. The user can still manually adjust corner points.

## Manual Corner Adjustment

The preview screen displays the corrected image with four draggable anchor points at the corners. When the user drags a point, the app re-runs the perspective transform with the new corner positions. This is a simple point-drag → re-warp loop, no OpenCV re-detection needed.

## PDF Export

After correction, the user taps "导出为 PDF". The app:

1. Saves the binarized Bitmap to a cache file (as PNG-encoded PDF page)
2. Uses the existing `PdfDocument` API (in `PdfRepository`) to create a single-page PDF
3. Presents the existing export dialog with "分享" and "保存到..." options
4. "保存到..." opens SAF `CreateDocument` to copy from cache to user-chosen location
5. "分享" uses `FileProvider` to share via `Intent.ACTION_SEND`

Reuses the exact same export components from the PDF page extractor feature.

## Navigation

Adds one new route:
- `scan` → `ScanPreviewScreen`

New route in `NavRoutes`:
```
sealed class NavRoutes(val route: String) {
    data object FileList : NavRoutes("fileList")
    data object PageGrid : NavRoutes("pageGrid/{uri}")
    data object Scan : NavRoutes("scan")  // new
}
```

No existing routes are modified.

## Screens

### ScanPreviewScreen (new)

Layout:
- TopAppBar with back button and title "文档扫描"
- Image preview area (center, fills most of the screen)
  - Shows corrected (binarized) image
  - Four draggable corner anchors overlaid on preview
  - Highlight/shade the area outside the document boundary
- Bottom action bar:
  - **自动矫正** button (re-runs auto-detection)
  - **原图对比** toggle (flip between original and corrected)
  - **旋转 90°** button
  - **导出为 PDF** primary button (enabled only when correction is done)

### ViewModel: ScanPreviewViewModel (new)

State:
- `originalBitmap: Bitmap?` — original photo
- `correctedBitmap: Bitmap?` — after OpenCV processing
- `cornerPoints: List<PointF>` — current corner positions
- `isProcessing: Boolean` — loading state

Actions:
- `loadImage(uri: Uri)` — decode and run auto-correction
- `autoCorrect()` — re-run OpenCV processing
- `updateCorner(index: Int, newPoint: PointF)` — update a corner and re-warp
- `rotateRight()` — rotate the original 90° and re-process
- `exportPdf(outputUri: Uri)` — write to PDF via PdfRepository
- `exportToCache()` — save corrected image to cache, return URI for share/save

## Data Layer Changes

### PdfRepository

Add a new method:
```kotlin
fun createPdfFromBitmap(bitmap: Bitmap, outputStream: OutputStream)
```
Creates a single-page PDF from the given bitmap using `PdfDocument`. The bitmap is scaled to A4 proportions (210:297) at reasonable DPI.

The existing `extractPages` methods are unchanged.

### ImageProcessor (new class)

Encapsulates all OpenCV operations:
```kotlin
class ImageProcessor {
    fun detectCorners(bitmap: Bitmap): List<Point>
    fun warpPerspective(bitmap: Bitmap, corners: List<Point>): Bitmap
    fun binarize(bitmap: Bitmap): Bitmap
    fun autoCorrect(bitmap: Bitmap): AutoCorrectResult?
}
```

This isolation keeps OpenCV dependency scoped to a single file. If needed later, it can be swapped for a different library without touching the rest of the app.

## Dependencies

Add OpenCV Android SDK:
```
implementation("com.quickbirdstudios:opencv:4.5.3.0")
```

This adds approximately 15MB to APK size (arm64-v8a only).

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Photo decoding fails | Show error message, back to entry |
| Edge detection fails | Use full image with skew correction only, show hint "建议手动调整" |
| Corner anchor dragged out of bounds | Clamp to image bounds |
| Export fails | Show error dialog, keep correction result |

## Future Considerations (Not Implemented)

- Multi-page scanning (scan multiple pages into one PDF)
- OCR text recognition
- Batch processing
- Filter presets (document, photo, business card)

## Open Questions

- Should the scan result automatically go into the PDF list? No — the generated PDF is saved to user-chosen location via SAF.
