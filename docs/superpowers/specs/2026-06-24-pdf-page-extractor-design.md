# PDF Page Extractor — Android App Design

## Overview

A lightweight Android app that reads PDFs, displays page thumbnails, lets users select pages via checkboxes or page-range input, and exports selected pages as a new PDF.

## Target Platform

- Android 10 (API 29)+
- Self-use (not published to app store)

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Single Activity
- **Navigation**: Compose Navigation
- **PDF Engine**: MuPDF (Artifex) — AGPL license, personal use
- **File Access**: Storage Access Framework (SAF) + Intent filter for `application/pdf` share

## Modules

### 1. File List Screen

- Scans app-internal directory and handles shared PDFs via Intent
- Lists PDFs with filename, size, and page count
- "Open file" button triggers SAF file picker
- Click a PDF → navigates to Page Grid Screen

### 2. Page Grid Screen

- Loaded when user opens a PDF (from list or share Intent)
- Displays page thumbnails in a scrollable grid (2-3 columns)
- Each thumbnail is tappable to toggle selection (visual checkmark overlay)
- Bottom bar: page-range input field (`1,3,5-8` syntax) + "Export Selected" button
- Page-range input and thumbnail selection are synchronized

### 3. Share Intent Integration

- AndroidManifest.xml registers `Intent.ACTION_SEND` with mime type `application/pdf`
- App appears in system share sheet for PDF files
- Shared PDF opens directly to Page Grid Screen

## Data Flow

1. File selected (from list / share / SAF picker) → `PdfRepository.open(uri)` → MuPDF document handle
2. `PdfRepository.renderPage(pageIndex, width, height)` → Bitmap thumbnail
3. User selects pages → `MainViewModel.selectedPages: Set<Int>`
4. "Export Selected" → `PdfRepository.extractPages(document, selectedPages, outputUri)` → new PDF saved via SAF

## Key Dependencies

- `com.artifex.mupdf:muPDF` — PDF rendering and manipulation
- `androidx.compose.*` — UI
- `androidx.activity.result:contract` — SAF file picker/save

## UI Wireframes

### Screen 1: PDF File List

```
┌─────────────────────────────┐
│  PDF Page Extractor         │
├─────────────────────────────┤
│ ┌──┬──────────────────┬──┐ │
│ │▐▐│ 报告-2026-Q2.pdf │ ›│ │
│ │  │ 3.2 MB · 24页    │  │ │
│ └──┴──────────────────┴──┘ │
│ ┌──┬──────────────────┬──┐ │
│ │▐▐│ 合同-供应商.pdf  │ ›│ │
│ │  │ 1.8 MB · 12页    │  │ │
│ └──┴──────────────────┴──┘ │
│ ┌──┬──────────────────┬──┐ │
│ │▐▐│ 产品手册.pdf     │ ›│ │
│ │  │ 5.6 MB · 48页    │  │ │
│ └──┴──────────────────┴──┘ │
│                            │
│  ┌──────────────────────┐  │
│  │ + 从文件管理器选择   │  │
│  └──────────────────────┘  │
└─────────────────────────────┘
```

### Screen 2: Page Preview & Export

```
┌─────────────────────────────┐
│ ← 报告-2026-Q2.pdf         │
├─────────────────────────────┤
│ ┌──────┐ ┌──────┐ ┌──────┐ │
│ │ ✓    │ │      │ │ ✓    │ │
│ │▐▐▐▐▐▐│ │▐▐▐▐▐▐│ │▐▐▐▐▐▐│ │
│ │第1页 │ │第2页 │ │第3页 │ │
│ └──────┘ └──────┘ └──────┘ │
│ ┌──────┐ ┌──────┐ ┌──────┐ │
│ │      │ │ ✓    │ │      │ │
│ │▐▐▐▐▐▐│ │▐▐▐▐▐▐│ │▐▐▐▐▐▐│ │
│ │第4页 │ │第5页 │ │第6页 │ │
│ └──────┘ └──────┘ └──────┘ │
├─────────────────────────────┤
│ 页码: [1,3,5-8      ]      │
│       [  导出选中    ]      │
└─────────────────────────────┘
```

### Share Integration

```
[在其他App中] → 分享 → PDF Page Extractor → 直接进入Screen 2
```

## Error Handling

- Invalid PDF file → display error toast, return to file list
- Export to non-writable location → SAF fallback with save dialog
- Large PDF (>100 pages) → lazy-load thumbnails, show loading indicator
- Memory: MuPDF document held as weak reference; thumbnails cached with LRU cache

## Future Considerations (out of scope for v1)

- PDF metadata editing
- Page reordering / drag-and-drop
- Merge multiple PDFs
- Annotations
